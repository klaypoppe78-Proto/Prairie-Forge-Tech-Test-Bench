package com.example.agopengps.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.agopengps.navigation.GeoPoint
import com.example.agopengps.navigation.GeoUtils
import com.example.agopengps.navigation.Vec2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*

/**
 * Web Mercator Tile coordinate representation (EPSG:3857).
 */
data class TileCoord(
    val z: Int,
    val x: Int,
    val y: Int
)

/**
 * Map Tile Provider for topographical elevation basemaps and satellite imagery.
 */
enum class MapTileProvider(val label: String, val attribution: String, val isTopographic: Boolean = true) {
    ESRI_WORLD_TOPO("Esri World Topographic", "© Esri, USGS, Garmin", true),
    USGS_TOPOGRAPHIC("USGS The National Map (Topo)", "USGS National Map (Contours)", true),
    OPEN_TOPO_MAP("OpenTopo Elevation Contours", "© OpenTopoMap, SRTM", true),
    USGS_IMAGERY_TOPO("USGS Ortho + Topo Contours", "USGS / USDA NAIP", true),
    ESRI_WORLD_IMAGERY("Esri High-Res Imagery (NAIP)", "© Esri, Maxar, USDA", false),
    GOOGLE_SATELLITE("Google Maps Satellite", "© Google", false)
}

/**
 * Topographical and satellite basemap tile loader and LRU memory cache.
 * Provides high-resolution Esri World Topo (up to zoom 19), USGS Topo Quads, and OpenTopoMap.
 */
class SatelliteTileRepository {

    companion object {
        // Google Maps Satellite Tiles (mt0..mt3 subdomains)
        private const val GOOGLE_SAT_TILE_URL = "https://mt%d.google.com/vt/lyrs=s&x=%d&y=%d&z=%d"
        // Esri World Imagery & Topo
        private const val ESRI_IMAGERY_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/%d/%d/%d"
        private const val ESRI_TOPO_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/%d/%d/%d"
        // USGS The National Map (7.5-minute Quad Topographic Contours & Elevation)
        private const val USGS_TOPO_URL = "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/%d/%d/%d"
        private const val USGS_IMAGERY_TOPO_URL = "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryTopo/MapServer/tile/%d/%d/%d"
        // OpenTopoMap Elevation & Relief
        private const val OPEN_TOPO_URL = "https://tile.opentopomap.org/%d/%d/%d.png"

        private const val CACHE_SIZE = 384
        private const val EARTH_CIRCUMFERENCE_M = 40075016.68557849
    }

    private val lruCache = object : LruCache<String, ImageBitmap>(CACHE_SIZE) {}
    private val inFlight = mutableSetOf<String>()
    private val failedKeys = mutableMapOf<String, Long>() // key -> failTimestampMs
    var currentProvider: MapTileProvider = MapTileProvider.ESRI_WORLD_TOPO

    /**
     * Converts WGS84 GeoPoint (lat, lon) to Web Mercator tile index (x, y) at zoom level z.
     */
    fun geoToTile(lat: Double, lon: Double, z: Int): TileCoord {
        val n = 1 shl z
        val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
        val y = floor((1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
        return TileCoord(z, x, y)
    }

    /**
     * Returns the northwest WGS84 corner of a tile.
     */
    fun tileToGeoNW(tile: TileCoord): GeoPoint {
        val n = 1 shl tile.z
        val lon = tile.x.toDouble() / n * 360.0 - 180.0
        val latRad = atan(sinh(Math.PI * (1.0 - 2.0 * tile.y.toDouble() / n)))
        val lat = Math.toDegrees(latRad)
        return GeoPoint(lat, lon, 0.0)
    }

    /**
     * Returns the southeast WGS84 corner of a tile.
     */
    fun tileToGeoSE(tile: TileCoord): GeoPoint {
        val n = 1 shl tile.z
        val lon = (tile.x + 1.0) / n * 360.0 - 180.0
        val latRad = atan(sinh(Math.PI * (1.0 - 2.0 * (tile.y + 1.0) / n)))
        val lat = Math.toDegrees(latRad)
        return GeoPoint(lat, lon, 0.0)
    }

    /**
     * Calculates the best Web Mercator zoom level corresponding to current canvas pixelsPerMeter.
     * Uses crisp high-resolution zoom up to 19 for Esri World Topo, eliminating blurry upscaling.
     */
    fun getZoomForPixelsPerMeter(pixelsPerMeter: Float, lat: Double, provider: MapTileProvider = currentProvider): Int {
        val latRad = Math.toRadians(lat)
        val cosLat = max(0.1, cos(latRad))
        val targetResolution = 1.0 / max(0.1f, pixelsPerMeter) // meters per pixel
        val z = round(log2((EARTH_CIRCUMFERENCE_M * cosLat) / (256.0 * targetResolution))).toInt()

        return when (provider) {
            MapTileProvider.ESRI_WORLD_TOPO -> z.coerceIn(12, 19)
            MapTileProvider.USGS_TOPOGRAPHIC -> z.coerceIn(12, 16)
            MapTileProvider.OPEN_TOPO_MAP -> z.coerceIn(12, 17)
            MapTileProvider.USGS_IMAGERY_TOPO -> z.coerceIn(12, 16)
            MapTileProvider.ESRI_WORLD_IMAGERY -> z.coerceIn(14, 19)
            MapTileProvider.GOOGLE_SATELLITE -> z.coerceIn(14, 20)
        }
    }

    private fun cacheKey(coord: TileCoord, provider: MapTileProvider = currentProvider): String {
        return "${provider.name}_${coord.z}_${coord.x}_${coord.y}"
    }

    fun clearCache() {
        synchronized(lruCache) {
            lruCache.evictAll()
            inFlight.clear()
            failedKeys.clear()
        }
    }

    /**
     * Gets a tile from the cache synchronously if available.
     */
    fun getCachedTile(coord: TileCoord, provider: MapTileProvider = currentProvider): ImageBitmap? {
        val key = cacheKey(coord, provider)
        synchronized(lruCache) {
            return lruCache.get(key)
        }
    }

    /**
     * Asynchronously downloads a tile if not already present or in flight.
     * Robust HTTP timeouts and user-agent ensure fast, reliable downloads in the field.
     */
    suspend fun fetchTile(coord: TileCoord, provider: MapTileProvider = currentProvider): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = cacheKey(coord, provider)
        val now = System.currentTimeMillis()
        synchronized(lruCache) {
            lruCache.get(key)?.let { return@withContext it }
            if (inFlight.contains(key)) return@withContext null
            val failedAt = failedKeys[key]
            if (failedAt != null && (now - failedAt) < 10_000L) {
                // Skip retrying failed tile within 10s cooldown
                return@withContext null
            }
            inFlight.add(key)
        }

        val urlString = when (provider) {
            MapTileProvider.GOOGLE_SATELLITE -> {
                val serverNum = abs(coord.x + coord.y) % 4
                String.format(Locale.US, GOOGLE_SAT_TILE_URL, serverNum, coord.x, coord.y, coord.z)
            }
            MapTileProvider.ESRI_WORLD_IMAGERY -> {
                String.format(Locale.US, ESRI_IMAGERY_URL, coord.z, coord.y, coord.x)
            }
            MapTileProvider.ESRI_WORLD_TOPO -> {
                String.format(Locale.US, ESRI_TOPO_URL, coord.z, coord.y, coord.x)
            }
            MapTileProvider.USGS_TOPOGRAPHIC -> {
                String.format(Locale.US, USGS_TOPO_URL, coord.z, coord.y, coord.x)
            }
            MapTileProvider.USGS_IMAGERY_TOPO -> {
                String.format(Locale.US, USGS_IMAGERY_TOPO_URL, coord.z, coord.y, coord.x)
            }
            MapTileProvider.OPEN_TOPO_MAP -> {
                String.format(Locale.US, OPEN_TOPO_URL, coord.z, coord.x, coord.y)
            }
        }

        try {
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 5000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15; Mobile) AgSteer-PrecisionAg/1.0")
                setRequestProperty("Accept", "image/webp,image/apng,image/jpeg,image/png,*/*;q=0.8")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) {
                        val imageBitmap = bitmap.asImageBitmap()
                        synchronized(lruCache) {
                            lruCache.put(key, imageBitmap)
                            failedKeys.remove(key)
                            inFlight.remove(key)
                        }
                        return@withContext imageBitmap
                    }
                }
            } else {
                synchronized(lruCache) {
                    failedKeys[key] = now
                }
            }
        } catch (e: Exception) {
            synchronized(lruCache) {
                failedKeys[key] = now
            }
        } finally {
            synchronized(lruCache) {
                inFlight.remove(key)
            }
        }
        null
    }
}

package com.example.agopengps.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.agopengps.navigation.GeoPoint
import com.example.agopengps.navigation.Vec2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

data class TileCoord(val z: Int, val x: Int, val y: Int)

enum class MapTileProvider(
    val label: String,
    val attribution: String,
    val isTopographic: Boolean = true,
    val minZoom: Int = 9,
    val maxZoom: Int = 18
) {
    ESRI_WORLD_TOPO("Esri World Topographic", "© Esri, USGS, Garmin", true, 9, 18),
    USGS_TOPOGRAPHIC("USGS The National Map (Topo)", "USGS National Map (Contours)", true, 9, 16),
    OPEN_TOPO_MAP("OpenTopo Elevation Contours", "© OpenTopoMap, SRTM", true, 9, 17),
    USGS_IMAGERY_TOPO("USGS Ortho + Topo Contours", "USGS / USDA NAIP", true, 9, 16),
    ESRI_WORLD_IMAGERY("Esri High-Res Imagery (NAIP)", "© Esri, Maxar, USDA", false, 9, 18),
    GOOGLE_SATELLITE("Google Maps Satellite", "© Google", false, 9, 19)
}

class SatelliteTileRepository {

    companion object {
        private const val GOOGLE_SAT_TILE_URL = "https://mt%d.google.com/vt/lyrs=s&x=%d&y=%d&z=%d"
        private const val ESRI_IMAGERY_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/%d/%d/%d"
        private const val ESRI_TOPO_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/%d/%d/%d"
        private const val USGS_TOPO_URL = "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/%d/%d/%d"
        private const val USGS_IMAGERY_TOPO_URL = "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryTopo/MapServer/tile/%d/%d/%d"
        private const val OPEN_TOPO_URL = "https://tile.opentopomap.org/%d/%d/%d.png"

        private const val CACHE_SIZE = 512
        private const val EARTH_CIRCUMFERENCE_M = 40075016.68557849
    }

    private val memoryCache: MutableMap<String, ImageBitmap> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
                return size > CACHE_SIZE
            }
        }
    )
    private val inFlightRequests = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val downloadScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    fun cancelPendingDownloads() {
        downloadScope.coroutineContext.cancelChildren()
        inFlightRequests.clear()
    }

    fun shutdown() {
        downloadScope.cancel()
        inFlightRequests.clear()
    }

    fun getCachedTile(coord: TileCoord, provider: MapTileProvider): ImageBitmap? {
        val key = "${provider.name}_${coord.z}_${coord.x}_${coord.y}"
        return memoryCache[key]
    }

    /**
     * Looks up cached tile, with fallback to parent/ancestor tile at lower zoom levels if the exact tile
     * is still loading or if user zoomed past provider's max zoom (preventing cutouts or disappearing tiles).
     */
    fun getCachedTileWithFallback(coord: TileCoord, provider: MapTileProvider): Pair<ImageBitmap, TileCoord>? {
        if (coord.z <= provider.maxZoom) {
            val direct = getCachedTile(coord, provider)
            if (direct != null) return Pair(direct, coord)
        }

        // Walk up ancestor zoom levels (overzoom display)
        var curZ = coord.z
        var curX = coord.x
        var curY = coord.y
        while (curZ > provider.minZoom) {
            curZ--
            curX = curX shr 1
            curY = curY shr 1
            if (curZ <= provider.maxZoom) {
                val parentCoord = TileCoord(curZ, curX, curY)
                val parent = getCachedTile(parentCoord, provider)
                if (parent != null) {
                    return Pair(parent, parentCoord)
                }
            }
        }
        return null
    }

    /**
     * Non-cancelling background tile fetcher that survives 20Hz vehicle movement and pinch-zoom gestures.
     * Pre-fetches base zoom (14), mid zoom (16), and target safe zoom so overzoom fallback is always ready.
     */
    fun ensureTilesLoaded(
        centerGeo: GeoPoint,
        pixelsPerMeter: Float,
        provider: MapTileProvider,
        onTileLoaded: () -> Unit
    ) {
        val detailZoom = getSafeZoomLevel(pixelsPerMeter, provider, centerGeo.latitude)
        val midZoom = 16.coerceIn(provider.minZoom, detailZoom)
        val baseZoom = 14.coerceIn(provider.minZoom, midZoom)

        val zoomLevels = listOf(baseZoom, midZoom, detailZoom).distinct()
        for (z in zoomLevels) {
            val radius = if (z == detailZoom) 2 else 1
            val coords = getVisibleTileCoords(centerGeo, z, radiusTiles = radius)
            for (tile in coords) {
                if (getCachedTile(tile, provider) == null) {
                    val key = "${provider.name}_${tile.z}_${tile.x}_${tile.y}"
                    if (!inFlightRequests.contains(key)) {
                        downloadScope.launch {
                            val bmp = fetchTile(tile, provider)
                            if (bmp != null) {
                                withContext(Dispatchers.Main) {
                                    onTileLoaded()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun fetchTile(coord: TileCoord, provider: MapTileProvider): ImageBitmap? = withContext(Dispatchers.IO) {
        val safeZoom = coord.z.coerceIn(provider.minZoom, provider.maxZoom)
        val safeCoord = if (safeZoom != coord.z) {
            val shift = coord.z - safeZoom
            if (shift > 0) {
                TileCoord(safeZoom, coord.x shr shift, coord.y shr shift)
            } else {
                TileCoord(safeZoom, coord.x shl (-shift), coord.y shl (-shift))
            }
        } else {
            coord
        }

        val key = "${provider.name}_${safeCoord.z}_${safeCoord.x}_${safeCoord.y}"
        val existing = memoryCache.get(key)
        if (existing != null) return@withContext existing

        if (!inFlightRequests.add(key)) {
            // Already being fetched in parallel
            return@withContext null
        }

        val urlString = when (provider) {
            MapTileProvider.GOOGLE_SATELLITE -> {
                val serverIdx = abs(safeCoord.x + safeCoord.y) % 4
                String.format(GOOGLE_SAT_TILE_URL, serverIdx, safeCoord.x, safeCoord.y, safeCoord.z)
            }
            MapTileProvider.ESRI_WORLD_IMAGERY -> String.format(ESRI_IMAGERY_URL, safeCoord.z, safeCoord.y, safeCoord.x)
            MapTileProvider.ESRI_WORLD_TOPO -> String.format(ESRI_TOPO_URL, safeCoord.z, safeCoord.y, safeCoord.x)
            MapTileProvider.USGS_TOPOGRAPHIC -> String.format(USGS_TOPO_URL, safeCoord.z, safeCoord.y, safeCoord.x)
            MapTileProvider.USGS_IMAGERY_TOPO -> String.format(USGS_IMAGERY_TOPO_URL, safeCoord.z, safeCoord.y, safeCoord.x)
            MapTileProvider.OPEN_TOPO_MAP -> String.format(OPEN_TOPO_URL, safeCoord.z, safeCoord.x, safeCoord.y)
        }

        try {
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 6000
                setRequestProperty("User-Agent", "SentinelGuidance-PrecisionAg/1.0 (Android; Precision Guidance System)")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val imageBitmap = bitmap.asImageBitmap()
                        memoryCache.put(key, imageBitmap)
                        return@withContext imageBitmap
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            inFlightRequests.remove(key)
        }
        return@withContext null
    }

    fun geoToTileCoord(geo: GeoPoint, zoom: Int): TileCoord {
        val n = 1 shl zoom
        val x = floor((geo.longitude + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(geo.latitude.coerceIn(-85.05112878, 85.05112878))
        val y = floor((1.0 - asinh(tan(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
        return TileCoord(zoom, x, y)
    }

    /**
     * Calculates the safe network tile zoom level for a given pixels-per-meter scale,
     * ensuring it never exceeds a reliable zoom level so rural tiles never 404.
     */
    fun getSafeZoomLevel(pixelsPerMeter: Float, provider: MapTileProvider, latitude: Double = 41.5): Int {
        val latRad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        val cosLat = cos(latRad).coerceAtLeast(0.1)
        val desiredZoom = (log2(pixelsPerMeter.toDouble() * 156543.03 * cosLat)).roundToInt()
        val maxSafeZoom = min(provider.maxZoom, 17)
        return desiredZoom.coerceIn(provider.minZoom, maxSafeZoom)
    }

    /**
     * Calculates the screen sub-tile grid zoom level (up to z=21) so that each rendered sub-tile
     * is ~200-500 pixels on screen even at maximum zoom, preventing giant offscreen textures or clipping.
     */
    fun getRenderZoomLevel(pixelsPerMeter: Float, provider: MapTileProvider, latitude: Double = 41.5): Int {
        val latRad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        val cosLat = cos(latRad).coerceAtLeast(0.1)
        val desiredZoom = (log2(pixelsPerMeter.toDouble() * 156543.03 * cosLat)).roundToInt()
        val maxRenderZoom = ( min(provider.maxZoom, 17) + 4 ).coerceAtMost(21)
        return desiredZoom.coerceIn(provider.minZoom, maxRenderZoom)
    }

    fun getVisibleTileCoords(centerGeo: GeoPoint, zoom: Int, radiusTiles: Int = 2): List<TileCoord> {
        val centerTile = geoToTileCoord(centerGeo, zoom)
        val maxIndex = (1 shl zoom) - 1
        val coords = mutableListOf<TileCoord>()

        // Order from center outward so center tile beneath the tractor loads first
        for (dx in -radiusTiles..radiusTiles) {
            for (dy in -radiusTiles..radiusTiles) {
                val tx = (centerTile.x + dx).coerceIn(0, maxIndex)
                val ty = (centerTile.y + dy).coerceIn(0, maxIndex)
                coords.add(TileCoord(zoom, tx, ty))
            }
        }
        return coords.distinct().sortedBy {
            val ddx = it.x - centerTile.x
            val ddy = it.y - centerTile.y
            ddx * ddx + ddy * ddy
        }
    }

    fun tileToLocalBoundsMeters(tile: TileCoord, originGeo: GeoPoint): Pair<Vec2, Vec2> {
        val n = 1 shl tile.z
        val lonWest = tile.x.toDouble() / n * 360.0 - 180.0
        val lonEast = (tile.x + 1.0) / n * 360.0 - 180.0
        val latNorth = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * tile.y.toDouble() / n))))
        val latSouth = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * (tile.y + 1.0) / n))))

        val nwGeo = GeoPoint(latNorth, lonWest)
        val seGeo = GeoPoint(latSouth, lonEast)

        val nwLocal = com.example.agopengps.navigation.GeoUtils.geoToLocalMeters(nwGeo, originGeo)
        val seLocal = com.example.agopengps.navigation.GeoUtils.geoToLocalMeters(seGeo, originGeo)
        return Pair(nwLocal, seLocal)
    }
}

package com.example.agopengps.navigation

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Spatial chunk bucket for painted field coverage geometry.
 * Groups swath segments into fixed-size world cells (default 60m x 60m) with a tight AABB
 * so 100+ acre fields (10,000+ segments) can be viewport/frustum-culled in O(visible chunks).
 */
class CoverageChunk(
    val chunkX: Int,
    val chunkY: Int
) {
    var minX: Double = Double.POSITIVE_INFINITY
        private set
    var maxX: Double = Double.NEGATIVE_INFINITY
        private set
    var minY: Double = Double.POSITIVE_INFINITY
        private set
    var maxY: Double = Double.NEGATIVE_INFINITY
        private set

    val segments: ArrayList<AppliedSwathSegment> = ArrayList(64)

    fun add(seg: AppliedSwathSegment) {
        segments.add(seg)
        val sMinX = min(min(seg.leftStart.x, seg.rightStart.x), min(seg.leftEnd.x, seg.rightEnd.x))
        val sMaxX = max(max(seg.leftStart.x, seg.rightStart.x), max(seg.leftEnd.x, seg.rightEnd.x))
        val sMinY = min(min(seg.leftStart.y, seg.rightStart.y), min(seg.leftEnd.y, seg.rightEnd.y))
        val sMaxY = max(max(seg.leftStart.y, seg.rightStart.y), max(seg.leftEnd.y, seg.rightEnd.y))
        if (sMinX < minX) minX = sMinX
        if (sMaxX > maxX) maxX = sMaxX
        if (sMinY < minY) minY = sMinY
        if (sMaxY > maxY) maxY = sMaxY
    }
}

/**
 * Thread-safe, O(1)-append chunked spatial store for applied coverage segments.
 * Eliminates O(N^2) List copy churn during 20 Hz painting and supports zero-allocation
 * viewport culling (2D) and frustum culling (3D) for 100+ acre fields.
 */
class CoverageChunkStore(val chunkSizeMeters: Double = 60.0) {
    @PublishedApi
    internal val lock = Any()
    @PublishedApi
    internal val chunks = HashMap<Long, CoverageChunk>(128)
    private val allSegments = ArrayList<AppliedSwathSegment>(1024)

    @Volatile
    var version: Long = 0L
        private set

    @Volatile
    var lastSegment: AppliedSwathSegment? = null
        private set

    val segmentCount: Int
        get() = synchronized(lock) { allSegments.size }

    val totalSegmentCount: Int
        get() = synchronized(lock) { allSegments.size }

    val chunkCount: Int
        get() = synchronized(lock) { chunks.size }

    private fun packKey(cx: Int, cy: Int): Long {
        return (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
    }

    fun addSegment(seg: AppliedSwathSegment): Long {
        val cx = floor(seg.center.x / chunkSizeMeters).toInt()
        val cy = floor(seg.center.y / chunkSizeMeters).toInt()
        val key = packKey(cx, cy)
        synchronized(lock) {
            var chunk = chunks[key]
            if (chunk == null) {
                chunk = CoverageChunk(cx, cy)
                chunks[key] = chunk
            }
            chunk.add(seg)
            allSegments.add(seg)
            lastSegment = seg
            version++
            return version
        }
    }

    fun addAll(segments: List<AppliedSwathSegment>): Long {
        if (segments.isEmpty()) return version
        synchronized(lock) {
            for (i in segments.indices) {
                val seg = segments[i]
                val cx = floor(seg.center.x / chunkSizeMeters).toInt()
                val cy = floor(seg.center.y / chunkSizeMeters).toInt()
                val key = packKey(cx, cy)
                var chunk = chunks[key]
                if (chunk == null) {
                    chunk = CoverageChunk(cx, cy)
                    chunks[key] = chunk
                }
                chunk.add(seg)
                allSegments.add(seg)
                lastSegment = seg
            }
            version++
            return version
        }
    }

    fun clear(): Long {
        synchronized(lock) {
            chunks.clear()
            allSegments.clear()
            lastSegment = null
            version++
            return version
        }
    }

    fun snapshotList(): List<AppliedSwathSegment> {
        synchronized(lock) {
            return ArrayList(allSegments)
        }
    }

    fun snapshotAllSegments(): List<AppliedSwathSegment> = snapshotList()

    /**
     * Zero-allocation iteration over segments whose spatial chunk intersects the 2D world AABB.
     */
    inline fun forEachVisibleIn2D(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        action: (AppliedSwathSegment) -> Unit
    ) {
        val minCx = floor(minX / chunkSizeMeters).toInt() - 1
        val maxCx = floor(maxX / chunkSizeMeters).toInt() + 1
        val minCy = floor(minY / chunkSizeMeters).toInt() - 1
        val maxCy = floor(maxY / chunkSizeMeters).toInt() + 1
        visitChunksInRange(minCx, maxCx, minCy, maxCy, minX, maxX, minY, maxY, action)
    }

    @PublishedApi
    internal inline fun visitChunksInRange(
        minCx: Int,
        maxCx: Int,
        minCy: Int,
        maxCy: Int,
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        action: (AppliedSwathSegment) -> Unit
    ) {
        synchronized(lock) {
            if (chunks.isEmpty()) return
            val cellRangeCount = (maxCx - minCx + 1L) * (maxCy - minCy + 1L)
            if (cellRangeCount <= chunks.size * 2L) {
                for (cx in minCx..maxCx) {
                    for (cy in minCy..maxCy) {
                        val key = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
                        val chunk = chunks[key] ?: continue
                        if (chunk.maxX < minX || chunk.minX > maxX || chunk.maxY < minY || chunk.minY > maxY) {
                            continue
                        }
                        val list = chunk.segments
                        for (i in 0 until list.size) {
                            action(list[i])
                        }
                    }
                }
            } else {
                for (chunk in chunks.values) {
                    if (chunk.maxX < minX || chunk.minX > maxX || chunk.maxY < minY || chunk.minY > maxY) {
                        continue
                    }
                    val list = chunk.segments
                    for (i in 0 until list.size) {
                        action(list[i])
                    }
                }
            }
        }
    }

    /**
     * Zero-allocation iteration over segments whose spatial chunk intersects the 3D camera frustum.
     */
    inline fun forEachVisibleIn3DFrustum(
        pivotX: Double,
        pivotY: Double,
        sinH: Double,
        cosH: Double,
        nearLimit: Double,
        farLimit: Double,
        maxLateral: Double,
        lateralSlopePerMeter: Double = 0.0,
        action: (AppliedSwathSegment) -> Unit
    ) {
        synchronized(lock) {
            if (chunks.isEmpty()) return
            for (chunk in chunks.values) {
                // Transform chunk center + bounding radius into tractor forward/lateral space
                val midX = (chunk.minX + chunk.maxX) * 0.5
                val midY = (chunk.minY + chunk.maxY) * 0.5
                val halfDiagX = (chunk.maxX - chunk.minX) * 0.5
                val halfDiagY = (chunk.maxY - chunk.minY) * 0.5
                val radius = kotlin.math.sqrt(halfDiagX * halfDiagX + halfDiagY * halfDiagY)

                val dx = midX - pivotX
                val dy = midY - pivotY
                val fwd = dx * sinH + dy * cosH
                if (fwd + radius < nearLimit || fwd - radius > farLimit) continue

                val lat = kotlin.math.abs(dx * cosH - dy * sinH)
                val maxAllowedLat = maxLateral + max(0.0, fwd) * lateralSlopePerMeter + radius
                if (lat > maxAllowedLat) continue

                val list = chunk.segments
                for (i in 0 until list.size) {
                    action(list[i])
                }
            }
        }
    }
}

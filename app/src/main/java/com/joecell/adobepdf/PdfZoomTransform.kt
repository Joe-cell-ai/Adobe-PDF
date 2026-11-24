package com.joecell.adobepdf

import kotlin.math.max
import kotlin.math.min

/** Pure focal-point zoom math shared by the viewer and unit tests. */
internal object PdfZoomTransform {
    data class Point(val x: Float, val y: Float)

    fun nextScale(current: Float, factor: Float, minScale: Float = 0.5f, maxScale: Float = 5f): Float {
        require(current.isFinite() && factor.isFinite() && minScale > 0f && maxScale >= minScale)
        return min(max(current * factor, minScale), maxScale)
    }

    /**
     * Updates translation so the document point beneath the gesture centroid remains
     * beneath that centroid after scaling, while also applying the user's pan delta.
     */
    fun focalPan(
        currentPan: Point,
        centroidFromViewportCenter: Point,
        panDelta: Point,
        oldScale: Float,
        newScale: Float
    ): Point {
        require(oldScale.isFinite() && newScale.isFinite() && oldScale > 0f && newScale > 0f)
        if (newScale <= 1.001f) return Point(0f, 0f)
        val ratio = newScale / oldScale
        return Point(
            (currentPan.x - centroidFromViewportCenter.x) * ratio + centroidFromViewportCenter.x + panDelta.x,
            (currentPan.y - centroidFromViewportCenter.y) * ratio + centroidFromViewportCenter.y + panDelta.y
        )
    }
}

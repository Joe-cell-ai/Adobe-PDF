package com.joecell.adobepdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfZoomTransformTest {
    @Test fun pinchZoomKeepsPointUnderCentroidAnchored() {
        val result = PdfZoomTransform.focalPan(
            currentPan = PdfZoomTransform.Point(0f, 0f),
            centroidFromViewportCenter = PdfZoomTransform.Point(120f, -40f),
            panDelta = PdfZoomTransform.Point(0f, 0f),
            oldScale = 1f,
            newScale = 2f
        )
        assertEquals(-120f, result.x, 0.001f)
        assertEquals(40f, result.y, 0.001f)
    }

    @Test fun pinchCombinesFocalZoomAndFingerTranslation() {
        val result = PdfZoomTransform.focalPan(
            currentPan = PdfZoomTransform.Point(10f, 20f),
            centroidFromViewportCenter = PdfZoomTransform.Point(100f, 50f),
            panDelta = PdfZoomTransform.Point(7f, -3f),
            oldScale = 2f,
            newScale = 3f
        )
        assertEquals(-28f, result.x, 0.001f)
        assertEquals(2f, result.y, 0.001f)
    }

    @Test fun returningToFitResetsPan() {
        val result = PdfZoomTransform.focalPan(
            PdfZoomTransform.Point(300f, -100f),
            PdfZoomTransform.Point(40f, 80f),
            PdfZoomTransform.Point(5f, 5f),
            oldScale = 2f,
            newScale = 1f
        )
        assertEquals(0f, result.x, 0f)
        assertEquals(0f, result.y, 0f)
    }

    @Test fun zoomScaleIsClamped() {
        assertEquals(5f, PdfZoomTransform.nextScale(4f, 2f), 0f)
        assertEquals(0.5f, PdfZoomTransform.nextScale(1f, 0.1f), 0f)
    }
}

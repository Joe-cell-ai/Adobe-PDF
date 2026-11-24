package com.joecell.adobepdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfEditorGeometryTest {
    @Test fun hitTestSelectsOnlyWithinObjectBounds() {
        val box = PdfEditorGeometry.Rect(0.2f, 0.3f, 0.4f, 0.2f)
        assertTrue(PdfEditorGeometry.contains(box, 0.4f, 0.4f))
        assertFalse(PdfEditorGeometry.contains(box, 0.7f, 0.4f))
    }

    @Test fun movePreservesSizeAndClampsToPage() {
        val moved = PdfEditorGeometry.move(PdfEditorGeometry.Rect(0.2f, 0.3f, 0.4f, 0.2f), 0.1f, -0.1f)
        assertEquals(0.3f, moved.left, 0.0001f)
        assertEquals(0.2f, moved.top, 0.0001f)
        assertEquals(0.4f, moved.width, 0.0001f)
        val edge = PdfEditorGeometry.move(moved, 1f, 1f)
        assertEquals(0.6f, edge.left, 0.0001f)
        assertEquals(0.8f, edge.top, 0.0001f)
    }

    @Test fun resizePreservesOriginAndClampsToPage() {
        val resized = PdfEditorGeometry.resize(PdfEditorGeometry.Rect(0.2f, 0.3f, 0.4f, 0.2f), 0.2f, 0.1f)
        assertEquals(0.2f, resized.left, 0f)
        assertEquals(0.6f, resized.width, 0.0001f)
        assertEquals(0.3f, resized.height, 0.0001f)
        val edge = PdfEditorGeometry.resize(PdfEditorGeometry.Rect(0.8f, 0.8f, 0.1f, 0.1f), 1f, 1f)
        assertEquals(0.2f, edge.width, 0.0001f)
        assertEquals(0.2f, edge.height, 0.0001f)
    }
}

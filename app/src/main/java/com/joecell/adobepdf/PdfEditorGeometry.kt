package com.joecell.adobepdf

/** Normalized page-space geometry for selectable PDF overlay objects. */
internal object PdfEditorGeometry {
    data class Rect(val left: Float, val top: Float, val width: Float, val height: Float) {
        val right: Float get() = left + width
        val bottom: Float get() = top + height
    }

    fun move(rect: Rect, dx: Float, dy: Float): Rect {
        requireValid(rect)
        require(dx.isFinite() && dy.isFinite())
        val left = (rect.left + dx).coerceIn(0f, 1f - rect.width)
        val top = (rect.top + dy).coerceIn(0f, 1f - rect.height)
        return rect.copy(left = left, top = top)
    }

    fun resize(rect: Rect, dw: Float, dh: Float, minSize: Float = 0.01f): Rect {
        requireValid(rect)
        require(dw.isFinite() && dh.isFinite() && minSize.isFinite() && minSize > 0f)
        val width = (rect.width + dw).coerceIn(minSize, 1f - rect.left)
        val height = (rect.height + dh).coerceIn(minSize, 1f - rect.top)
        return rect.copy(width = width, height = height)
    }

    fun contains(rect: Rect, x: Float, y: Float): Boolean {
        requireValid(rect)
        return x.isFinite() && y.isFinite() && x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
    }

    private fun requireValid(rect: Rect) {
        require(rect.left.isFinite() && rect.top.isFinite() && rect.width.isFinite() && rect.height.isFinite() &&
            rect.left in 0f..1f && rect.top in 0f..1f && rect.width > 0f && rect.height > 0f &&
            rect.right <= 1.0001f && rect.bottom <= 1.0001f) { "Object bounds must fit inside the PDF page." }
    }
}

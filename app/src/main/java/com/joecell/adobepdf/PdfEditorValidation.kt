package com.joecell.adobepdf

import java.net.URI

/** Small pure validation helpers shared by PDF editor operations and unit tests. */
internal object PdfEditorValidation {
    fun requirePageIndex(pageIndex: Int, pageCount: Int) {
        require(pageCount > 0 && pageIndex in 0 until pageCount) { "Page not found." }
    }

    fun requireNormalizedPoint(x: Float, y: Float) {
        require(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) {
            "Position must be between 0 and 100 percent."
        }
    }

    fun requireNormalizedRectangle(left: Float, top: Float, width: Float, height: Float) {
        require(left.isFinite() && top.isFinite() && width.isFinite() && height.isFinite() &&
            left in 0f..1f && top in 0f..1f && width > 0f && height > 0f &&
            left + width <= 1.001f && top + height <= 1.001f) {
            "Redaction area must fit inside the page."
        }
    }

    fun requireHttpUrl(value: String) {
        val parsed = runCatching { URI(value.trim()) }.getOrNull()
        require(parsed != null &&
            (parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) &&
            !parsed.host.isNullOrBlank()) { "Enter a valid http or https URL." }
    }
}


    fun requireSignatureStrokes(strokes: List<List<Pair<Float, Float>>>) {
        require(strokes.sumOf { it.size } >= 2) { "Draw a signature first." }
        require(strokes.all { stroke -> stroke.all { point ->
            point.first.isFinite() && point.second.isFinite() &&
                point.first in 0f..1f && point.second in 0f..1f
        } }) { "Signature coordinates are invalid." }
    }

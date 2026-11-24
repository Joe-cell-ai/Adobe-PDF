package com.joecell.adobepdf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.io.FileOutputStream

/** Local-only PDF operations using Android's Storage Access Framework. */
class PdfEngine(private val context: Context) {
    init { PDFBoxResourceLoader.init(context) }

    fun merge(uris: List<Uri>): File {
        require(uris.size >= 2) { "Choose at least two PDF files to merge." }
        val merger = PDFMergerUtility()
        val streams = uris.mapIndexed { index, uri ->
            context.contentResolver.openInputStream(uri) ?: error("Cannot open selected PDF number ${index + 1}.")
        }
        try {
            streams.forEach { merger.addSource(it) }
            val output = File(context.cacheDir, "merged_${System.currentTimeMillis()}.pdf")
            FileOutputStream(output).use { stream ->
                merger.destinationStream = stream
                merger.mergeDocuments(null)
            }
            return output
        } finally { streams.forEach { runCatching { it.close() } } }
    }

    fun extractPages(uri: Uri, pageIndices: List<Int>): File {
        require(pageIndices.isNotEmpty()) { "Select at least one page." }
        val document = openDocument(uri)
        val result = PDDocument()
        try {
            pageIndices.forEach { index ->
                require(index in 0 until document.numberOfPages) { "Page selection is out of range." }
                result.importPage(document.getPage(index))
            }
            val output = File(context.cacheDir, "extracted_${System.currentTimeMillis()}.pdf")
            result.save(output)
            return output
        } finally { result.close(); document.close() }
    }

    fun rebuild(uri: Uri, pageOrder: List<Int>, rotations: Map<Int, Int> = emptyMap()): File {
        require(pageOrder.isNotEmpty()) { "A PDF must contain at least one page." }
        val document = openDocument(uri)
        val result = PDDocument()
        try {
            pageOrder.forEach { sourceIndex ->
                require(sourceIndex in 0 until document.numberOfPages) { "Page order contains an invalid page." }
                val imported = result.importPage(document.getPage(sourceIndex))
                imported.rotation = (document.getPage(sourceIndex).rotation + (rotations[sourceIndex] ?: 0)) % 360
            }
            val output = File(context.cacheDir, "organized_${System.currentTimeMillis()}.pdf")
            result.save(output)
            return output
        } finally { result.close(); document.close() }
    }

    fun rotateAllPages(uri: Uri): File {
        val document = openDocument(uri)
        try {
            for (page in document.pages) page.rotation = (page.rotation + 90) % 360
            val output = File(context.cacheDir, "rotated_${System.currentTimeMillis()}.pdf")
            document.save(output)
            return output
        } finally { document.close() }
    }

    fun addText(uri: Uri, text: String): File {
        require(text.isNotBlank()) { "Enter text to add." }
        val document = openDocument(uri)
        try {
            val page = document.getPage(0)
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(48f, page.mediaBox.height - 64f)
                stream.showText(text.take(500))
                stream.endText()
            }
            val output = File(context.cacheDir, "edited_${System.currentTimeMillis()}.pdf")
            document.save(output)
            return output
        } finally { document.close() }
    }

    fun pageCount(uri: Uri): Int {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open PDF.")
        return input.use { PDDocument.load(it).use { pdf -> pdf.numberOfPages } }
    }

    private fun openDocument(uri: Uri): PDDocument {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open PDF.")
        return input.use { PDDocument.load(it) }
    }

    fun saveToUri(source: File, resolver: ContentResolver, target: Uri) {
        source.inputStream().use { input ->
            resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
                ?: error("Cannot write to the selected destination.")
        }
    }
}

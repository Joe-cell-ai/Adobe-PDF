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
        uris.forEachIndexed { index, uri ->
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Cannot open selected PDF number ${index + 1}.")
            merger.addSource(input)
        }
        val output = File(context.cacheDir, "merged_${System.currentTimeMillis()}.pdf")
        FileOutputStream(output).use { stream ->
            merger.destinationStream = stream
            merger.mergeDocuments(null)
        }
        return output
    }

    fun splitFirstPage(uri: Uri): File {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open PDF.")
        val document = input.use { PDDocument.load(it) }
        require(document.numberOfPages > 0) { "The PDF has no pages." }
        val result = PDDocument()
        result.addPage(document.getPage(0))
        val output = File(context.cacheDir, "page_1_${System.currentTimeMillis()}.pdf")
        result.save(output)
        result.close()
        document.close()
        return output
    }

    fun rotateAllPages(uri: Uri): File {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open PDF.")
        val document = input.use { PDDocument.load(it) }
        for (page in document.pages) page.rotation = (page.rotation + 90) % 360
        val output = File(context.cacheDir, "rotated_${System.currentTimeMillis()}.pdf")
        document.save(output)
        document.close()
        return output
    }

    fun addText(uri: Uri, text: String): File {
        require(text.isNotBlank()) { "Enter text to add." }
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open PDF.")
        val document = input.use { PDDocument.load(it) }
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
        document.close()
        return output
    }

    fun pageCount(uri: Uri): Int {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open PDF.")
        return input.use { PDDocument.load(it).use { pdf -> pdf.numberOfPages } }
    }

    fun saveToUri(source: File, resolver: ContentResolver, target: Uri) {
        source.inputStream().use { input ->
            resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
                ?: error("Cannot write to the selected destination.")
        }
    }
}

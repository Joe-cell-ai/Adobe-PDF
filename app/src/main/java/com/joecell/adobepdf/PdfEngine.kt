package com.joecell.adobepdf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.util.Matrix
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


    fun addTextOverlay(uri: Uri, text: String, pageIndex: Int = 0): File {
        require(text.isNotBlank()) { "Enter text to add." }
        val document = openDocument(uri)
        try {
            require(pageIndex in 0 until document.numberOfPages) { "Page not found." }
            val page = document.getPage(pageIndex)
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 14f)
                stream.newLineAtOffset(48f, page.mediaBox.height - 64f)
                stream.showText(text.take(500))
                stream.endText()
            }
            return saveEdited(document, "text")
        } finally { document.close() }
    }

    fun insertImage(uri: Uri, imageUri: Uri, pageIndex: Int = 0): File {
        val document = openDocument(uri)
        try {
            require(pageIndex in 0 until document.numberOfPages) { "Page not found." }
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
                ?: error("Could not read the selected image.")
            try {
                val image = LosslessFactory.createFromImage(document, bitmap)
                val page = document.getPage(pageIndex)
                val scale = minOf(page.mediaBox.width * 0.42f / image.width, page.mediaBox.height * 0.32f / image.height, 1f)
                val width = image.width * scale
                val height = image.height * scale
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use {
                    it.drawImage(image, (page.mediaBox.width - width) / 2f, (page.mediaBox.height - height) / 2f, width, height)
                }
            } finally { bitmap.recycle() }
            return saveEdited(document, "image")
        } finally { document.close() }
    }

    fun addHighlight(uri: Uri, pageIndex: Int = 0): File {
        val document = openDocument(uri)
        try {
            require(pageIndex in 0 until document.numberOfPages) { "Page not found." }
            val box = document.getPage(pageIndex).mediaBox
            PDPageContentStream(document, document.getPage(pageIndex), PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.setNonStrokingColor(255, 238, 120)
                stream.addRect(box.width * 0.22f, box.height * 0.52f, box.width * 0.56f, 24f)
                stream.fill()
            }
            return saveEdited(document, "highlight")
        } finally { document.close() }
    }

    fun addTypedSignature(uri: Uri, signature: String, pageIndex: Int = 0): File {
        require(signature.isNotBlank()) { "Enter a signature." }
        val document = openDocument(uri)
        try {
            require(pageIndex in 0 until document.numberOfPages) { "Page not found." }
            val page = document.getPage(pageIndex)
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.TIMES_ITALIC, 24f)
                stream.newLineAtOffset(48f, 54f)
                stream.showText(signature.take(100))
                stream.endText()
            }
            return saveEdited(document, "signature")
        } finally { document.close() }
    }

    private fun saveEdited(document: PDDocument, prefix: String): File {
        val output = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.pdf")
        document.save(output)
        return output
    }


    /**
     * Renders each page locally, recognises Latin text with the bundled ML Kit model,
     * and adds invisible PDF text positioned over the recognised words.
     */
    fun createSearchablePdf(uri: Uri): File {
        val document = openDocument(uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            require(document.numberOfPages > 0) { "This PDF has no pages." }
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open PDF for OCR.")
            try {
                PdfRenderer(descriptor).use { renderer ->
                    require(renderer.pageCount == document.numberOfPages) { "PDF page count changed while opening." }
                    for (pageIndex in 0 until renderer.pageCount) {
                        renderer.openPage(pageIndex).use { renderedPage ->
                            val scale = minOf(2.0f, 2400f / maxOf(renderedPage.width, renderedPage.height))
                            val bitmapWidth = (renderedPage.width * scale).toInt().coerceAtLeast(1)
                            val bitmapHeight = (renderedPage.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                renderedPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                val recognized = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                                val pdfPage = document.getPage(pageIndex)
                                val scaleX = pdfPage.mediaBox.width / bitmap.width.toFloat()
                                val scaleY = pdfPage.mediaBox.height / bitmap.height.toFloat()
                                PDPageContentStream(document, pdfPage, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                                    recognized.textBlocks.flatMap { it.lines }.forEach { line ->
                                        val bounds = line.boundingBox ?: return@forEach
                                        val cleanText = line.text
                                            .replace(Regex("[^\\u0020-\\u007E]"), "?")
                                            .take(1000)
                                        if (cleanText.isNotBlank()) {
                                            val fontSize = (bounds.height() * scaleY * 0.78f).coerceIn(5f, 36f)
                                            val x = bounds.left * scaleX
                                            val y = pdfPage.mediaBox.height - bounds.bottom * scaleY
                                            stream.beginText()
                                            stream.setFont(PDType1Font.HELVETICA, fontSize)
                                            stream.setRenderingMode(PDPageContentStream.RenderingMode.NEITHER)
                                            stream.setTextMatrix(Matrix.getTranslateInstance(x, y))
                                            runCatching { stream.showText(cleanText) }
                                            stream.endText()
                                        }
                                    }
                                }
                            } finally { bitmap.recycle() }
                        }
                    }
                }
            } finally { descriptor.close() }
            return saveEdited(document, "searchable")
        } finally {
            recognizer.close()
            document.close()
        }
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

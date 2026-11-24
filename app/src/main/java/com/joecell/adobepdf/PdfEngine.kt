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
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream

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
            PdfEditorValidation.requirePageIndex(pageIndex, document.numberOfPages)
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

    fun insertImage(
        uri: Uri, imageUri: Uri, pageIndex: Int = 0,
        xRatio: Float = 0.29f, yRatio: Float = 0.30f, widthRatio: Float = 0.42f
    ): File {
        PdfEditorValidation.requireNormalizedPoint(xRatio, yRatio)
        require(widthRatio.isFinite() && widthRatio in 0.02f..1f) { "Image width must be between 2 and 100 percent." }
        val document = openDocument(uri)
        try {
            require(pageIndex in 0 until document.numberOfPages) { "Page not found." }
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
                ?: error("Could not read the selected image.")
            try {
                val image = LosslessFactory.createFromImage(document, bitmap)
                val page = document.getPage(pageIndex)
                val box = page.mediaBox
                val targetWidth = box.width * widthRatio
                val scale = minOf(targetWidth / image.width, (box.height * 0.8f) / image.height)
                val width = image.width * scale
                val height = image.height * scale
                val x = (box.width * xRatio).coerceIn(0f, box.width - width)
                val yTop = (box.height * yRatio).coerceIn(0f, box.height - height)
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use {
                    it.drawImage(image, x, box.height - yTop - height, width, height)
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
                                            stream.setRenderingMode(RenderingMode.NEITHER)
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


    /** Convert selected image files into a single multi-page PDF, preserving image aspect ratio. */
    fun imagesToPdf(imageUris: List<Uri>): File {
        require(imageUris.isNotEmpty()) { "Choose at least one image." }
        val document = PDDocument()
        try {
            imageUris.forEachIndexed { index, uri ->
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: error("Could not read image ${index + 1}.")
                try {
                    val pageSize = PDRectangle(595f, 842f)
                    val page = PDPage(pageSize)
                    document.addPage(page)
                    val maxW = pageSize.width - 48f
                    val maxH = pageSize.height - 48f
                    val scale = minOf(maxW / bitmap.width, maxH / bitmap.height)
                    val width = bitmap.width * scale
                    val height = bitmap.height * scale
                    val x = (pageSize.width - width) / 2f
                    val y = (pageSize.height - height) / 2f
                    val image = LosslessFactory.createFromImage(document, bitmap)
                    PDPageContentStream(document, page).use { it.drawImage(image, x, y, width, height) }
                } finally { bitmap.recycle() }
            }
            return saveEdited(document, "images")
        } finally { document.close() }
    }

    /**
     * Export a selected page range as JPEG files inside one ZIP archive.
     * Pages are rendered sequentially to keep peak bitmap memory bounded.
     */
    fun exportPagesAsImages(
        uri: Uri,
        firstPage: Int = 1,
        lastPage: Int,
        jpegQuality: Int = 85,
        maxDimension: Int = 2400
    ): File {
        require(firstPage >= 1 && lastPage >= firstPage) { "Choose a valid page range." }
        require(jpegQuality in 40..95) { "Image quality must be between 40 and 95." }
        require(maxDimension in 800..4000) { "Image size must be between 800 and 4000 pixels." }
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open PDF for image export.")
        try {
            val output = File(context.cacheDir, "pdf_pages_${System.currentTimeMillis()}.zip")
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                require(lastPage <= renderer.pageCount) { "The selected page range exceeds this PDF." }
                java.util.zip.ZipOutputStream(FileOutputStream(output).buffered()).use { zip ->
                    for (index in (firstPage - 1) until lastPage) {
                        renderer.openPage(index).use { page ->
                            val scale = minOf(1f, maxDimension.toFloat() / maxOf(page.width, page.height))
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            try {
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                val entry = java.util.zip.ZipEntry("page_${(index + 1).toString().padStart(4, '0')}.jpg")
                                zip.putNextEntry(entry)
                                check(bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, zip)) {
                                    "Could not encode page ${index + 1}."
                                }
                                zip.closeEntry()
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
            return output
        } catch (error: Exception) {
            throw error
        } finally {
            descriptor.close()
        }
    }

    /**
     * Create a smaller raster-optimised PDF. This deliberately flattens page content;
     * it is best for scanned/image-heavy files, not documents needing selectable text.
     */
    fun compressPdf(uri: Uri, jpegQuality: Int = 68): File {
        require(jpegQuality in 35..90) { "Compression quality must be between 35 and 90." }
        val source = openDocument(uri)
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: run { source.close(); error("Cannot open PDF for compression.") }
        val result = PDDocument()
        try {
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount == source.numberOfPages) { "PDF page count changed while opening." }
                for (index in 0 until renderer.pageCount) {
                    val rendered = renderer.openPage(index)
                    try {
                        val scale = minOf(1.5f, 1800f / maxOf(rendered.width, rendered.height))
                        val bitmap = Bitmap.createBitmap(
                            (rendered.width * scale).toInt().coerceAtLeast(1),
                            (rendered.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        try {
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            rendered.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val bytes = ByteArrayOutputStream().use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                                out.toByteArray()
                            }
                            val image = PDImageXObject.createFromByteArray(result, bytes, "compressed_page_${index + 1}")
                            val sourcePage = source.getPage(index)
                            val sourceBox = sourcePage.mediaBox
                            val pageWidth = if (sourcePage.rotation % 180 != 0) sourceBox.height else sourceBox.width
                            val pageHeight = if (sourcePage.rotation % 180 != 0) sourceBox.width else sourceBox.height
                            val page = PDPage(PDRectangle(pageWidth, pageHeight))
                            result.addPage(page)
                            PDPageContentStream(result, page).use { it.drawImage(image, 0f, 0f, pageWidth, pageHeight) }
                        } finally { bitmap.recycle() }
                    } finally { rendered.close() }
                }
            }
            return saveEdited(result, "compressed")
        } finally {
            result.close()
            source.close()
            descriptor.close()
        }
    }


    /** Add a real PDF sticky-note annotation on a selected page. */
    fun addNoteAnnotation(uri: Uri, note: String, pageIndex: Int): File {
        require(note.isNotBlank()) { "Enter a note." }
        val document = openDocument(uri)
        try {
            require(pageIndex in 0 until document.numberOfPages) { "Page not found." }
            val page = document.getPage(pageIndex)
            val box = page.mediaBox
            val annotation = PDAnnotationText().apply {
                contents = note.take(2000)
                rectangle = PDRectangle(box.width - 52f, box.height - 76f, 28f, 28f)
                titlePopup = "Note"
            }
            page.annotations.add(annotation)
            return saveEdited(document, "annotated")
        } finally { document.close() }
    }

    /** Add a clickable web link annotation to the selected page. */
    fun addWebLink(uri: Uri, url: String, pageIndex: Int): File {
        PdfEditorValidation.requireHttpUrl(url)
        val parsed = android.net.Uri.parse(url.trim())
        val document = openDocument(uri)
        try {
            require(pageIndex in 0 until document.numberOfPages) { "Page not found." }
            val page = document.getPage(pageIndex)
            val box = page.mediaBox
            val link = PDAnnotationLink().apply {
                rectangle = PDRectangle(48f, box.height - 92f, (box.width - 96f).coerceAtLeast(80f), 28f)
                action = PDActionURI().apply { this.uri = parsed.toString() }
            }
            page.annotations.add(link)
            return saveEdited(document, "linked")
        } finally { document.close() }
    }

    /** Update standard PDF document properties without changing page content. */
    fun updateMetadata(uri: Uri, title: String, author: String, subject: String, keywords: String): File {
        val document = openDocument(uri)
        try {
            document.documentInformation.apply {
                this.title = title.take(250)
                this.author = author.take(150)
                this.subject = subject.take(500)
                this.keywords = keywords.take(500)
            }
            return saveEdited(document, "metadata")
        } finally { document.close() }
    }

    /** Encrypt a PDF with a user-open password and a separate owner password. */
    fun protectPdf(uri: Uri, userPassword: String, ownerPassword: String): File {
        require(userPassword.length >= 6) { "User password must be at least 6 characters." }
        require(ownerPassword.length >= 8) { "Owner password must be at least 8 characters." }
        require(userPassword != ownerPassword) { "Use different user and owner passwords." }
        val document = openDocument(uri)
        try {
            val policy = StandardProtectionPolicy(ownerPassword, userPassword, AccessPermission()).apply {
                encryptionKeyLength = 128
                permissions = AccessPermission().apply {
                    setCanPrint(true)
                    setCanModify(false)
                    setCanExtractContent(false)
                    setCanModifyAnnotations(false)
                    setCanFillInForm(false)
                    setCanAssembleDocument(false)
                    setCanPrintDegraded(true)
                }
            }
            document.protect(policy)
            return saveEdited(document, "protected")
        } finally { document.close() }
    }


    /** Place text using normalized page coordinates (0..1, origin at top-left). */
    fun addPositionedText(uri: Uri, text: String, pageIndex: Int, xRatio: Float, yRatio: Float, fontSize: Float = 16f): File {
        require(text.isNotBlank()) { "Enter text to add." }
        PdfEditorValidation.requireNormalizedPoint(xRatio, yRatio)
        require(fontSize in 6f..72f) { "Font size must be between 6 and 72 points." }
        val document = openDocument(uri)
        try {
            require(pageIndex in 0 until document.numberOfPages) { "Page not found." }
            val page = document.getPage(pageIndex)
            val box = page.mediaBox
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, fontSize)
                stream.newLineAtOffset((box.width * xRatio).coerceIn(0f, box.width - 8f),
                    (box.height * (1f - yRatio)).coerceIn(8f, box.height - 8f))
                stream.showText(text.take(500))
                stream.endText()
            }
            return saveEdited(document, "positioned_text")
        } finally { document.close() }
    }

    /** Add a hand-drawn signature while preserving separate pen strokes. */
    fun addDrawnSignature(uri: Uri, pageIndex: Int, strokes: List<List<Pair<Float, Float>>>): File {
        PdfEditorValidation.requireSignatureStrokes(strokes)
        val document = openDocument(uri)
        try {
            PdfEditorValidation.requirePageIndex(pageIndex, document.numberOfPages)
            val page = document.getPage(pageIndex)
            val box = page.mediaBox
            PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.setStrokingColor(25, 45, 85)
                stream.setLineWidth(2.2f)
                stream.setLineCapStyle(1)
                stream.setLineJoinStyle(1)
                strokes.filter { it.size >= 2 }.forEach { stroke ->
                    val first = stroke.first()
                    stream.moveTo(first.first * box.width, (1f - first.second) * box.height)
                    stroke.drop(1).forEach { point ->
                        stream.lineTo(point.first * box.width, (1f - point.second) * box.height)
                    }
                    stream.stroke()
                }
            }
            return saveEdited(document, "signed")
        } finally { document.close() }
    }

    /**
     * Permanently redact a normalized rectangle by rasterizing every page and painting
     * the target pixels black before rebuilding the PDF. This removes selectable text,
     * vector objects, links, annotations and form fields from the output by design.
     */
    fun redactRegion(uri: Uri, pageIndex: Int, left: Float, top: Float, width: Float, height: Float): File {
        require(pageIndex >= 0) { "Page index is invalid." }
        PdfEditorValidation.requireNormalizedRectangle(left, top, width, height)
        val source = openDocument(uri)
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: run { source.close(); error("Cannot open PDF for redaction.") }
        val result = PDDocument()
        try {
            require(pageIndex < source.numberOfPages) { "Page not found." }
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount == source.numberOfPages) { "PDF page count changed while opening." }
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { rendered ->
                        val scale = minOf(1.5f, 1800f / maxOf(rendered.width, rendered.height))
                        val bitmap = Bitmap.createBitmap(
                            (rendered.width * scale).toInt().coerceAtLeast(1),
                            (rendered.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        try {
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            rendered.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            if (index == pageIndex) {
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.BLACK
                                    style = android.graphics.Paint.Style.FILL
                                }
                                android.graphics.Canvas(bitmap).drawRect(
                                    left * bitmap.width, top * bitmap.height,
                                    (left + width) * bitmap.width, (top + height) * bitmap.height, paint
                                )
                            }
                            val bytes = ByteArrayOutputStream().use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                out.toByteArray()
                            }
                            val sourcePage = source.getPage(index)
                            val sourceBox = sourcePage.mediaBox
                            val pageWidth = if (sourcePage.rotation % 180 != 0) sourceBox.height else sourceBox.width
                            val pageHeight = if (sourcePage.rotation % 180 != 0) sourceBox.width else sourceBox.height
                            val page = PDPage(PDRectangle(pageWidth, pageHeight))
                            result.addPage(page)
                            val image = PDImageXObject.createFromByteArray(result, bytes, "redacted_page_${index + 1}")
                            PDPageContentStream(result, page).use { it.drawImage(image, 0f, 0f, pageWidth, pageHeight) }
                        } finally { bitmap.recycle() }
                    }
                }
            }
            // Do not copy source metadata into the redacted output.
            return saveEdited(result, "redacted")
        } finally {
            result.close()
            source.close()
            descriptor.close()
        }
    }

    /** Crop every page to a normalized rectangle measured from the visible top-left. */
    fun cropPages(uri: Uri, left: Float, top: Float, right: Float, bottom: Float): File {
        require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f && right - left >= 0.05f && bottom - top >= 0.05f) {
            "Crop bounds must form a valid rectangle within the page."
        }
        val document = openDocument(uri)
        try {
            document.pages.forEach { page ->
                val media = page.mediaBox
                val crop = PDRectangle(
                    media.lowerLeftX + media.width * left,
                    media.lowerLeftY + media.height * (1f - bottom),
                    media.width * (right - left),
                    media.height * (bottom - top)
                )
                page.cropBox = crop
            }
            return saveEdited(document, "cropped")
        } finally { document.close() }
    }

    /** Detect mostly-white pages locally and remove them from a new PDF copy. */
    fun removeBlankPages(uri: Uri, darknessThreshold: Int = 245, maxInkRatio: Float = 0.0015f): File {
        require(darknessThreshold in 200..254 && maxInkRatio in 0.0001f..0.05f)
        val source = openDocument(uri)
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: run { source.close(); error("Cannot open PDF for blank-page detection.") }
        val result = PDDocument()
        try {
            val kept = mutableListOf<Int>()
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount == source.numberOfPages) { "PDF page count changed while opening." }
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = minOf(1f, 320f / maxOf(page.width, page.height))
                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        try {
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            val ink = pixels.count { pixel ->
                                val luminance = (android.graphics.Color.red(pixel) * 299 +
                                    android.graphics.Color.green(pixel) * 587 +
                                    android.graphics.Color.blue(pixel) * 114) / 1000
                                luminance < darknessThreshold
                            }
                            val inkRatio = ink.toFloat() / pixels.size.coerceAtLeast(1)
                            if (inkRatio > maxInkRatio) kept += index
                        } finally { bitmap.recycle() }
                    }
                }
            }
            require(kept.isNotEmpty()) { "Every page appears blank; no output was created." }
            kept.forEach { result.importPage(source.getPage(it)) }
            return saveEdited(result, "without_blank_pages")
        } finally {
            result.close()
            source.close()
            descriptor.close()
        }
    }

    /** Remove standard document information and embedded XMP metadata. */
    fun removeMetadata(uri: Uri): File {
        val document = openDocument(uri)
        try {
            document.documentInformation.apply {
                title = ""
                author = ""
                subject = ""
                keywords = ""
                creator = ""
                producer = ""
                creationDate = null
                modificationDate = null
                trapped = null
            }
            document.documentCatalog.metadata = null
            return saveEdited(document, "metadata_removed")
        } finally { document.close() }
    }

    /** Decrypt a PDF only when the supplied password is valid and export a separate copy. */
    fun unlockPdf(uri: Uri, password: String): File {
        require(password.isNotEmpty()) { "Enter the PDF password." }
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open PDF.")
        val document = input.use { PDDocument.load(it, password) }
        try {
            require(document.isEncrypted) { "This PDF is not password protected." }
            document.isAllSecurityToBeRemoved = true
            return saveEdited(document, "unlocked")
        } finally { document.close() }
    }

    /** Return editable AcroForm field names for the form-fill screen. */
    fun listFormFields(uri: Uri): List<String> {
        val document = openDocument(uri)
        try {
            val form = document.documentCatalog.acroForm ?: return emptyList()
            return form.fields.mapNotNull { it.fullyQualifiedName }.sorted()
        } finally { document.close() }
    }

    /** Fill a named AcroForm field and export a separate PDF copy. */
    fun fillFormField(uri: Uri, fieldName: String, value: String): File {
        require(fieldName.isNotBlank()) { "Choose a form field." }
        val document = openDocument(uri)
        try {
            val form = document.documentCatalog.acroForm ?: error("This PDF has no interactive form.")
            val field = form.getField(fieldName) ?: error("Form field not found.")
            field.setValue(value)
            return saveEdited(document, "form_filled")
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

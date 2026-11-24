package com.joecell.adobepdf

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Ink = Color(0xFF15233B)
private val Accent = Color(0xFF365CF5)
private val Canvas = Color(0xFFF4F6FB)

private enum class PdfScreen { HOME, VIEWER, ORGANIZER }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Canvas)) { PdfHome() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfHome() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { PdfEngine(context) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var activeUri by remember { mutableStateOf<Uri?>(null) }
    var screen by remember { mutableStateOf(PdfScreen.HOME) }
    var message by remember { mutableStateOf("Your documents stay on this device during processing.") }
    var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<File?>(null) }
    var compressionQuality by remember { mutableFloatStateOf(68f) }
    var showCompressionDialog by remember { mutableStateOf(false) }
    var showImageExportDialog by remember { mutableStateOf(false) }
    var exportFirstPage by remember { mutableStateOf("1") }
    var exportLastPage by remember { mutableStateOf("1") }
    var exportQuality by remember { mutableStateOf("85") }
    var exportMaxDimension by remember { mutableStateOf("2400") }
    var zipOutput by remember { mutableStateOf<File?>(null) }
    var textToAdd by remember { mutableStateOf("Approved") }
    var pageCount by remember { mutableIntStateOf(0) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var signatureText by remember { mutableStateOf("") }
    var signaturePage by remember { mutableStateOf("1") }
    var notePage by remember { mutableStateOf("1") }
    var highlightPage by remember { mutableStateOf("1") }
    var showHighlightDialog by remember { mutableStateOf(false) }
    var linkPage by remember { mutableStateOf("1") }
    var imagePage by remember { mutableStateOf("1") }
    var imageX by remember { mutableStateOf("29") }
    var imageY by remember { mutableStateOf("30") }
    var imageWidth by remember { mutableStateOf("42") }
    var showImagePlacementDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("https://") }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var metadataTitle by remember { mutableStateOf("") }
    var metadataAuthor by remember { mutableStateOf("") }
    var metadataSubject by remember { mutableStateOf("") }
    var metadataKeywords by remember { mutableStateOf("") }
    var showProtectDialog by remember { mutableStateOf(false) }
    var userPassword by remember { mutableStateOf("") }
    var ownerPassword by remember { mutableStateOf("") }
    var showCropDialog by remember { mutableStateOf(false) }
    var cropLeft by remember { mutableStateOf("5") }
    var cropTop by remember { mutableStateOf("5") }
    var cropRight by remember { mutableStateOf("95") }
    var cropBottom by remember { mutableStateOf("95") }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var unlockPassword by remember { mutableStateOf("") }

    var showPositionDialog by remember { mutableStateOf(false) }
    var positionX by remember { mutableStateOf("10") }
    var positionY by remember { mutableStateOf("15") }
    var positionPage by remember { mutableStateOf("1") }
    var positionFontSize by remember { mutableStateOf("16") }
    var showRedactDialog by remember { mutableStateOf(false) }
    var redactLeft by remember { mutableStateOf("25") }
    var redactTop by remember { mutableStateOf("25") }
    var redactWidth by remember { mutableStateOf("35") }
    var redactHeight by remember { mutableStateOf("12") }
    var redactPage by remember { mutableStateOf("1") }
    var showFormDialog by remember { mutableStateOf(false) }
    var formFieldNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var formFieldName by remember { mutableStateOf("") }
    var formFieldValue by remember { mutableStateOf("") }
    var signatureStrokes by remember { mutableStateOf<List<List<Pair<Float, Float>>>>(emptyList()) }
    var showDrawnSignatureDialog by remember { mutableStateOf(false) }

    val pageOrder = remember { mutableStateListOf<Int>() }
    val selectedPages = remember { mutableStateListOf<Int>() }
    val rotations = remember { mutableStateMapOf<Int, Int>() }

    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected = it }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selected = listOf(uri)
            activeUri = uri
            screen = PdfScreen.VIEWER
        }
    }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val file = zipOutput
        if (uri != null && file != null) runCatching { engine.saveToUri(file, context.contentResolver, uri) }
            .onSuccess { message = "Images ZIP saved successfully." }
            .onFailure { message = it.message ?: "ZIP save failed." }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = output
        if (uri != null && file != null) runCatching { engine.saveToUri(file, context.contentResolver, uri) }
            .onSuccess { message = "Saved successfully. Your original PDF is unchanged." }
            .onFailure { message = it.message ?: "Save failed." }
    }

    LaunchedEffect(activeUri) {
        val uri = activeUri ?: return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { engine.pageCount(uri) } }
            .onSuccess { count ->
                pageCount = count
                pageOrder.clear()
                pageOrder.addAll((0 until count).toList())
                selectedPages.clear()
                rotations.clear()
            }
            .onFailure { message = it.message ?: "Could not read this PDF." }
    }

    fun runOperation(label: String, task: suspend () -> File) {
        scope.launch {
            busy = true
            message = "$label…"
            try {
                output = withContext(Dispatchers.IO) { task() }
                message = "$label completed. Save the new PDF to keep the changes."
            } catch (e: Exception) { message = e.message ?: "$label failed." }
            finally { busy = false }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri ->
        val documentUri = activeUri
        if (imageUri != null && documentUri != null) runOperation("Inserting image") {
            engine.insertImage(documentUri, imageUri, (imagePage.toIntOrNull() ?: 1) - 1,
                (imageX.toFloatOrNull() ?: 29f) / 100f, (imageY.toFloatOrNull() ?: 30f) / 100f,
                (imageWidth.toFloatOrNull() ?: 42f) / 100f)
        }
    }

    val pickImagesForPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { imageUris ->
        if (imageUris.isNotEmpty()) runOperation("Converting images to PDF") { engine.imagesToPdf(imageUris) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (screen == PdfScreen.HOME) "Adobe PDF" else if (screen == PdfScreen.VIEWER) "PDF Viewer" else "Organize pages", fontWeight = FontWeight.Bold, color = Ink)
                        Text(if (screen == PdfScreen.HOME) "Your documents. Your device." else activeUri?.lastPathSegment?.substringAfterLast('/') ?: "Document", fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                    }
                },
                navigationIcon = {
                    if (screen != PdfScreen.HOME) IconButton(onClick = { screen = if (screen == PdfScreen.ORGANIZER) PdfScreen.VIEWER else PdfScreen.HOME }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (screen == PdfScreen.VIEWER && activeUri != null) IconButton(onClick = { screen = PdfScreen.ORGANIZER }) { Icon(Icons.Default.GridView, "Organize pages") }
                    if (screen == PdfScreen.HOME) IconButton(onClick = { pickMany.launch(arrayOf("application/pdf")) }) { Icon(Icons.Default.Add, "Add PDFs") }
                }
            )
        },
        containerColor = Canvas
    ) { padding ->
        when (screen) {
            PdfScreen.HOME -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Ink), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("PDF WORKSPACE", color = Color(0xFFB7C8FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Everything you need\nto work with PDFs.", color = Color.White, fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                            Text("Read, organize and edit documents with on-device tools.", color = Color(0xFFD7DEEF))
                            Button(onClick = { pickOne.launch(arrayOf("application/pdf")) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)) {
                                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Open and read PDF")
                            }
                        }
                    }
                }
                item { Text("Selected documents", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) }
                if (selected.isEmpty()) {
                    item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PictureAsPdf, null, tint = Accent, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp)); Column { Text("No PDF selected", fontWeight = FontWeight.SemiBold); Text("Choose one or more files to get started.", color = Color.Gray, fontSize = 12.sp) }
                        }
                    } }
                } else {
                    itemsIndexed(selected) { _, uri ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PictureAsPdf, null, tint = Accent)
                                Spacer(Modifier.width(10.dp))
                                Text(uri.lastPathSegment?.substringAfterLast('/') ?: "Selected PDF", Modifier.weight(1f), maxLines = 1)
                                IconButton(onClick = { activeUri = uri; screen = PdfScreen.VIEWER }) { Icon(Icons.Default.Visibility, "View") }
                                IconButton(onClick = { selected = selected - uri }) { Icon(Icons.Default.Close, "Remove") }
                            }
                        }
                    }
                }
                item { Text("PDF tools", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) }
                item { ToolCard("Merge PDFs", "Combine selected PDFs into one document", Icons.Default.MergeType, selected.size < 2 || busy) {
                    runOperation("Merging PDFs") { engine.merge(selected) }
                } }
                item { ToolCard("Extract first page", "Create a new PDF from page one", Icons.Default.ContentCut, selected.size != 1 || busy) {
                    runOperation("Extracting page") { engine.extractPages(selected.first(), listOf(0)) }
                } }
                item { ToolCard("Rotate pages", "Rotate every page by 90 degrees", Icons.Default.RotateRight, selected.size != 1 || busy) {
                    runOperation("Rotating pages") { engine.rotateAllPages(selected.first()) }
                } }
                item { ToolCard("Add text", "Place a text label on the first page", Icons.Default.Edit, selected.size != 1 || busy) { showTextDialog = true } }
                item { ToolCard("Compress PDF", "Choose image quality and reduce file size; output is flattened", Icons.Default.Compress, selected.size != 1 || busy) {
                    showCompressionDialog = true
                } }
                item { ToolCard("PDF to images", "Export a page range as JPEG images in a ZIP file", Icons.Default.Image, selected.size != 1 || busy) {
                    exportLastPage = pageCount.takeIf { it > 0 }?.toString() ?: "1"
                    showImageExportDialog = true
                } }
                item { ToolCard("Images to PDF", "Combine photos and images into a multi-page PDF", Icons.Default.Collections, busy) {
                    pickImagesForPdf.launch("image/*")
                } }
                item { ToolCard("Edit PDF metadata", "Set document title, author, subject and keywords", Icons.Default.Info, selected.size != 1 || busy) {
                    showMetadataDialog = true
                } }
                item { ToolCard("PDF Multi Tool", "Merge, extract, reorder, duplicate, rotate and delete pages", Icons.Default.Dashboard, selected.size != 1 || busy) {
                    activeUri = selected.first(); screen = PdfScreen.ORGANIZER
                } }
                item { ToolCard("Crop PDF", "Trim page margins with a custom crop rectangle", Icons.Default.Crop, selected.size != 1 || busy) { showCropDialog = true } }
                item { ToolCard("Remove blank pages", "Detect mostly-white pages on-device and remove them", Icons.Default.AutoDelete, selected.size != 1 || busy) {
                    runOperation("Removing blank pages") { engine.removeBlankPages(selected.first()) }
                } }
                item { ToolCard("Remove metadata", "Clear title, author, dates and embedded XMP metadata", Icons.Default.DeleteSweep, selected.size != 1 || busy) {
                    runOperation("Removing metadata") { engine.removeMetadata(selected.first()) }
                } }
                item { ToolCard("Unlock PDF", "Remove password protection using the correct document password", Icons.Default.LockOpen, selected.size != 1 || busy) { showUnlockDialog = true } }
                item { ToolCard("Password protect PDF", "Encrypt a PDF with an open password and owner password", Icons.Default.Lock, selected.size != 1 || busy) {
                    showProtectDialog = true
                } }
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Result", fontWeight = FontWeight.Bold, color = Ink)
                            Text(message, color = Color.Gray, fontSize = 13.sp)
                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (output != null) Button(onClick = { saveFile.launch(output!!.name) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.SaveAlt, null); Spacer(Modifier.width(8.dp)); Text("Save PDF")
                            }
                            if (zipOutput != null) Button(onClick = { saveZip.launch(zipOutput!!.name) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.SaveAlt, null); Spacer(Modifier.width(8.dp)); Text("Save image ZIP")
                            }
                        }
                    }
                }
                item { Text("Convert images to PDF or reduce image-heavy PDF file sizes with on-device tools.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 24.dp)) }
            }

            PdfScreen.VIEWER -> {
                val uri = activeUri
                if (uri == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Open a PDF to view it.") }
                else Column(Modifier.fillMaxSize().padding(padding)) {
                    var zoom by remember(uri) { mutableFloatStateOf(1f) }
                    var pan by remember(uri) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    val zoomState = rememberTransformableState { zoomChange, panChange, _ ->
                        val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                        zoom = nextZoom
                        pan = if (nextZoom <= 1.001f) androidx.compose.ui.geometry.Offset.Zero else pan + panChange
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$pageCount pages · ${(zoom * 100).toInt()}%", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(1f); if (zoom <= 1.001f) pan = androidx.compose.ui.geometry.Offset.Zero }, enabled = zoom > 1.001f) { Icon(Icons.Default.Remove, "Zoom out") }
                        IconButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(5f) }) { Icon(Icons.Default.Add, "Zoom in") }
                        TextButton(onClick = { zoom = 1f; pan = androidx.compose.ui.geometry.Offset.Zero }) { Text("Fit width") }
                        TextButton(onClick = { zoom = 0.85f; pan = androidx.compose.ui.geometry.Offset.Zero }) { Text("Fit page") }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = { showTextDialog = true }) { Icon(Icons.Default.TextFields, null); Text("Text") }
                        OutlinedButton(enabled = !busy, onClick = { showImagePlacementDialog = true }) { Icon(Icons.Default.AddPhotoAlternate, null); Text("Image") }
                        OutlinedButton(enabled = !busy, onClick = { showHighlightDialog = true }) { Icon(Icons.Default.Highlight, null); Text("Highlight") }
                        OutlinedButton(enabled = !busy, onClick = { showSignatureDialog = true }) { Icon(Icons.Default.Draw, null); Text("Signature") }
                        OutlinedButton(enabled = !busy, onClick = { showNoteDialog = true }) { Icon(Icons.Default.Comment, null); Text("Note") }
                        OutlinedButton(enabled = !busy, onClick = { showLinkDialog = true }) { Icon(Icons.Default.Link, null); Text("Link") }
                        OutlinedButton(enabled = !busy, onClick = { runOperation("Creating searchable PDF") { engine.createSearchablePdf(uri) } }) { Icon(Icons.Default.Search, null); Text("OCR") }

                        OutlinedButton(enabled = !busy, onClick = { showPositionDialog = true }) { Icon(Icons.Default.OpenWith, null); Text("Position text") }
                        OutlinedButton(enabled = !busy, onClick = { signatureStrokes = emptyList(); showDrawnSignatureDialog = true }) { Icon(Icons.Default.Draw, null); Text("Draw signature") }
                        OutlinedButton(enabled = !busy, onClick = { showRedactDialog = true }) { Icon(Icons.Default.Block, null); Text("Redact") }
                        OutlinedButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    formFieldNames = withContext(Dispatchers.IO) { engine.listFormFields(uri) }
                                    formFieldName = formFieldNames.firstOrNull().orEmpty()
                                    formFieldValue = ""
                                    showFormDialog = true
                                    if (formFieldNames.isEmpty()) message = "This PDF does not contain editable AcroForm fields."
                                } catch (e: Exception) { message = e.message ?: "Could not inspect form fields." }
                                finally { busy = false }
                            }
                        }) { Icon(Icons.Default.EditNote, null); Text("Fill form") }

                    }
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth()
                            .transformable(zoomState)
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                            },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed((0 until pageCount).toList()) { index, _ ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PdfPagePreview(uri, index, 1400, Modifier.fillMaxWidth())
                                Text("Page ${index + 1}", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                    if (busy || output != null || message != "Your documents stay on this device during processing.") {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (output != null) "Editor result · ready to save" else "Editor status", color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text(message, color = Color.Gray, fontSize = 11.sp, maxLines = 2)
                                }
                                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                if (output != null) TextButton(onClick = { saveFile.launch(output!!.name) }) { Icon(Icons.Default.SaveAlt, null); Text("Save") }
                            }
                        }
                    }
                }
            }

            PdfScreen.ORGANIZER -> {
                val uri = activeUri
                if (uri == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Open a PDF first.") }
                else Column(Modifier.fillMaxSize().padding(padding)) {
                    Text("Select pages, then choose an action. Original file stays untouched.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy && pageOrder.isNotEmpty(), onClick = {
                            runOperation("Applying page order") { engine.rebuild(uri, pageOrder.toList(), rotations.toMap()) }
                        }) { Icon(Icons.Default.Save, null); Text("Apply order") }
                        OutlinedButton(enabled = selectedPages.isNotEmpty() && !busy, onClick = {
                            val removed = selectedPages.toSet()
                            val newOrder = pageOrder.filterNot { it in removed }
                            runOperation("Removing selected pages") { engine.rebuild(uri, newOrder, rotations.toMap()) }
                        }) { Icon(Icons.Default.Delete, null); Text("Delete") }
                        OutlinedButton(enabled = selectedPages.isNotEmpty() && !busy, onClick = {
                            val chosen = selectedPages.sorted()
                            runOperation("Duplicating selected pages") { engine.rebuild(uri, pageOrder.flatMap { p -> if (p in chosen) listOf(p, p) else listOf(p) }, rotations.toMap()) }
                        }) { Icon(Icons.Default.ContentCopy, null); Text("Duplicate") }
                        OutlinedButton(enabled = selectedPages.isNotEmpty() && !busy, onClick = {
                            val chosen = pageOrder.filter { it in selectedPages }
                            runOperation("Extracting selected pages") { engine.extractPages(uri, chosen) }
                        }) { Icon(Icons.Default.FileDownload, null); Text("Extract") }
                        OutlinedButton(enabled = selectedPages.isNotEmpty() && !busy, onClick = {
                            val updated = rotations.toMutableMap()
                            selectedPages.forEach { updated[it] = ((updated[it] ?: 0) + 90) % 360 }
                            rotations.clear(); rotations.putAll(updated)
                            runOperation("Rotating selected pages") { engine.rebuild(uri, pageOrder.toList(), rotations.toMap()) }
                        }) { Icon(Icons.Default.RotateRight, null); Text("Rotate") }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${selectedPages.size} selected", color = Ink, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { if (selectedPages.size == pageOrder.size) selectedPages.clear() else { selectedPages.clear(); selectedPages.addAll(pageOrder) } }) { Text("Select all") }
                    }
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f).padding(horizontal = 10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        gridItemsIndexed(pageOrder) { position, sourceIndex ->
                            val isSelected = sourceIndex in selectedPages
                            Card(Modifier.padding(5.dp).fillMaxWidth().clickable {
                                if (isSelected) selectedPages.remove(sourceIndex) else selectedPages.add(sourceIndex)
                            }, colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFE5EBFF) else Color.White), border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Accent) else null) {
                                Column(Modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    PdfPagePreview(uri, sourceIndex, 360, Modifier.fillMaxWidth().height(130.dp))
                                    Text("Page ${sourceIndex + 1}", fontSize = 11.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = isSelected, onCheckedChange = { checked -> if (checked) selectedPages.add(sourceIndex) else selectedPages.remove(sourceIndex) })
                                        IconButton(enabled = position > 0, onClick = {
                                            val swap = pageOrder[position - 1]; pageOrder[position - 1] = pageOrder[position]; pageOrder[position] = swap
                                        }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.ArrowBack, "Move earlier", modifier = Modifier.size(16.dp)) }
                                        IconButton(enabled = position < pageOrder.lastIndex, onClick = {
                                            val swap = pageOrder[position + 1]; pageOrder[position + 1] = pageOrder[position]; pageOrder[position] = swap
                                        }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.ArrowForward, "Move later", modifier = Modifier.size(16.dp)) }
                                    }
                                }
                            }
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Card(Modifier.fillMaxWidth().padding(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(message, color = Color.Gray, fontSize = 12.sp)
                            if (output != null) Button(onClick = { saveFile.launch(output!!.name) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.SaveAlt, null); Spacer(Modifier.width(8.dp)); Text("Save modified PDF") }
                        }
                    }
                }
            }
        }
    }
    if (showTextDialog) AlertDialog(
        onDismissRequest = { showTextDialog = false },
        title = { Text("Add text to first page") },
        text = { OutlinedTextField(value = textToAdd, onValueChange = { textToAdd = it }, label = { Text("Text") }) },
        confirmButton = { TextButton(onClick = { showTextDialog = false; runOperation("Adding text") { engine.addTextOverlay(activeUri ?: selected.first(), textToAdd, 0) } }) { Text("Add text") } },
        dismissButton = { TextButton(onClick = { showTextDialog = false }) { Text("Cancel") } }
    )

    if (showHighlightDialog) AlertDialog(
        onDismissRequest = { showHighlightDialog = false },
        title = { Text("Add highlight") },
        text = { OutlinedTextField(value = highlightPage, onValueChange = { highlightPage = it }, label = { Text("Page number") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            showHighlightDialog = false
            activeUri?.let { uri -> runOperation("Adding highlight") { engine.addHighlight(uri, (highlightPage.toIntOrNull() ?: 1) - 1) } }
        }) { Text("Add highlight") } },
        dismissButton = { TextButton(onClick = { showHighlightDialog = false }) { Text("Cancel") } }
    )

    if (showNoteDialog) AlertDialog(
        onDismissRequest = { showNoteDialog = false },
        title = { Text("Add note annotation") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Note") }, minLines = 3)
            OutlinedTextField(value = notePage, onValueChange = { notePage = it }, label = { Text("Page number") }, singleLine = true)
            Text("Adds a PDF sticky-note annotation to the selected page.", color = Color.Gray, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = { showNoteDialog = false; activeUri?.let { uri -> runOperation("Adding note") { engine.addNoteAnnotation(uri, noteText, (notePage.toIntOrNull() ?: 1) - 1) } } }) { Text("Add note") } },
        dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") } }
    )
    if (showLinkDialog) AlertDialog(
        onDismissRequest = { showLinkDialog = false },
        title = { Text("Add web link") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = linkText, onValueChange = { linkText = it }, label = { Text("https:// address") }, singleLine = true)
            OutlinedTextField(value = linkPage, onValueChange = { linkPage = it }, label = { Text("Page number") }, singleLine = true)
            Text("Adds a clickable link annotation to the selected page.", color = Color.Gray, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = { showLinkDialog = false; activeUri?.let { uri -> runOperation("Adding link") { engine.addWebLink(uri, linkText, (linkPage.toIntOrNull() ?: 1) - 1) } } }) { Text("Add link") } },
        dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") } }
    )
    if (showImageExportDialog) AlertDialog(
        onDismissRequest = { showImageExportDialog = false },
        title = { Text("Export PDF pages as images") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = exportFirstPage, onValueChange = { exportFirstPage = it }, label = { Text("First page") }, singleLine = true)
            OutlinedTextField(value = exportLastPage, onValueChange = { exportLastPage = it }, label = { Text("Last page") }, singleLine = true)
            OutlinedTextField(value = exportQuality, onValueChange = { exportQuality = it }, label = { Text("JPEG quality (40–95)") }, singleLine = true)
            OutlinedTextField(value = exportMaxDimension, onValueChange = { exportMaxDimension = it }, label = { Text("Maximum image dimension (800–4000 px)") }, singleLine = true)
            Text("Each page is rendered separately to limit memory use. The ZIP contains one JPEG per page.", color = Color.Gray, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val uri = selected.singleOrNull()
            val first = exportFirstPage.toIntOrNull() ?: 0
            val last = exportLastPage.toIntOrNull() ?: 0
            val quality = exportQuality.toIntOrNull() ?: 0
            val dimension = exportMaxDimension.toIntOrNull() ?: 0
            showImageExportDialog = false
            if (uri != null) {
                runOperation("Exporting PDF pages to images") {
                    engine.exportPagesAsImages(uri, first, last, quality, dimension).also { zipOutput = it }
                }
            }
        }) { Text("Export images") } },
        dismissButton = { TextButton(onClick = { showImageExportDialog = false }) { Text("Cancel") } }
    )

    if (showCompressionDialog) AlertDialog(
        onDismissRequest = { showCompressionDialog = false },
        title = { Text("Compress PDF") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("JPEG quality: ${compressionQuality.toInt()}%", fontWeight = FontWeight.SemiBold)
            Slider(value = compressionQuality, onValueChange = { compressionQuality = it }, valueRange = 35f..90f, steps = 10)
            Text("Lower quality usually creates a smaller file. This mode rasterizes pages, so selectable text, vector graphics, forms, links and annotations are not preserved.", color = Color.Gray, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = {
            showCompressionDialog = false
            selected.firstOrNull()?.let { uri -> runOperation("Compressing PDF") { engine.compressPdf(uri, compressionQuality.toInt()) } }
        }) { Text("Compress") } },
        dismissButton = { TextButton(onClick = { showCompressionDialog = false }) { Text("Cancel") } }
    )

    if (showMetadataDialog) AlertDialog(
        onDismissRequest = { showMetadataDialog = false },
        title = { Text("PDF document properties") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = metadataTitle, onValueChange = { metadataTitle = it }, label = { Text("Title") }, singleLine = true)
            OutlinedTextField(value = metadataAuthor, onValueChange = { metadataAuthor = it }, label = { Text("Author") }, singleLine = true)
            OutlinedTextField(value = metadataSubject, onValueChange = { metadataSubject = it }, label = { Text("Subject") }, singleLine = true)
            OutlinedTextField(value = metadataKeywords, onValueChange = { metadataKeywords = it }, label = { Text("Keywords") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { showMetadataDialog = false; selected.firstOrNull()?.let { uri -> runOperation("Updating metadata") { engine.updateMetadata(uri, metadataTitle, metadataAuthor, metadataSubject, metadataKeywords) } } }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = { showMetadataDialog = false }) { Text("Cancel") } }
    )
    if (showCropDialog) AlertDialog(
        onDismissRequest = { showCropDialog = false },
        title = { Text("Crop PDF pages") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Set the visible page area as percentages. Values apply to every page.", color = Color.Gray, fontSize = 12.sp)
            OutlinedTextField(value = cropLeft, onValueChange = { cropLeft = it }, label = { Text("Left %") }, singleLine = true)
            OutlinedTextField(value = cropTop, onValueChange = { cropTop = it }, label = { Text("Top %") }, singleLine = true)
            OutlinedTextField(value = cropRight, onValueChange = { cropRight = it }, label = { Text("Right %") }, singleLine = true)
            OutlinedTextField(value = cropBottom, onValueChange = { cropBottom = it }, label = { Text("Bottom %") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = {
            val l = cropLeft.toFloatOrNull(); val t = cropTop.toFloatOrNull()
            val r = cropRight.toFloatOrNull(); val b = cropBottom.toFloatOrNull()
            if (l == null || t == null || r == null || b == null) message = "Enter valid crop percentages."
            else { showCropDialog = false; selected.firstOrNull()?.let { uri -> runOperation("Cropping PDF") { engine.cropPages(uri, l / 100f, t / 100f, r / 100f, b / 100f) } } }
        }) { Text("Crop pages") } },
        dismissButton = { TextButton(onClick = { showCropDialog = false }) { Text("Cancel") } }
    )
    if (showUnlockDialog) AlertDialog(
        onDismissRequest = { showUnlockDialog = false },
        title = { Text("Unlock PDF") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = unlockPassword, onValueChange = { unlockPassword = it }, label = { Text("Current PDF password") }, singleLine = true)
            Text("Requires the correct password. The original file is not changed.", color = Color.Gray, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = {
            showUnlockDialog = false
            selected.firstOrNull()?.let { uri -> runOperation("Unlocking PDF") { engine.unlockPdf(uri, unlockPassword) } }
            unlockPassword = ""
        }) { Text("Unlock") } },
        dismissButton = { TextButton(onClick = { showUnlockDialog = false }) { Text("Cancel") } }
    )
    if (showProtectDialog) AlertDialog(
        onDismissRequest = { showProtectDialog = false },
        title = { Text("Password protect PDF") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = userPassword, onValueChange = { userPassword = it }, label = { Text("Open password (6+ characters)") }, singleLine = true)
            OutlinedTextField(value = ownerPassword, onValueChange = { ownerPassword = it }, label = { Text("Owner password (8+ characters)") }, singleLine = true)
            Text("The open password is required to view the output. Keep both passwords safe.", color = Color.Gray, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = { showProtectDialog = false; selected.firstOrNull()?.let { uri -> runOperation("Protecting PDF") { engine.protectPdf(uri, userPassword, ownerPassword) } } }) { Text("Protect") } },
        dismissButton = { TextButton(onClick = { showProtectDialog = false }) { Text("Cancel") } }
    )


    if (showDrawnSignatureDialog) AlertDialog(
        onDismissRequest = { showDrawnSignatureDialog = false },
        title = { Text("Draw your signature") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = signaturePage, onValueChange = { signaturePage = it }, label = { Text("Page number") }, singleLine = true)
            Text("Sign in the box below to add your signature to that page.", color = Color.Gray, fontSize = 12.sp)
            Canvas(
                modifier = Modifier.fillMaxWidth().height(150.dp)
                    .background(Color(0xFFF7F9FF), RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val point = (start.x / size.width).coerceIn(0f, 1f) to (start.y / size.height).coerceIn(0f, 1f)
                                signatureStrokes = signatureStrokes + listOf(listOf(point))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val point = (change.position.x / size.width).coerceIn(0f, 1f) to (change.position.y / size.height).coerceIn(0f, 1f)
                                signatureStrokes = signatureStrokes.dropLast(1) + listOf(signatureStrokes.last() + point)
                            }
                        )
                    }
            ) {
                signatureStrokes.forEach { stroke ->
                    for (i in 1 until stroke.size) {
                        drawLine(
                            color = Color(0xFF192D55),
                            start = androidx.compose.ui.geometry.Offset(stroke[i - 1].first * size.width, stroke[i - 1].second * size.height),
                            end = androidx.compose.ui.geometry.Offset(stroke[i].first * size.width, stroke[i].second * size.height),
                            strokeWidth = 3.5.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }
            }
            TextButton(onClick = { signatureStrokes = emptyList() }) { Text("Clear signature") }
        } },
        confirmButton = { TextButton(enabled = signatureStrokes.sumOf { it.size } > 1, onClick = {
            showDrawnSignatureDialog = false
            activeUri?.let { uri ->
                runOperation("Adding handwritten signature") { engine.addDrawnSignature(uri, (signaturePage.toIntOrNull() ?: 1) - 1, signatureStrokes) }
            }
        }) { Text("Add signature") } },
        dismissButton = { TextButton(onClick = { showDrawnSignatureDialog = false }) { Text("Cancel") } }
    )

    if (showImagePlacementDialog) AlertDialog(
        onDismissRequest = { showImagePlacementDialog = false },
        title = { Text("Place image on PDF") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            PositionPicker(
                x = (imageX.toFloatOrNull() ?: 29f) / 100f,
                y = (imageY.toFloatOrNull() ?: 30f) / 100f,
                onPositionChange = { x, y -> imageX = (x * 100).toInt().toString(); imageY = (y * 100).toInt().toString() }
            )
            OutlinedTextField(value = imagePage, onValueChange = { imagePage = it }, label = { Text("Page number") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = imageX, onValueChange = { imageX = it }, label = { Text("Left %") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = imageY, onValueChange = { imageY = it }, label = { Text("Top %") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = imageWidth, onValueChange = { imageWidth = it }, label = { Text("Image width %") }, singleLine = true)
            Text("Choose the image next. Its aspect ratio is preserved; placement and size are measured from the page.", color = Color.Gray, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = { showImagePlacementDialog = false; pickImage.launch("image/*") }) { Text("Choose image") } },
        dismissButton = { TextButton(onClick = { showImagePlacementDialog = false }) { Text("Cancel") } }
    )

    if (showPositionDialog) AlertDialog(
        onDismissRequest = { showPositionDialog = false },
        title = { Text("Position text on a page") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = textToAdd, onValueChange = { textToAdd = it }, label = { Text("Text") })
            PositionPicker(
                x = (positionX.toFloatOrNull() ?: 10f) / 100f,
                y = (positionY.toFloatOrNull() ?: 15f) / 100f,
                onPositionChange = { x, y -> positionX = (x * 100).toInt().toString(); positionY = (y * 100).toInt().toString() }
            )
            OutlinedTextField(value = positionPage, onValueChange = { positionPage = it }, label = { Text("Page number") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = positionX, onValueChange = { positionX = it }, label = { Text("X %") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = positionY, onValueChange = { positionY = it }, label = { Text("Y %") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = positionFontSize, onValueChange = { positionFontSize = it }, label = { Text("Font size (pt)") }, singleLine = true)
            Text("Coordinates are percentages measured from the page's top-left corner.", color = Color.Gray, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = {
            showPositionDialog = false
            activeUri?.let { uri ->
                runOperation("Placing text") {
                    engine.addPositionedText(uri, textToAdd, (positionPage.toIntOrNull() ?: 1) - 1,
                        (positionX.toFloatOrNull() ?: 10f) / 100f, (positionY.toFloatOrNull() ?: 15f) / 100f,
                        positionFontSize.toFloatOrNull() ?: 16f)
                }
            }
        }) { Text("Place text") } },
        dismissButton = { TextButton(onClick = { showPositionDialog = false }) { Text("Cancel") } }
    )

    if (showRedactDialog) AlertDialog(
        onDismissRequest = { showRedactDialog = false },
        title = { Text("Permanently redact a region") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = redactPage, onValueChange = { redactPage = it }, label = { Text("Page number") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = redactLeft, onValueChange = { redactLeft = it }, label = { Text("Left %") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = redactTop, onValueChange = { redactTop = it }, label = { Text("Top %") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = redactWidth, onValueChange = { redactWidth = it }, label = { Text("Width %") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = redactHeight, onValueChange = { redactHeight = it }, label = { Text("Height %") }, modifier = Modifier.weight(1f))
            }
            Text("Warning: creates a flattened, image-based PDF. The selected page's underlying text and objects are removed, and form fields, links and annotations are not retained.", color = Color(0xFFB3261E), fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = {
            showRedactDialog = false
            activeUri?.let { uri ->
                runOperation("Applying permanent redaction") {
                    engine.redactRegion(uri, (redactPage.toIntOrNull() ?: 1) - 1,
                        (redactLeft.toFloatOrNull() ?: 25f) / 100f, (redactTop.toFloatOrNull() ?: 25f) / 100f,
                        (redactWidth.toFloatOrNull() ?: 35f) / 100f, (redactHeight.toFloatOrNull() ?: 12f) / 100f)
                }
            }
        }) { Text("Redact permanently") } },
        dismissButton = { TextButton(onClick = { showRedactDialog = false }) { Text("Cancel") } }
    )

    if (showFormDialog) AlertDialog(
        onDismissRequest = { showFormDialog = false },
        title = { Text("Fill PDF form") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (formFieldNames.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(value = formFieldName, onValueChange = {}, readOnly = true, label = { Text("Form field") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        formFieldNames.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { formFieldName = name; expanded = false }) }
                    }
                }
                OutlinedTextField(value = formFieldValue, onValueChange = { formFieldValue = it }, label = { Text("Value") })
            } else Text("No editable AcroForm fields were found in this document.")
        } },
        confirmButton = { TextButton(enabled = formFieldName.isNotBlank() && formFieldValue.isNotEmpty(), onClick = {
            showFormDialog = false
            activeUri?.let { uri -> runOperation("Filling PDF form") { engine.fillFormField(uri, formFieldName, formFieldValue) } }
        }) { Text("Apply value") } },
        dismissButton = { TextButton(onClick = { showFormDialog = false }) { Text("Close") } }
    )

    if (showSignatureDialog) AlertDialog(
        onDismissRequest = { showSignatureDialog = false },
        title = { Text("Add typed signature") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = signatureText, onValueChange = { signatureText = it }, label = { Text("Name or signature text") }, singleLine = true)
            OutlinedTextField(value = signaturePage, onValueChange = { signaturePage = it }, label = { Text("Page number") }, singleLine = true)
            Text("Adds an italic signature near the bottom of the selected page.", color = Color.Gray, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = { showSignatureDialog = false; activeUri?.let { uri -> runOperation("Adding signature") { engine.addTypedSignature(uri, signatureText, (signaturePage.toIntOrNull() ?: 1) - 1) } } }) { Text("Add signature") } },
        dismissButton = { TextButton(onClick = { showSignatureDialog = false }) { Text("Cancel") } }
    )
}

@Composable
private fun PositionPicker(x: Float, y: Float, onPositionChange: (Float, Float) -> Unit) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxWidth().height(116.dp)
            .background(Color(0xFFF1F4FC), RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { point ->
                        onPositionChange((point.x / size.width).coerceIn(0f, 1f), (point.y / size.height).coerceIn(0f, 1f))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onPositionChange((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                    }
                )
            }
    ) {
        drawRoundRect(color = Color.White, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.06f, size.height * 0.08f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.88f, size.height * 0.84f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
        val px = size.width * (0.06f + x.coerceIn(0f, 1f) * 0.88f)
        val py = size.height * (0.08f + y.coerceIn(0f, 1f) * 0.84f)
        drawCircle(Accent, radius = 8.dp.toPx(), center = androidx.compose.ui.geometry.Offset(px, py))
        drawLine(Accent.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(px - 14.dp.toPx(), py), androidx.compose.ui.geometry.Offset(px + 14.dp.toPx(), py), strokeWidth = 1.dp.toPx())
        drawLine(Accent.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(px, py - 14.dp.toPx()), androidx.compose.ui.geometry.Offset(px, py + 14.dp.toPx()), strokeWidth = 1.dp.toPx())
    }
    Text("Drag the marker to position. Set image width below to resize.", color = Color.Gray, fontSize = 11.sp)
}

@Composable
private fun PdfPagePreview(uri: Uri, pageIndex: Int, maxDimension: Int, modifier: Modifier = Modifier) {
    var bitmap by remember(uri, pageIndex, maxDimension) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(uri, pageIndex, maxDimension) {
        bitmap = withContext(Dispatchers.IO) {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open PDF.")
            descriptor.use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                    require(pageIndex in 0 until renderer.pageCount) { "Page not found." }
                    val page = renderer.openPage(pageIndex)
                    try {
                        val ratio = minOf(1f, maxDimension.toFloat() / maxOf(page.width, page.height))
                        val width = (page.width * ratio).toInt().coerceAtLeast(1)
                        val height = (page.height * ratio).toInt().coerceAtLeast(1)
                        android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888).also { image ->
                            image.eraseColor(android.graphics.Color.WHITE)
                            page.render(image, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    } finally { page.close() }
                }
            }
        }
    }
    val image = bitmap
    Box(modifier, contentAlignment = Alignment.Center) {
        if (image == null) {
            Box(Modifier.fillMaxWidth().height(150.dp).background(Color.White), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
        } else {
            Image(image.asImageBitmap(), contentDescription = "PDF page ${pageIndex + 1}", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)))
        }
    }
}

@Composable
private fun ToolCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, disabled: Boolean, action: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(Color(0xFFEAF0FF), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Accent) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, color = Ink); Text(description, color = Color.Gray, fontSize = 12.sp) }
            IconButton(onClick = action, enabled = !disabled) { Icon(Icons.Default.ChevronRight, "Open tool", tint = if (disabled) Color.LightGray else Accent) }
        }
    }
}

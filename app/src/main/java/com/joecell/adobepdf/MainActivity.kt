package com.joecell.adobepdf

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
    var textToAdd by remember { mutableStateOf("Approved") }
    var pageCount by remember { mutableIntStateOf(0) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var signatureText by remember { mutableStateOf("") }
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
        if (imageUri != null && documentUri != null) runOperation("Inserting image") { engine.insertImage(documentUri, imageUri, 0) }
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
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Result", fontWeight = FontWeight.Bold, color = Ink)
                            Text(message, color = Color.Gray, fontSize = 13.sp)
                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (output != null) Button(onClick = { saveFile.launch(output!!.name) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.SaveAlt, null); Spacer(Modifier.width(8.dp)); Text("Save PDF")
                            }
                        }
                    }
                }
                item { Text("Phase 2 adds the full reader and page organizer. More editing and OCR tools are planned next.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 24.dp)) }
            }

            PdfScreen.VIEWER -> {
                val uri = activeUri
                if (uri == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Open a PDF to view it.") }
                else Column(Modifier.fillMaxSize().padding(padding)) {
                    var zoom by remember(uri) { mutableFloatStateOf(1f) }
                    val zoomState = rememberTransformableState { zoomChange, _, _ -> zoom = (zoom * zoomChange).coerceIn(1f, 4f) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$pageCount pages · ${(zoom * 100).toInt()}%", color = Ink, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { zoom = 1f }) { Text("Reset zoom") }
                            TextButton(onClick = { screen = PdfScreen.ORGANIZER }) { Icon(Icons.Default.GridView, null); Spacer(Modifier.width(6.dp)); Text("Organize") }
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = { showTextDialog = true }) { Icon(Icons.Default.TextFields, null); Text("Text") }
                        OutlinedButton(enabled = !busy, onClick = { pickImage.launch("image/*") }) { Icon(Icons.Default.AddPhotoAlternate, null); Text("Image") }
                        OutlinedButton(enabled = !busy, onClick = { runOperation("Adding highlight") { engine.addHighlight(uri, 0) } }) { Icon(Icons.Default.Highlight, null); Text("Highlight") }
                        OutlinedButton(enabled = !busy, onClick = { showSignatureDialog = true }) { Icon(Icons.Default.Draw, null); Text("Signature") }
                    }
                    LazyColumn(Modifier.fillMaxSize().transformable(zoomState).graphicsLayer(scaleX = zoom, scaleY = zoom), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed((0 until pageCount).toList()) { index, _ ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PdfPagePreview(uri, index, 1400, Modifier.fillMaxWidth())
                                Text("Page ${index + 1}", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
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
    if (showSignatureDialog) AlertDialog(
        onDismissRequest = { showSignatureDialog = false },
        title = { Text("Add typed signature") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = signatureText, onValueChange = { signatureText = it }, label = { Text("Name or signature text") }, singleLine = true)
            Text("Adds an italic signature near the bottom of page 1.", color = Color.Gray, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = { showSignatureDialog = false; activeUri?.let { uri -> runOperation("Adding signature") { engine.addTypedSignature(uri, signatureText, 0) } } }) { Text("Add signature") } },
        dismissButton = { TextButton(onClick = { showSignatureDialog = false }) { Text("Cancel") } }
    )
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

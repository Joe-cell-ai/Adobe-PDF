package com.joecell.adobepdf

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var message by remember { mutableStateOf("Your documents stay on this device during processing.") }
    var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<File?>(null) }
    var textToAdd by remember { mutableStateOf("Approved") }
    var pageCount by remember { mutableStateOf<Int?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }

    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected = it }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) selected = listOf(uri)
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = output
        if (uri != null && file != null) runCatching { engine.saveToUri(file, context.contentResolver, uri) }
            .onSuccess { message = "Saved successfully." }.onFailure { message = it.message ?: "Save failed." }
    }

    fun runOperation(label: String, task: suspend () -> File) {
        scope.launch {
            busy = true
            message = "$label…"
            try {
                output = withContext(Dispatchers.IO) { task() }
                message = "$label completed. Save your result to a folder."
            } catch (e: Exception) { message = e.message ?: "$label failed." }
            finally { busy = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Adobe PDF", fontWeight = FontWeight.Bold, color = Ink); Text("Your documents. Your device.", fontSize = 11.sp, color = Color.Gray) } },
                actions = { IconButton(onClick = { pickMany.launch(arrayOf("application/pdf")) }) { Icon(Icons.Default.Add, "Add PDFs") } }
            )
        },
        containerColor = Canvas
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Ink), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("PDF WORKSPACE", color = Color(0xFFB7C8FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Everything you need\nto work with PDFs.", color = Color.White, fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                        Text("View, organize, edit and protect documents with on-device tools.", color = Color(0xFFD7DEEF))
                        Button(onClick = { pickOne.launch(arrayOf("application/pdf")) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)) {
                            Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Open a PDF")
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
                items(selected) { uri ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PictureAsPdf, null, tint = Accent)
                            Spacer(Modifier.width(10.dp))
                            Text(uri.lastPathSegment?.substringAfterLast('/') ?: "Selected PDF", Modifier.weight(1f), maxLines = 1)
                            IconButton(onClick = { selected = selected - uri }) { Icon(Icons.Default.Close, "Remove") }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = {
                        runOperation("Reading PDF details") { pageCount = engine.pageCount(selected.first()); File(context.cacheDir, "details.tmp").also { it.writeText("PDF has $pageCount pages") } }
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Info, null); Spacer(Modifier.width(8.dp)); Text("Read page count") }
                    pageCount?.let { Text("First selected PDF: $it pages", color = Ink, fontWeight = FontWeight.Medium) }
                }
            }
            item { Text("PDF tools", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) }
            item { ToolCard("Merge PDFs", "Combine selected PDFs into one document", Icons.Default.MergeType, selected.size < 2 || busy) {
                runOperation("Merging PDFs") { engine.merge(selected) }
            } }
            item { ToolCard("Extract first page", "Create a new PDF from page one", Icons.Default.ContentCut, selected.size != 1 || busy) {
                runOperation("Extracting page") { engine.splitFirstPage(selected.first()) }
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
            item { Text("Planned next: viewer and thumbnails, OCR, image conversion, page management, annotations, forms, password protection and digital signatures.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }
    if (showTextDialog) AlertDialog(
        onDismissRequest = { showTextDialog = false },
        title = { Text("Add text to first page") },
        text = { OutlinedTextField(value = textToAdd, onValueChange = { textToAdd = it }, label = { Text("Text") }) },
        confirmButton = { TextButton(onClick = { showTextDialog = false; runOperation("Adding text") { engine.addText(selected.first(), textToAdd) } }) { Text("Add text") } },
        dismissButton = { TextButton(onClick = { showTextDialog = false }) { Text("Cancel") } }
    )
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

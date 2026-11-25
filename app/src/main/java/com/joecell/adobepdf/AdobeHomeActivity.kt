package com.joecell.adobepdf

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AdobeBlue = Color(0xFF1473E6)
private val AdobeInk = Color(0xFF171717)
private val AdobeMuted = Color(0xFF6B6B6B)
private val AdobeSurface = Color(0xFFF7F7F7)

class AdobeHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = AdobeBlue, background = Color.White)) {
            AdobeHomeRoot(onOpenWorkspace = { openWorkspace() })
        } }
    }

    private fun openWorkspace() {
        startActivity(Intent(this, MainActivity::class.java))
    }
}

private enum class AdobeTab(val label: String) { HOME("Home"), CREATE("Create"), SPACES("PDF Spaces"), FILES("Files"), TOOLS("Tools") }

@Composable
private fun AdobeHomeRoot(onOpenWorkspace: () -> Unit) {
    var tab by remember { mutableStateOf(AdobeTab.HOME) }
    var query by remember { mutableStateOf("") }
    var showQuickActions by remember { mutableStateOf(true) }
    var templateCategory by remember { mutableStateOf("All") }
    var recentTab by remember { mutableStateOf("Recent") }
    val categories = listOf("All", "Flyer", "Resume", "Instagram post", "Card")
    val recent = remember { mutableStateListOf("Welcome.pdf") }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Selected PDF"
            recent.remove(name)
            recent.add(0, name)
            onOpenWorkspace()
        }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            Row(
                Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Adobe", color = Color(0xFFED1C24), fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { query = if (query.isEmpty()) "Welcome" else "" }) { Icon(Icons.Default.Search, "Search", tint = AdobeInk) }
                IconButton(onClick = { }) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE9E9E9)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, "Profile", tint = Color.DarkGray)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 2.dp) {
                val icons = listOf(Icons.Default.Home, Icons.Default.Edit, Icons.Default.Dashboard, Icons.Default.InsertDriveFile, Icons.Default.GridView)
                AdobeTab.values().forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(icons[index], contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AdobeBlue, selectedTextColor = AdobeBlue, indicatorColor = Color(0xFFEAF2FF))
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == AdobeTab.HOME || tab == AdobeTab.FILES) {
                FloatingActionButton(onClick = { picker.launch(arrayOf("application/pdf")) }, containerColor = AdobeBlue, contentColor = Color.White) {
                    Icon(Icons.Default.Add, "Add PDF")
                }
            }
        }
    ) { inset ->
        when (tab) {
            AdobeTab.HOME -> HomeContent(
                Modifier.fillMaxSize().padding(inset),
                recentTab,
                onRecentTab = { recentTab = it },
                recent = recent,
                onOpen = onOpenWorkspace,
                onPick = { picker.launch(arrayOf("application/pdf")) },
                query = query
            )
            AdobeTab.CREATE -> CreateContent(
                Modifier.fillMaxSize().padding(inset),
                showQuickActions,
                onToggleQuick = { showQuickActions = !showQuickActions },
                category = templateCategory,
                onCategory = { templateCategory = it },
                categories = categories,
                query = query,
                onQuery = { query = it },
                onAction = onOpenWorkspace
            )
            AdobeTab.SPACES -> SimpleWorkspaceContent(Modifier.fillMaxSize().padding(inset), "PDF Spaces", "Keep related PDFs together. Open a document to start a workspace.", Icons.Default.Dashboard, onOpenWorkspace)
            AdobeTab.FILES -> SimpleWorkspaceContent(Modifier.fillMaxSize().padding(inset), "Files", "Browse PDFs stored on your device using the document picker.", Icons.Default.FolderOpen) { picker.launch(arrayOf("application/pdf")) }
            AdobeTab.TOOLS -> ToolsContent(Modifier.fillMaxSize().padding(inset), onOpenWorkspace)
        }
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier,
    recentTab: String,
    onRecentTab: (String) -> Unit,
    recent: List<String>,
    onOpen: () -> Unit,
    onPick: () -> Unit,
    query: String
) {
    LazyColumn(modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { Text("Welcome", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF3F8)).clickable { onOpen() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, null, tint = Color(0xFFC52C85), modifier = Modifier.size(42.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Easily edit PDFs", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Fix typos and add text or images to your PDFs.", color = AdobeMuted, fontSize = 14.sp)
                    Text("Open PDF editor", color = AdobeBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Icon(Icons.Default.ChevronRight, null)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeShortcut("Organize\npages", Icons.Default.Reorder, Color(0xFFEAF6E8), onOpen, Modifier.weight(1f))
                HomeShortcut("Create PDF", Icons.Default.PictureAsPdf, Color(0xFFFCEBED), onOpen, Modifier.weight(1f))
                HomeShortcut("Edit PDF", Icons.Default.Edit, Color(0xFFFBEAF4), onOpen, Modifier.weight(1f))
                HomeShortcut("Create\nFlashcards", Icons.Default.FolderCopy, Color(0xFFFFF1E2), onOpen, Modifier.weight(1f))
                HomeShortcut("Fill & Sign", Icons.Default.Draw, Color(0xFFF2ECFA), onOpen, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Recent", "Starred", "On device").forEach { label ->
                    Column(Modifier.clickable { onRecentTab(label) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, color = if (recentTab == label) Color.Black else AdobeMuted, fontWeight = if (recentTab == label) FontWeight.SemiBold else FontWeight.Normal)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(54.dp).height(3.dp).background(if (recentTab == label) Color.Black else Color.Transparent))
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.MoreVert, "More options")
            }
        }
        if (recentTab == "Recent") {
            items(recent.filter { query.isBlank() || it.contains(query, true) }) { name ->
                Row(Modifier.fillMaxWidth().clickable { onOpen() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFFF1F1F1)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name.removeSuffix(".pdf"), fontWeight = FontWeight.SemiBold)
                        Text("PDF  ·  On device", color = AdobeMuted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.MoreVert, "Document options")
                }
                HorizontalDivider(color = Color(0xFFE8E8E8))
            }
        } else {
            item { Text(if (recentTab == "Starred") "Your starred PDFs will appear here." else "Choose a PDF stored on your device.", color = AdobeMuted) }
            item { OutlinedButton(onClick = onPick) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Browse PDFs") } }
        }
        item { OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open PDF workspace") } }
    }
}

@Composable
private fun HomeShortcut(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.86f).clip(RoundedCornerShape(8.dp)).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color(0xFF8A5CC2), modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp, color = AdobeInk)
    }
}

@Composable
private fun CreateContent(
    modifier: Modifier,
    showQuickActions: Boolean,
    onToggleQuick: () -> Unit,
    category: String,
    onCategory: (String) -> Unit,
    categories: List<String>,
    query: String,
    onQuery: (String) -> Unit,
    onAction: () -> Unit
) {
    val actions = listOf(
        Triple("Create PDF", Icons.Default.PictureAsPdf, Color(0xFFFCEBED)),
        Triple("Create resume", Icons.Default.Description, Color(0xFFEAF5F4)),
        Triple("Create Flashcards", Icons.Default.FolderCopy, Color(0xFFFFF0D8)),
        Triple("Edit Image", Icons.Default.Image, Color(0xFFFFF2E7)),
        Triple("Create flyer", Icons.Default.Article, Color(0xFFF1EAF8)),
        Triple("WhatsApp Greetings", Icons.Default.CardGiftcard, Color(0xFFFBEAF3)),
        Triple("Remove background", Icons.Default.ImageSearch, Color(0xFFEAF6EA)),
        Triple("Export PDF", Icons.Default.PictureAsPdf, Color(0xFFE8F5F4))
    )
    LazyColumn(modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 26.dp)) {
        item {
            Row(Modifier.fillMaxWidth().background(Color(0xFFEAEAEA)).padding(10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("Powered by Adobe Express", fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quick actions", fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleQuick) { Icon(if (showQuickActions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Toggle quick actions") }
            }
            if (showQuickActions) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    actions.chunked(4).forEach { group ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            group.forEach { (label, icon, tint) ->
                                Column(Modifier.weight(1f).clickable { onAction() }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(tint), contentAlignment = Alignment.Center) {
                                        Icon(icon, null, tint = Color(0xFF8B55B8), modifier = Modifier.size(34.dp))
                                    }
                                    Spacer(Modifier.height(7.dp))
                                    Text(label, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp, minLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Text("Templates", fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                categories.forEach { item ->
                    FilterChip(selected = category == item, onClick = { onCategory(item) }, label = { Text(item) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TemplatePreview("Happy Birthday", "Flyer", Color(0xFFFFF4F4), onAction, Modifier.weight(1f))
                TemplatePreview("Celebrate today", "Flyer", Color(0xFFFFE9F0), onAction, Modifier.weight(1f))
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Create an event flyer") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { IconButton(onClick = onAction) { Icon(Icons.Default.Mic, "Voice search") } },
                shape = RoundedCornerShape(28.dp),
                singleLine = true
            )
        }
    }
}

@Composable
private fun TemplatePreview(title: String, type: String, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(12.dp)).background(tint).padding(12.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Favorite, null, tint = Color(0xFFC47B87), modifier = Modifier.size(28.dp))
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C6B3A), textAlign = TextAlign.Center)
                Box(Modifier.fillMaxWidth(0.7f).height(90.dp).clip(RoundedCornerShape(60.dp)).background(Color(0xFFD7D7D7)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
                Text("A special day to celebrate", fontSize = 11.sp, color = AdobeInk, textAlign = TextAlign.Center)
            }
        }
        Text(type, color = AdobeMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun SimpleWorkspaceContent(modifier: Modifier, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = AdobeSurface)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = AdobeBlue, modifier = Modifier.size(46.dp))
                Text(subtitle, color = AdobeMuted, textAlign = TextAlign.Center)
                Button(onClick = onClick) { Text("Open PDF workspace") }
            }
        }
    }
}

@Composable
private fun ToolsContent(modifier: Modifier, onClick: () -> Unit) {
    val tools = listOf("Organize pages", "Create PDF", "Edit PDF", "Fill & Sign", "Merge PDFs", "Compress PDF", "Crop PDF", "Remove blank pages", "Unlock PDF", "Remove metadata", "PDF to images", "Images to PDF")
    LazyColumn(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("All tools", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        items(tools) { tool ->
            Card(Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = AdobeSurface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PictureAsPdf, null, tint = AdobeBlue)
                    Spacer(Modifier.width(14.dp))
                    Text(tool, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
    }
}

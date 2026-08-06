package com.yourname.pdftoolkit.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.pdftoolkit.data.SafUriManager
import com.yourname.pdftoolkit.ui.navigation.Screen
import kotlinx.coroutines.launch

private data class ResolvedTool(
    val source: ToolItem,
    val title: String,
    val description: String,
)

private val premiumSections = listOf(
    ToolSection.QUICK_ACTIONS,
    ToolSection.ORGANIZE,
    ToolSection.CONVERT,
    ToolSection.SECURITY,
    ToolSection.IMAGE_TOOLS,
    ToolSection.VIEW_EXPORT,
)

private fun sectionLabel(section: ToolSection?): String = when (section) {
    null -> "All tools"
    ToolSection.QUICK_ACTIONS -> "Quick actions"
    ToolSection.ORGANIZE -> "Organize PDF"
    ToolSection.CONVERT -> "Convert"
    ToolSection.SECURITY -> "Security & markup"
    ToolSection.IMAGE_TOOLS -> "Images & OCR"
    ToolSection.VIEW_EXPORT -> "View & export"
}

private fun sectionIcon(section: ToolSection?): ImageVector = when (section) {
    null -> Icons.Default.AutoAwesome
    ToolSection.QUICK_ACTIONS -> Icons.Default.AutoAwesome
    ToolSection.ORGANIZE -> Icons.Default.Description
    ToolSection.CONVERT -> Icons.Default.AutoAwesome
    ToolSection.SECURITY -> Icons.Default.Lock
    ToolSection.IMAGE_TOOLS -> Icons.Default.Description
    ToolSection.VIEW_EXPORT -> Icons.Default.FolderOpen
}

@Composable
private fun sectionAccent(section: ToolSection): Color = when (section) {
    ToolSection.QUICK_ACTIONS -> MaterialTheme.colorScheme.primary
    ToolSection.ORGANIZE -> MaterialTheme.colorScheme.secondary
    ToolSection.CONVERT -> MaterialTheme.colorScheme.tertiary
    ToolSection.SECURITY -> MaterialTheme.colorScheme.primary
    ToolSection.IMAGE_TOOLS -> MaterialTheme.colorScheme.secondary
    ToolSection.VIEW_EXPORT -> MaterialTheme.colorScheme.primary
}

/**
 * Adaptive Eyad PDF home workspace.
 *
 * The screen uses a stable branded palette, larger touch targets and cards that
 * scale from phones to tablets. Expensive per-card entrance animations are
 * intentionally avoided so long tool lists remain smooth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumToolsScreen(
    onNavigateToScreen: (Screen) -> Unit,
    onNavigateToRoute: ((String) -> Unit)? = null,
    onOpenPdfViewer: (Uri, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf<ToolSection?>(null) }

    val sourceTools = getAllTools()
    val resolvedTools = buildList {
        for (tool in sourceTools) {
            add(
                ResolvedTool(
                    source = tool,
                    title = tool.getTitle(),
                    description = tool.getDescription(),
                )
            )
        }
    }

    val visibleTools = resolvedTools.filter { tool ->
        val sectionMatches = selectedSection == null || tool.source.section == selectedSection
        val queryMatches = query.isBlank() ||
            tool.title.contains(query, ignoreCase = true) ||
            tool.description.contains(query, ignoreCase = true) ||
            sectionLabel(tool.source.section).contains(query, ignoreCase = true)
        sectionMatches && queryMatches
    }

    val openPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            val persisted = SafUriManager.addRecentFile(context, uri, flags)
            var displayName = persisted?.name?.substringBeforeLast('.') ?: "PDF Document"
            if (persisted == null) {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            displayName = cursor.getString(index)?.substringBeforeLast('.') ?: displayName
                        }
                    }
                }
            }
            onOpenPdfViewer(uri, displayName)
        }
    }

    fun openTool(tool: ToolItem) {
        if (tool.id == "view_pdf") {
            openPdf.launch(arrayOf("application/pdf"))
            return
        }

        val imageToolIds = setOf("image_compress", "image_resize", "image_convert", "image_metadata")
        if (tool.id in imageToolIds && onNavigateToRoute != null) {
            onNavigateToRoute(Screen.getRouteForToolId(tool.id))
        } else {
            onNavigateToScreen(tool.screen)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            ),
    ) {
        val expanded = maxWidth >= 840.dp

        Row(Modifier.fillMaxSize()) {
            if (expanded) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Surface(
                            modifier = Modifier
                                .padding(top = 18.dp, bottom = 12.dp)
                                .size(62.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Eyad PDF",
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    },
                ) {
                    NavigationRailItem(
                        selected = selectedSection == null,
                        onClick = { selectedSection = null },
                        icon = { Icon(sectionIcon(null), contentDescription = null) },
                        label = { Text("All") },
                    )
                    premiumSections.forEach { section ->
                        NavigationRailItem(
                            selected = selectedSection == section,
                            onClick = { selectedSection = section },
                            icon = { Icon(sectionIcon(section), contentDescription = null) },
                            label = { Text(sectionLabel(section), maxLines = 1) },
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (expanded) 236.dp else 172.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (expanded) 28.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (expanded) 16.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (expanded) 16.dp else 12.dp),
                contentPadding = PaddingValues(bottom = 34.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "workspace_hero") {
                    WorkspaceHero(
                        toolCount = resolvedTools.size,
                        expanded = expanded,
                        onOpenPdf = { openPdf.launch(arrayOf("application/pdf")) },
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }, key = "tool_search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("Search all PDF tools") },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                    )
                }

                if (!expanded) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "tool_filters") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedSection == null,
                                    onClick = { selectedSection = null },
                                    label = { Text("All tools") },
                                )
                            }
                            items(premiumSections, key = { it.name }) { section ->
                                FilterChip(
                                    selected = selectedSection == section,
                                    onClick = { selectedSection = section },
                                    label = { Text(sectionLabel(section)) },
                                )
                            }
                        }
                    }
                }

                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "section_heading_${selectedSection?.name ?: "all"}",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = sectionLabel(selectedSection),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Choose a tool and work completely offline",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = "${visibleTools.size} tools",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                            )
                        }
                    }
                }

                items(visibleTools, key = { it.source.id }) { tool ->
                    PremiumToolCard(
                        tool = tool,
                        expanded = expanded,
                        onClick = { openTool(tool.source) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceHero(
    toolCount: Int,
    expanded: Boolean,
    onOpenPdf: () -> Unit,
) {
    val heroShape = RoundedCornerShape(if (expanded) 30.dp else 26.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (expanded) 20.dp else 14.dp)
            .clip(heroShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                    )
                )
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (expanded) 26.dp else 19.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(if (expanded) 82.dp else 66.dp),
                shape = RoundedCornerShape(if (expanded) 25.dp else 21.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(if (expanded) 42.dp else 34.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.width(if (expanded) 22.dp else 16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = "Eyad PDF",
                    style = if (expanded) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Your private document workspace",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(7.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = "Offline • private • $toolCount tools",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (expanded) {
                Button(
                    onClick = onOpenPdf,
                    modifier = Modifier.height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text("Open PDF")
                }
            }
        }
    }

    if (!expanded) {
        Spacer(Modifier.height(11.dp))
        Button(
            onClick = onOpenPdf,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(9.dp))
            Text("Open PDF")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumToolCard(
    tool: ResolvedTool,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val accent = sectionAccent(tool.source.section)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (expanded) 202.dp else 184.dp),
        shape = RoundedCornerShape(if (expanded) 24.dp else 22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (expanded) 18.dp else 15.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    modifier = Modifier.size(if (expanded) 58.dp else 52.dp),
                    shape = RoundedCornerShape(if (expanded) 18.dp else 16.dp),
                    color = accent.copy(alpha = 0.14f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tool.source.icon,
                            contentDescription = null,
                            modifier = Modifier.size(if (expanded) 30.dp else 27.dp),
                            tint = accent,
                        )
                    }
                }

                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

package com.yourname.pdftoolkit.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
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

/**
 * Premium adaptive Eyad PDF tool workspace.
 *
 * It deliberately avoids per-card entrance animations and fixed three-column rows:
 * the grid adapts to phones, foldables and tablets without triggering unnecessary
 * recomposition or layout churn while the user scrolls.
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp

        Row(Modifier.fillMaxSize()) {
            if (expanded) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    header = {
                        Surface(
                            modifier = Modifier.padding(vertical = 14.dp).size(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Eyad PDF",
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (expanded) 24.dp else 16.dp),
            ) {
                WorkspaceHero(
                    toolCount = resolvedTools.size,
                    expanded = expanded,
                    onOpenPdf = { openPdf.launch(arrayOf("application/pdf")) },
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search PDF tools") },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                )

                if (!expanded) {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedSection == null,
                                onClick = { selectedSection = null },
                                label = { Text("All") },
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

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = sectionLabel(selectedSection),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${visibleTools.size} tools",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (expanded) 220.dp else 164.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    items(visibleTools, key = { it.source.id }) { tool ->
                        PremiumToolCard(
                            tool = tool,
                            onClick = { openTool(tool.source) },
                        )
                    }
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
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(if (expanded) 20.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(if (expanded) 68.dp else 56.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(if (expanded) 34.dp else 28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Eyad PDF",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Private offline document workspace",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "No account • no upload • no Internet permission",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (expanded) {
                Column(horizontalAlignment = Alignment.End) {
                    AssistChip(
                        onClick = {},
                        label = { Text("$toolCount tools") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onOpenPdf) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open PDF")
                    }
                }
            }
        }
    }

    if (!expanded) {
        Spacer(Modifier.height(10.dp))
        Button(onClick = onOpenPdf, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open PDF")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumToolCard(
    tool: ResolvedTool,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(164.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.source.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Column {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

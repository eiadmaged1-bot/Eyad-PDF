package com.yourname.pdftoolkit.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yourname.pdftoolkit.domain.operations.PdfOrganizer
import com.yourname.pdftoolkit.ui.components.ToolTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class TouchReorderPage(
    val originalPageNumber: Int,
    val thumbnail: Bitmap?,
)

private fun <T> moveSelectedBlock(
    items: List<T>,
    selectedIndexes: Set<Int>,
    insertionIndex: Int,
): List<T> {
    if (items.isEmpty() || selectedIndexes.isEmpty()) return items

    val validIndexes = selectedIndexes.filter { it in items.indices }.sorted()
    if (validIndexes.isEmpty()) return items

    val block = validIndexes.map(items::get)
    val remaining = items.filterIndexed { index, _ -> index !in selectedIndexes }
    val removedBeforeTarget = validIndexes.count { it < insertionIndex }
    val adjustedTarget = (insertionIndex - removedBeforeTarget).coerceIn(0, remaining.size)

    return buildList(items.size) {
        addAll(remaining.subList(0, adjustedTarget))
        addAll(block)
        addAll(remaining.subList(adjustedTarget, remaining.size))
    }
}

/**
 * Touch-first page reordering screen.
 *
 * The page list is not mutated during pointer movement. A floating overlay follows
 * the exact pointer delta and the selected block is committed once, on release.
 * This prevents the double-motion bug where the thumbnail used to run ahead of the
 * user's finger while the grid was also being reordered underneath it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchReorderScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val organizer = remember { PdfOrganizer() }
    val pages = remember { mutableStateListOf<TouchReorderPage>() }
    val gridState = rememberLazyGridState()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("document.pdf") }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    val itemPositions = remember { mutableStateMapOf<Int, Offset>() }
    val itemSizes = remember { mutableStateMapOf<Int, IntSize>() }

    var draggedPageNumber by remember { mutableStateOf<Int?>(null) }
    var dragSelectionIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var pointerInViewport by remember { mutableStateOf(Offset.Zero) }
    var overlayStart by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var dropInsertion by remember { mutableStateOf<Int?>(null) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    fun resetDrag() {
        draggedPageNumber = null
        dragSelectionIds = emptySet()
        dragDelta = Offset.Zero
        pointerInViewport = Offset.Zero
        overlayStart = Offset.Zero
        overlaySize = IntSize.Zero
        dropInsertion = null
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    suspend fun loadPdf(uri: Uri) {
        loading = true
        resetDrag()
        selectedIds = emptySet()
        pages.forEach { page ->
            page.thumbnail?.takeUnless(Bitmap::isRecycled)?.recycle()
        }
        pages.clear()

        runCatching {
            val pageCount = organizer.getPageCount(context, uri)
            pages.addAll((1..pageCount).map { page -> TouchReorderPage(page, null) })

            withContext(Dispatchers.IO) {
                organizer.getPageThumbnails(
                    context = context,
                    uri = uri,
                    width = 300,
                    height = 420,
                ) { pageNumber, bitmap ->
                    scope.launch(Dispatchers.Main) {
                        val index = pages.indexOfFirst { it.originalPageNumber == pageNumber }
                        if (index >= 0) {
                            pages[index] = pages[index].copy(thumbnail = bitmap)
                        }
                    }
                }
            }
        }.onFailure { error ->
            snackbar.showSnackbar(error.message ?: "Could not open this PDF")
        }

        loading = false
    }

    DisposableEffect(Unit) {
        onDispose {
            autoScrollJob?.cancel()
            pages.forEach { page ->
                page.thumbnail?.takeUnless(Bitmap::isRecycled)?.recycle()
            }
        }
    }

    LaunchedEffect(sourceUri) {
        sourceUri?.let { loadPdf(it) }
    }

    val openPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        sourceUri = uri
        sourceName = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
    }

    val savePdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { outputUri ->
        val inputUri = sourceUri ?: return@rememberLauncherForActivityResult
        outputUri ?: return@rememberLauncherForActivityResult

        scope.launch {
            saving = true
            val stream = context.contentResolver.openOutputStream(outputUri)
            if (stream == null) {
                snackbar.showSnackbar("Could not create the output PDF")
                saving = false
                return@launch
            }

            stream.use { output ->
                organizer.reorderPages(
                    context = context,
                    inputUri = inputUri,
                    outputStream = output,
                    newOrder = pages.map { it.originalPageNumber },
                    onProgress = {},
                ).onSuccess {
                    snackbar.showSnackbar("Reordered PDF saved")
                }.onFailure { error ->
                    snackbar.showSnackbar(error.message ?: "Export failed")
                }
            }
            saving = false
        }
    }

    Scaffold(
        topBar = {
            ToolTopBar(
                title = "Organize Pages",
                onNavigateBack = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { rootPosition = it.positionInRoot() },
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { openPdf.launch(arrayOf("application/pdf")) },
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (sourceUri == null) "Select PDF" else "Replace PDF")
                        }

                        Button(
                            onClick = {
                                val base = sourceName.substringBeforeLast('.', sourceName)
                                savePdf.launch("${base}_organized.pdf")
                            },
                            enabled = pages.isNotEmpty() && !saving,
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (saving) "Saving…" else "Save PDF")
                        }

                        if (pages.isNotEmpty()) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${pages.size} pages • ${selectedIds.size} selected",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Text(
                    text = "Tap pages to select them. Long-press a selected page, then drag. The floating page stays anchored to your finger and the order changes only when you release.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    pages.isEmpty() -> Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(42.dp),
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Select one PDF to organize its pages",
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                    ) {
                        itemsIndexed(
                            items = pages,
                            key = { _, page -> page.originalPageNumber },
                        ) { index, page ->
                            val selected = page.originalPageNumber in selectedIds
                            val draggedMember = draggedPageNumber != null &&
                                page.originalPageNumber in dragSelectionIds
                            val insertionHighlight = dropInsertion == index ||
                                (dropInsertion == pages.size && index == pages.lastIndex)

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ),
                                border = if (insertionHighlight) {
                                    BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .alpha(if (draggedMember) 0.25f else 1f)
                                    .onGloballyPositioned { coordinates ->
                                        itemPositions[page.originalPageNumber] = coordinates.positionInRoot()
                                        itemSizes[page.originalPageNumber] = coordinates.size
                                    }
                                    .pointerInput(page.originalPageNumber, selectedIds, pages.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { pressOffset ->
                                                val selection = if (selected) {
                                                    selectedIds
                                                } else {
                                                    setOf(page.originalPageNumber)
                                                }
                                                selectedIds = selection
                                                dragSelectionIds = selection
                                                draggedPageNumber = page.originalPageNumber
                                                dragDelta = Offset.Zero
                                                overlayStart =
                                                    (itemPositions[page.originalPageNumber] ?: Offset.Zero) - rootPosition
                                                overlaySize = itemSizes[page.originalPageNumber] ?: IntSize.Zero

                                                val currentIndex = pages.indexOfFirst {
                                                    it.originalPageNumber == page.originalPageNumber
                                                }
                                                val visibleInfo = gridState.layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.index == currentIndex }
                                                pointerInViewport = if (visibleInfo != null) {
                                                    Offset(
                                                        visibleInfo.offset.x + pressOffset.x,
                                                        visibleInfo.offset.y + pressOffset.y,
                                                    )
                                                } else {
                                                    pressOffset
                                                }
                                                dropInsertion = currentIndex
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragCancel = { resetDrag() },
                                            onDragEnd = {
                                                val insertion = dropInsertion
                                                if (insertion != null && dragSelectionIds.isNotEmpty()) {
                                                    val selectedIndexes = pages.mapIndexedNotNull { pageIndex, item ->
                                                        pageIndex.takeIf {
                                                            item.originalPageNumber in dragSelectionIds
                                                        }
                                                    }.toSet()
                                                    val reordered = moveSelectedBlock(
                                                        pages.toList(),
                                                        selectedIndexes,
                                                        insertion,
                                                    )
                                                    pages.clear()
                                                    pages.addAll(reordered)
                                                }
                                                resetDrag()
                                            },
                                        ) { change, amount ->
                                            change.consume()
                                            dragDelta += amount
                                            pointerInViewport += amount

                                            val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                                pointerInViewport.x >= info.offset.x &&
                                                    pointerInViewport.x <= info.offset.x + info.size.width &&
                                                    pointerInViewport.y >= info.offset.y &&
                                                    pointerInViewport.y <= info.offset.y + info.size.height
                                            }
                                            if (target != null) {
                                                val after = pointerInViewport.y >
                                                    target.offset.y + target.size.height / 2f
                                                dropInsertion = (target.index + if (after) 1 else 0)
                                                    .coerceIn(0, pages.size)
                                            }

                                            val thresholdPx = with(density) { 88.dp.toPx() }
                                            val scrollAmount = when {
                                                pointerInViewport.y <
                                                    gridState.layoutInfo.viewportStartOffset + thresholdPx -> -42f
                                                pointerInViewport.y >
                                                    gridState.layoutInfo.viewportEndOffset - thresholdPx -> 42f
                                                else -> 0f
                                            }
                                            if (
                                                scrollAmount != 0f &&
                                                (autoScrollJob == null || autoScrollJob?.isCompleted == true)
                                            ) {
                                                autoScrollJob = scope.launch {
                                                    gridState.scrollBy(scrollAmount)
                                                }
                                            }
                                        }
                                    }
                                    .clickable {
                                        selectedIds = if (selected) {
                                            selectedIds - page.originalPageNumber
                                        } else {
                                            selectedIds + page.originalPageNumber
                                        }
                                    },
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    val thumbnail = page.thumbnail
                                    if (thumbnail != null && !thumbnail.isRecycled) {
                                        Image(
                                            bitmap = thumbnail.asImageBitmap(),
                                            contentDescription = "Page ${index + 1}",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(0.72f)
                                                .clip(RoundedCornerShape(12.dp)),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(0.72f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(7.dp))
                                    Text("Page ${index + 1}", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            val draggedPage = draggedPageNumber?.let { id ->
                pages.firstOrNull { it.originalPageNumber == id }
            }
            val draggedThumbnail = draggedPage?.thumbnail
            if (
                draggedPage != null &&
                draggedThumbnail != null &&
                !draggedThumbnail.isRecycled &&
                overlaySize != IntSize.Zero
            ) {
                val widthDp = with(density) { overlaySize.width.toDp() }
                val heightDp = with(density) { overlaySize.height.toDp() }

                Card(
                    modifier = Modifier
                        .zIndex(100f)
                        .offset {
                            IntOffset(
                                overlayStart.x.roundToInt(),
                                overlayStart.y.roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            translationX = dragDelta.x
                            translationY = dragDelta.y
                            scaleX = 1.02f
                            scaleY = 1.02f
                        }
                        .size(widthDp, heightDp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Image(
                            bitmap = draggedThumbnail.asImageBitmap(),
                            contentDescription = "Dragging page",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        if (dragSelectionIds.size > 1) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primary,
                            ) {
                                Text(
                                    text = "${dragSelectionIds.size} pages",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

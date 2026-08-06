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
import androidx.compose.material.icons.filled.Check
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
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
 * Touch-first page organizer.
 *
 * The page order changes only after release. Edge auto-scroll runs on a gentle,
 * distance-based frame loop so long drags remain controlled instead of jumping
 * when the pointer reaches the final rows of the document.
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
    var autoScrollVelocity by remember { mutableStateOf(0f) }

    fun resetDrag() {
        draggedPageNumber = null
        dragSelectionIds = emptySet()
        dragDelta = Offset.Zero
        pointerInViewport = Offset.Zero
        overlayStart = Offset.Zero
        overlaySize = IntSize.Zero
        dropInsertion = null
        autoScrollVelocity = 0f
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    fun resolveDropInsertion() {
        val layoutInfo = gridState.layoutInfo
        val target = layoutInfo.visibleItemsInfo.firstOrNull { info ->
            pointerInViewport.x >= info.offset.x &&
                pointerInViewport.x <= info.offset.x + info.size.width &&
                pointerInViewport.y >= info.offset.y &&
                pointerInViewport.y <= info.offset.y + info.size.height
        }

        if (target != null) {
            val after = pointerInViewport.y > target.offset.y + target.size.height / 2f
            dropInsertion = (target.index + if (after) 1 else 0).coerceIn(0, pages.size)
            return
        }

        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return
        dropInsertion = when {
            pointerInViewport.y <= layoutInfo.viewportStartOffset -> visibleItems.first().index
            pointerInViewport.y >= layoutInfo.viewportEndOffset -> visibleItems.last().index + 1
            else -> dropInsertion
        }?.coerceIn(0, pages.size)
    }

    fun ensureAutoScroll() {
        if (abs(autoScrollVelocity) < 0.1f || draggedPageNumber == null) {
            autoScrollJob?.cancel()
            autoScrollJob = null
            return
        }
        if (autoScrollJob?.isActive == true) return

        autoScrollJob = scope.launch {
            while (isActive && draggedPageNumber != null && abs(autoScrollVelocity) >= 0.1f) {
                val consumed = gridState.scrollBy(autoScrollVelocity)
                resolveDropInsertion()
                if (abs(consumed) < 0.1f) {
                    autoScrollVelocity = 0f
                    break
                }
                delay(16L)
            }
            autoScrollJob = null
        }
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
                    width = 336,
                    height = 470,
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { rootPosition = it.positionInRoot() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { openPdf.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(17.dp),
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
                                modifier = Modifier.weight(1f).height(52.dp),
                                enabled = pages.isNotEmpty() && !saving,
                                shape = RoundedCornerShape(17.dp),
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (saving) "Saving…" else "Save PDF")
                            }
                        }

                        if (pages.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = sourceName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = "${pages.size} pages • ${selectedIds.size} selected",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
                ) {
                    Text(
                        text = "Tap to select. Long-press, then drag. Near the top or bottom edge, the document now scrolls gently while the floating page stays anchored to your finger.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    )
                }

                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    pages.isEmpty() -> Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    modifier = Modifier.size(72.dp),
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            modifier = Modifier.size(38.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "Select one PDF to organize its pages",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(168.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 34.dp),
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
                                        MaterialTheme.colorScheme.surface
                                    },
                                ),
                                border = if (insertionHighlight) {
                                    BorderStroke(3.dp, MaterialTheme.colorScheme.secondary)
                                } else if (selected) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                },
                                shape = RoundedCornerShape(22.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .alpha(if (draggedMember) 0.24f else 1f)
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
                                                autoScrollVelocity = 0f
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
                                            resolveDropInsertion()

                                            val layoutInfo = gridState.layoutInfo
                                            val thresholdPx = with(density) { 64.dp.toPx() }
                                            val viewportStart = layoutInfo.viewportStartOffset.toFloat()
                                            val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
                                            val y = pointerInViewport.y
                                            val maxVelocity = 9f
                                            autoScrollVelocity = when {
                                                y < viewportStart + thresholdPx -> {
                                                    val depth = ((viewportStart + thresholdPx - y) / thresholdPx)
                                                        .coerceIn(0f, 1f)
                                                    -(2.5f + (maxVelocity - 2.5f) * depth)
                                                }

                                                y > viewportEnd - thresholdPx -> {
                                                    val depth = ((y - (viewportEnd - thresholdPx)) / thresholdPx)
                                                        .coerceIn(0f, 1f)
                                                    2.5f + (maxVelocity - 2.5f) * depth
                                                }

                                                else -> 0f
                                            }
                                            ensureAutoScroll()
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
                                    modifier = Modifier.padding(10.dp),
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
                                                .clip(RoundedCornerShape(14.dp)),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(0.72f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(26.dp))
                                        }
                                    }

                                    Spacer(Modifier.height(9.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "Page ${index + 1}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        if (selected) {
                                            Surface(
                                                modifier = Modifier.size(28.dp),
                                                shape = RoundedCornerShape(999.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        modifier = Modifier.size(17.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                }
                                            }
                                        }
                                    }
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
                            scaleX = 1.01f
                            scaleY = 1.01f
                        }
                        .size(widthDp, heightDp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.secondary),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Image(
                            bitmap = draggedThumbnail.asImageBitmap(),
                            contentDescription = "Dragging page",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                        if (dragSelectionIds.size > 1) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(9.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primary,
                            ) {
                                Text(
                                    text = "${dragSelectionIds.size} pages",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

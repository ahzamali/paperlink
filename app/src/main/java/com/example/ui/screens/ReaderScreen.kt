package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.components.CompressPdfDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ReadingMode
import com.example.data.model.ToolType
import com.example.ui.components.AnnotationCanvas
import com.example.ui.components.ColorPickerRow
import com.example.ui.components.ExportBottomSheet
import com.example.ui.components.ReadingModeFilters
import com.example.ui.theme.SepiaBackground
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    BackHandler {
        onBack()
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    var showExportSheet by remember { mutableStateOf(false) }
    var showThumbnailsSheet by remember { mutableStateOf(false) }
    var showSearchResultsSheet by remember { mutableStateOf(false) }
    var showJumpPageDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showSaveToOriginalConfirm by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    val exportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val thumbnailsSheetState = rememberModalBottomSheetState()
    val searchResultsSheetState = rememberModalBottomSheetState()

    val verticalLazyListState = rememberLazyListState()

    // Track scroll position to update current visible page indicator
    LaunchedEffect(verticalLazyListState) {
        snapshotFlow { verticalLazyListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { pageIdx ->
                viewModel.updateCurrentVisiblePage(pageIdx)
            }
    }

    val pageBackground = when (uiState.readingMode) {
        ReadingMode.NORMAL -> Color(0xFFE2E8F0)
        ReadingMode.NIGHT -> Color(0xFF0F172A)
        ReadingMode.SEPIA -> SepiaBackground
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("reader_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSearchActive) {
                // In-PDF Search Top Bar
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = uiState.pdfSearchQuery,
                            onValueChange = { viewModel.setPdfSearchQuery(it) },
                            placeholder = { Text("Search text & notes in PDF...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("in_pdf_search_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (uiState.pdfSearchQuery.isNotBlank()) {
                                    IconButton(onClick = { viewModel.setPdfSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.closeSearch() },
                            modifier = Modifier.testTag("close_in_pdf_search_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    },
                    actions = {
                        if (uiState.isSearchingInPdf) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else if (uiState.pdfSearchResults.isNotEmpty()) {
                            Text(
                                text = "${uiState.currentSearchMatchIndex + 1}/${uiState.pdfSearchResults.size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = {
                                    val targetPage = viewModel.prevSearchMatch()
                                    if (targetPage != null) {
                                        coroutineScope.launch {
                                            verticalLazyListState.animateScrollToItem(targetPage)
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("prev_search_match_button")
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match")
                            }
                            IconButton(
                                onClick = {
                                    val targetPage = viewModel.nextSearchMatch()
                                    if (targetPage != null) {
                                        coroutineScope.launch {
                                            verticalLazyListState.animateScrollToItem(targetPage)
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("next_search_match_button")
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
                            }
                            IconButton(
                                onClick = { showSearchResultsSheet = true },
                                modifier = Modifier.testTag("toggle_search_results_sheet_button")
                            ) {
                                Icon(Icons.Default.FormatListBulleted, contentDescription = "View all matches list")
                            }
                        } else if (uiState.pdfSearchQuery.isNotBlank()) {
                            Text(
                                text = "0 matches",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = uiState.document?.title ?: "PDF Reader",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Page ${uiState.currentPageIndex + 1} of ${uiState.totalPages}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { showJumpPageDialog = true }
                                )
                                if (uiState.hasUnsavedChanges) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF16A34A))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Unsaved Edits",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF16A34A),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("reader_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Search in PDF
                        IconButton(
                            onClick = { viewModel.openSearch() },
                            modifier = Modifier.testTag("open_in_pdf_search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search in PDF")
                        }

                        // Save Button (Non-destructive: saves to database library)
                        Button(
                            onClick = {
                                if (uiState.hasUnsavedChanges) {
                                    viewModel.saveEdits(burnToPdfFile = false)
                                } else {
                                    showSaveToOriginalConfirm = true
                                }
                            },
                            modifier = Modifier
                                .testTag("save_pdf_button")
                                .padding(end = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.hasUnsavedChanges) Color(0xFF16A34A) else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (uiState.hasUnsavedChanges) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save PDF",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (uiState.hasUnsavedChanges) "Save" else "Saved", fontWeight = FontWeight.Bold)
                        }

                        // 3-Dot Overflow Menu for secondary actions (Save to Original, Print, Dark Mode, Compress, Share)
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.testTag("reader_overflow_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options"
                                )
                            }

                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                // Save into original PDF file (with confirmation dialog)
                                DropdownMenuItem(
                                    text = { Text("Save into Original PDF File...") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Save,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showSaveToOriginalConfirm = true
                                    },
                                    modifier = Modifier.testTag("menu_item_save_to_original")
                                )

                                HorizontalDivider()

                                // Compress PDF
                                DropdownMenuItem(
                                    text = { Text("Compress PDF") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.setCompressDialogVisible(true)
                                    },
                                    modifier = Modifier.testTag("menu_item_compress_pdf")
                                )

                                // Share & Export
                                DropdownMenuItem(
                                    text = { Text("Share & Export") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showExportSheet = true
                                    },
                                    modifier = Modifier.testTag("menu_item_share_export")
                                )

                                // Print Document
                                DropdownMenuItem(
                                    text = { Text("Print") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Print,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.printCurrentDocument(context)
                                    },
                                    modifier = Modifier.testTag("menu_item_print")
                                )

                                HorizontalDivider()

                                // Reading Mode (Dark Mode / Normal / Sepia)
                                val readingModeLabel = when (uiState.readingMode) {
                                    ReadingMode.NORMAL -> "Dark Mode"
                                    ReadingMode.NIGHT -> "Sepia Mode"
                                    ReadingMode.SEPIA -> "Normal (Day) Mode"
                                }
                                val readingModeIcon = when (uiState.readingMode) {
                                    ReadingMode.NORMAL -> Icons.Default.DarkMode
                                    ReadingMode.NIGHT -> Icons.Default.LightMode
                                    ReadingMode.SEPIA -> Icons.Default.WbSunny
                                }
                                DropdownMenuItem(
                                    text = { Text(readingModeLabel) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = readingModeIcon,
                                            contentDescription = null,
                                            tint = when (uiState.readingMode) {
                                                ReadingMode.NIGHT -> Color(0xFF60A5FA)
                                                ReadingMode.SEPIA -> Color(0xFFF59E0B)
                                                ReadingMode.NORMAL -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.cycleReadingMode()
                                    },
                                    modifier = Modifier.testTag("menu_item_reading_mode")
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            // Automatically resizes and gives space to the bottom navigation buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Quick Pen Ink Color Selector Strip (shown when Pen tool is active)
                AnimatedVisibility(
                    visible = uiState.activeTool == ToolType.PEN,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Surface(
                        tonalElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Brush,
                                    contentDescription = null,
                                    tint = uiState.penColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pen Ink Color:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val penSwatches = listOf(
                                    Color(0xFF0F172A) to "Black",
                                    Color(0xFF2563EB) to "Blue",
                                    Color(0xFFDC2626) to "Red",
                                    Color(0xFF16A34A) to "Green",
                                    Color(0xFF9333EA) to "Purple",
                                    Color(0xFFEA580C) to "Orange"
                                )
                                penSwatches.forEach { (swatchColor, label) ->
                                    val isSelected = uiState.penColor == swatchColor
                                    Box(
                                        modifier = Modifier
                                            .size(if (isSelected) 28.dp else 24.dp)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.25f),
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.setPenColor(swatchColor) }
                                            .testTag("pen_color_${label.lowercase()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = label,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { showColorPicker = !showColorPicker },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("pen_color_expand_palette_button")
                                ) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = "More Colors & Thickness",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Color & Stroke Width Picker Drawer (shown for Pen, Highlighter, or Text)
                AnimatedVisibility(
                    visible = showColorPicker && (uiState.activeTool == ToolType.PEN || uiState.activeTool == ToolType.HIGHLIGHTER || uiState.activeTool == ToolType.TEXT),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    val isHighlighter = uiState.activeTool == ToolType.HIGHLIGHTER
                    ColorPickerRow(
                        selectedColor = if (isHighlighter) uiState.highlighterColor else uiState.penColor,
                        onColorSelected = { color ->
                            if (isHighlighter) {
                                viewModel.setHighlighterColor(color)
                            } else {
                                viewModel.setPenColor(color)
                            }
                        },
                        strokeWidth = uiState.strokeWidth,
                        onStrokeWidthChange = { viewModel.setStrokeWidth(it) },
                        isHighlighter = isHighlighter,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Adobe-Style Quick Page Indicator Bar (arrow navigation removed as vertical scroll is active)
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clickable { showJumpPageDialog = true }
                                .testTag("adobe_page_indicator_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Page ${uiState.currentPageIndex + 1} of ${uiState.totalPages}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable { showThumbnailsSheet = true }
                                .testTag("adobe_thumbnails_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.GridView,
                                    contentDescription = "Thumbnails",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pages Grid",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Annotation Tools Bar (with active ink color display)
                Surface(
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Read / Scroll tool
                        ToolButton(
                            icon = Icons.Default.PanTool,
                            label = "Read",
                            isSelected = uiState.activeTool == ToolType.READ,
                            testTag = "tool_read",
                            onClick = {
                                viewModel.selectTool(ToolType.READ)
                                showColorPicker = false
                            }
                        )

                        // Pen tool with custom ink color indicator
                        ToolButtonWithColorDot(
                            icon = Icons.Default.Brush,
                            label = "Pen",
                            colorDot = uiState.penColor,
                            isSelected = uiState.activeTool == ToolType.PEN,
                            testTag = "tool_pen",
                            onClick = {
                                if (uiState.activeTool == ToolType.PEN) {
                                    showColorPicker = !showColorPicker
                                } else {
                                    viewModel.selectTool(ToolType.PEN)
                                    showColorPicker = true
                                }
                            }
                        )

                        // Highlighter tool with custom highlight color indicator
                        ToolButtonWithColorDot(
                            icon = Icons.Default.Highlight,
                            label = "Highlight",
                            colorDot = uiState.highlighterColor,
                            isSelected = uiState.activeTool == ToolType.HIGHLIGHTER,
                            testTag = "tool_highlighter",
                            onClick = {
                                if (uiState.activeTool == ToolType.HIGHLIGHTER) {
                                    showColorPicker = !showColorPicker
                                } else {
                                    viewModel.selectTool(ToolType.HIGHLIGHTER)
                                    showColorPicker = true
                                }
                            }
                        )

                        // Text Note tool
                        ToolButton(
                            icon = Icons.Default.TextFields,
                            label = "Text",
                            isSelected = uiState.activeTool == ToolType.TEXT,
                            testTag = "tool_text",
                            onClick = {
                                viewModel.selectTool(ToolType.TEXT)
                                showColorPicker = false
                            }
                        )

                        // Eraser tool
                        ToolButton(
                            icon = Icons.Default.Clear,
                            label = "Eraser",
                            isSelected = uiState.activeTool == ToolType.ERASER,
                            testTag = "tool_eraser",
                            onClick = {
                                viewModel.selectTool(ToolType.ERASER)
                                showColorPicker = false
                            }
                        )

                        // Undo
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = uiState.canUndo,
                            modifier = Modifier.testTag("tool_undo")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }

                        // Redo
                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = uiState.canRedo,
                            modifier = Modifier.testTag("tool_redo")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }

                        // Clear page
                        IconButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.testTag("tool_clear_page")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Current Page Annotations")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(pageBackground),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isPageLoading && uiState.document == null) {
                CircularProgressIndicator()
            } else {
                // Continuous Vertical Scrolling List of Pages with Pinch-to-zoom & Pan
                LazyColumn(
                    state = verticalLazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("vertical_pdf_page_list")
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffsetX
                            translationY = panOffsetY
                        }
                        .pointerInput(uiState.activeTool) {
                            if (uiState.activeTool == ToolType.READ) {
                                awaitEachGesture {
                                    do {
                                        val event = awaitPointerEvent()
                                        val downPointers = event.changes.filter { it.pressed }
                                        if (downPointers.size >= 2) {
                                            // Pinch to zoom with 2 fingers
                                            val zoom = event.calculateZoom()
                                            val pan = event.calculatePan()
                                            val newScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                            zoomScale = newScale
                                            if (newScale > 1f) {
                                                val maxPanX = (newScale - 1f) * size.width / 2f
                                                val maxPanY = (newScale - 1f) * size.height / 2f
                                                panOffsetX = (panOffsetX + pan.x).coerceIn(-maxPanX, maxPanX)
                                                panOffsetY = (panOffsetY + pan.y).coerceIn(-maxPanY, maxPanY)
                                            } else {
                                                panOffsetX = 0f
                                                panOffsetY = 0f
                                            }
                                            event.changes.forEach { it.consume() }
                                        } else if (downPointers.size == 1 && zoomScale > 1.05f) {
                                            // 1-finger pan when zoomed in
                                            val pan = event.calculatePan()
                                            val maxPanX = (zoomScale - 1f) * size.width / 2f
                                            val maxPanY = (zoomScale - 1f) * size.height / 2f
                                            panOffsetX = (panOffsetX + pan.x).coerceIn(-maxPanX, maxPanX)
                                            panOffsetY = (panOffsetY + pan.y).coerceIn(-maxPanY, maxPanY)
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                        }
                        .pointerInput(uiState.activeTool) {
                            if (uiState.activeTool == ToolType.READ) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (zoomScale > 1.2f) {
                                            zoomScale = 1f
                                            panOffsetX = 0f
                                            panOffsetY = 0f
                                        } else {
                                            zoomScale = 2.2f
                                        }
                                    }
                                )
                            }
                        },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.totalPages, key = { it }) { pageIdx ->
                        val pageMatches = uiState.getSearchMatchesForPage(pageIdx)
                        val isCurrentMatch = uiState.isSearchActive && uiState.currentMatch?.pageIndex == pageIdx
                        val activeMatchId = if (uiState.isSearchActive) uiState.currentMatch?.matchId else null
                        PdfPageCard(
                            pageIndex = pageIdx,
                            readingMode = uiState.readingMode,
                            activeTool = uiState.activeTool,
                            selectedColor = uiState.activeColor,
                            strokeWidthDp = uiState.strokeWidth,
                            strokes = uiState.getStrokesForPage(pageIdx),
                            textNotes = uiState.getTextsForPage(pageIdx),
                            searchMatches = pageMatches,
                            activeMatchId = activeMatchId,
                            searchQuery = uiState.pdfSearchQuery,
                            isCurrentSearchMatch = isCurrentMatch,
                            onSelectSearchMatch = { match ->
                                viewModel.jumpToSearchMatchById(match.matchId)
                            },
                            onAddStroke = { viewModel.addStroke(pageIdx, it) },
                            onDeleteStroke = { viewModel.deleteStroke(pageIdx, it) },
                            onAddTextNote = { viewModel.addTextNote(pageIdx, it) },
                            onDeleteTextNote = { viewModel.deleteTextNote(pageIdx, it) },
                            onUpdateTextNote = { viewModel.updateTextNote(pageIdx, it) },
                            loadBitmap = { viewModel.getPageBitmap(pageIdx) }
                        )
                    }
                }
            }

            // Floating Quick Zoom Controller Pill
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .testTag("zoom_controller_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val newScale = (zoomScale - 0.25f).coerceIn(1f, 4f)
                            zoomScale = newScale
                            if (newScale <= 1f) {
                                panOffsetX = 0f
                                panOffsetY = 0f
                            }
                        },
                        enabled = zoomScale > 1f,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("zoom_out_button")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                    }

                    Text(
                        text = "${(zoomScale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (zoomScale > 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clickable {
                                zoomScale = 1f
                                panOffsetX = 0f
                                panOffsetY = 0f
                            }
                            .padding(horizontal = 6.dp)
                            .testTag("zoom_reset_button")
                    )

                    IconButton(
                        onClick = {
                            val newScale = (zoomScale + 0.25f).coerceIn(1f, 4f)
                            zoomScale = newScale
                        },
                        enabled = zoomScale < 4f,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("zoom_in_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Saving / Exporting Overlay
            if (uiState.isExporting) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Saving changes to PDF...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    // Export Bottom Sheet
    if (showExportSheet) {
        ExportBottomSheet(
            sheetState = exportSheetState,
            docTitle = uiState.document?.title ?: "Document",
            currentPage = uiState.currentPageIndex,
            onDismiss = { showExportSheet = false },
            onPrintClicked = { viewModel.printCurrentDocument(context) },
            onShareAnnotatedPdf = { viewModel.shareAnnotatedPdf(context) },
            onExportPageImage = { viewModel.exportCurrentPageImage(context) },
            onCompressClicked = {
                showExportSheet = false
                viewModel.setCompressDialogVisible(true)
            }
        )
    }

    // Thumbnails Grid Sheet
    if (showThumbnailsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThumbnailsSheet = false },
            sheetState = thumbnailsSheetState,
            modifier = Modifier
                .testTag("thumbnails_bottom_sheet")
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Document Pages (${uiState.totalPages})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 85.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.totalPages) { pIdx ->
                        val isCurrent = pIdx == uiState.currentPageIndex
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isCurrent) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .height(110.dp)
                                .clickable {
                                    showThumbnailsSheet = false
                                    coroutineScope.launch {
                                        verticalLazyListState.animateScrollToItem(pIdx)
                                    }
                                }
                                .testTag("thumbnail_page_$pIdx")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Page ${pIdx + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Jump to Page Dialog
    if (showJumpPageDialog) {
        var pageInput by remember { mutableStateOf((uiState.currentPageIndex + 1).toString()) }

        AlertDialog(
            onDismissRequest = { showJumpPageDialog = false },
            title = { Text("Jump to Page") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it },
                    label = { Text("Page Number (1 - ${uiState.totalPages})") },
                    singleLine = true,
                    modifier = Modifier.testTag("jump_page_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = pageInput.toIntOrNull()
                        if (num != null && num in 1..uiState.totalPages) {
                            coroutineScope.launch {
                                verticalLazyListState.animateScrollToItem(num - 1)
                            }
                        }
                        showJumpPageDialog = false
                    },
                    modifier = Modifier.testTag("confirm_jump_page_button")
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpPageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Annotations Confirmation Dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Page Annotations?") },
            text = { Text("Remove all highlights, ink, and notes from Page ${uiState.currentPageIndex + 1}? (You can undo this immediately).") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearPageAnnotations(uiState.currentPageIndex)
                        showClearConfirm = false
                    },
                    modifier = Modifier.testTag("confirm_clear_page_button")
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Save Changes to Original PDF Confirmation Dialog
    if (showSaveToOriginalConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveToOriginalConfirm = false },
            title = { Text("Save Changes to Original PDF?") },
            text = {
                val docTitle = uiState.document?.title ?: "Document"
                Text("This will permanently burn all pen strokes, highlights, and annotations directly into the original PDF file on your device for '$docTitle'.\n\nAre you sure you want to overwrite the original file?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveToOriginalConfirm = false
                        viewModel.saveToOriginalPdfConfirmed()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("confirm_save_to_original_button")
                ) {
                    Text("Save & Overwrite")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveToOriginalConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Compress PDF Dialog
    if (uiState.showCompressDialog) {
        CompressPdfDialog(
            docTitle = uiState.document?.title ?: "Document",
            originalSizeBytes = uiState.document?.fileSizeBytes ?: 0L,
            isCompressing = uiState.isCompressing,
            compressionResult = uiState.compressionResult,
            onDismiss = { viewModel.setCompressDialogVisible(false) },
            onCompress = { level -> viewModel.compressDocument(level) },
            onShareResult = { result -> viewModel.shareCompressedPdf(context, result) },
            onExportSave = { result -> viewModel.exportCompressedPdf(result) }
        )
    }
}

@Composable
private fun PdfPageCard(
    pageIndex: Int,
    readingMode: ReadingMode,
    activeTool: ToolType,
    selectedColor: Color,
    strokeWidthDp: Float,
    strokes: List<com.example.data.model.StrokeAnnotation>,
    textNotes: List<com.example.data.model.TextAnnotation>,
    searchMatches: List<com.example.data.model.PdfSearchMatch> = emptyList(),
    activeMatchId: Long? = null,
    searchQuery: String = "",
    isCurrentSearchMatch: Boolean = false,
    onSelectSearchMatch: (com.example.data.model.PdfSearchMatch) -> Unit = {},
    onAddStroke: (com.example.data.model.StrokeAnnotation) -> Unit,
    onDeleteStroke: (Long) -> Unit,
    onAddTextNote: (com.example.data.model.TextAnnotation) -> Unit,
    onDeleteTextNote: (Long) -> Unit,
    onUpdateTextNote: (com.example.data.model.TextAnnotation) -> Unit,
    loadBitmap: suspend () -> Bitmap?
) {
    var retryTrigger by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, retryTrigger) {
        isLoading = true
        pageBitmap = loadBitmap()
        isLoading = false
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Search Match Banner if matches exist on this page
        if (searchMatches.isNotEmpty()) {
            val highlightedSnippet = remember(searchMatches, searchQuery) {
                val raw = searchMatches.firstOrNull()?.snippet ?: ""
                buildAnnotatedString {
                    val q = searchQuery.trim().lowercase()
                    if (q.isBlank()) {
                        append(raw)
                    } else {
                        var curr = 0
                        val lower = raw.lowercase()
                        while (curr < raw.length) {
                            val idx = lower.indexOf(q, curr)
                            if (idx < 0) {
                                append(raw.substring(curr))
                                break
                            }
                            append(raw.substring(curr, idx))
                            pushStyle(
                                SpanStyle(
                                    background = Color(0xFFFDE047),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF78350F)
                                )
                            )
                            append(raw.substring(idx, idx + q.length))
                            pop()
                            curr = idx + q.length
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isCurrentSearchMatch) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCurrentSearchMatch) Color(0xFFF59E0B) else Color(0xFFCBD5E1)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { searchMatches.firstOrNull()?.let { onSelectSearchMatch(it) } }
                    .padding(bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (isCurrentSearchMatch) Color(0xFFD97706) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("${searchMatches.size} match${if (searchMatches.size > 1) "es" else ""}: \"")
                            append(highlightedSnippet)
                            append("\"")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrentSearchMatch) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isCurrentSearchMatch) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Page Header Label
        Surface(
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            color = if (isCurrentSearchMatch) Color(0xFFF59E0B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Text(
                text = "Page ${pageIndex + 1}${if (isCurrentSearchMatch) " • Active Search Match" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrentSearchMatch) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isCurrentSearchMatch) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            border = if (isCurrentSearchMatch) androidx.compose.foundation.BorderStroke(2.5.dp, Color(0xFFF59E0B)) else null
        ) {
            val bitmap = pageBitmap
            if (bitmap != null) {
                val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF Page ${pageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        colorFilter = ReadingModeFilters.getColorFilter(readingMode)
                    )

                    // Search Highlight Layer on PDF Page
                    if (searchMatches.isNotEmpty()) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(searchMatches) {
                                    detectTapGestures { offset ->
                                        val normX = offset.x / size.width
                                        val normY = offset.y / size.height
                                        val clicked = searchMatches.firstOrNull { m ->
                                            m.rects.any { r ->
                                                normX in (r.left - 0.04f)..(r.right + 0.04f) &&
                                                normY in (r.top - 0.04f)..(r.bottom + 0.04f)
                                            }
                                        }
                                        if (clicked != null) {
                                            onSelectSearchMatch(clicked)
                                        }
                                    }
                                }
                        ) {
                            val canvasW = size.width
                            val canvasH = size.height

                            for (match in searchMatches) {
                                val isActive = match.matchId == activeMatchId
                                val fillColor = if (isActive) Color(0xFFF59E0B).copy(alpha = 0.55f) else Color(0xFFFFEB3B).copy(alpha = 0.40f)
                                val strokeColor = if (isActive) Color(0xFFD97706) else Color(0xFFF59E0B).copy(alpha = 0.70f)
                                val strokeWidthPx = if (isActive) 2.5.dp.toPx() else 1.2.dp.toPx()

                                for (rect in match.rects) {
                                    val left = rect.left * canvasW
                                    val top = rect.top * canvasH
                                    val right = rect.right * canvasW
                                    val bottom = rect.bottom * canvasH
                                    val w = (right - left).coerceAtLeast(14f)
                                    val h = (bottom - top).coerceAtLeast(12f)

                                    drawRoundRect(
                                        color = fillColor,
                                        topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                        size = androidx.compose.ui.geometry.Size(w, h),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                    )
                                    drawRoundRect(
                                        color = strokeColor,
                                        topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                        size = androidx.compose.ui.geometry.Size(w, h),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthPx)
                                    )
                                }
                            }
                        }
                    }

                    // Interactive Drawing & Annotation Layer for this page
                    AnnotationCanvas(
                        toolType = activeTool,
                        selectedColor = selectedColor,
                        strokeWidthDp = strokeWidthDp,
                        strokes = strokes,
                        textNotes = textNotes,
                        onAddStroke = onAddStroke,
                        onDeleteStroke = onDeleteStroke,
                        onAddTextNote = onAddTextNote,
                        onDeleteTextNote = onDeleteTextNote,
                        onUpdateTextNote = onUpdateTextNote,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry loading page",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { retryTrigger++ }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Page ${pageIndex + 1} could not be rendered",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = { retryTrigger++ }) {
                            Text("Tap to Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ToolButtonWithColorDot(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    colorDot: Color,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
                // Color indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colorDot)
                        .border(
                            1.dp,
                            if (isSelected) Color.White else Color.Black.copy(alpha = 0.3f),
                            CircleShape
                        )
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

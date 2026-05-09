package com.haridushakk.classroomai.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF as AndroidRectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haridushakk.classroomai.data.ChatMessage
import com.haridushakk.classroomai.data.ChatRole
import com.haridushakk.classroomai.ui.DrawingPoint
import com.haridushakk.classroomai.ui.DrawingStroke
import com.haridushakk.classroomai.ui.DrawingTool
import com.haridushakk.classroomai.ui.HighlightSelection
import com.haridushakk.classroomai.ui.StudentUiState
import com.haridushakk.classroomai.ui.StudentViewModel
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val SHOWCASE_IMAGE_ASSET_PATH = "imgs"

@Composable
fun StudentRoute(
    viewModel: StudentViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var isShowcaseImagePickerOpen by remember { mutableStateOf(false) }
    val showcaseImageNames = remember {
        context.assets.list(SHOWCASE_IMAGE_ASSET_PATH)
            .orEmpty()
            .filter { it.isShowcaseImageName() }
            .sorted()
    }
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bitmap = runCatching {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            context.contentResolver.openInputStream(uri)
                ?.use { inputStream -> BitmapFactory.decodeStream(inputStream) }
        }.getOrNull()
        if (bitmap != null) {
            viewModel.onWhiteboardImageImported(bitmap)
        }
    }
    fun importShowcaseImage(fileName: String) {
        val bitmap = runCatching {
            context.assets.open("$SHOWCASE_IMAGE_ASSET_PATH/$fileName").use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        }.getOrNull()
        if (bitmap != null) {
            viewModel.onWhiteboardImageImported(bitmap)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorShown()
    }

    StudentScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onNotesChanged = viewModel::onNotesChanged,
        onClearNotes = viewModel::clearNotes,
        onImportWhiteboardImage = { isShowcaseImagePickerOpen = true },
        onDraftMessageChanged = viewModel::onDraftMessageChanged,
        onSendMessage = viewModel::sendMessage,
        onAskWorkspace = viewModel::askAboutWorkspace,
        onToggleTextBox = viewModel::toggleTextBox,
        onDrawingToolChanged = viewModel::onDrawingToolChanged,
        onDrawingCanvasSizeChanged = viewModel::onDrawingCanvasSizeChanged,
        onBeginDrawing = viewModel::beginDrawing,
        onContinueDrawing = viewModel::continueDrawing,
        onPanWorkspace = viewModel::panWorkspace,
        onEndDrawing = viewModel::endDrawing,
        onUndoDrawing = viewModel::undoDrawing,
        onClearDrawing = viewModel::clearDrawing,
        onZoomIn = viewModel::zoomIn,
        onZoomOut = viewModel::zoomOut,
        onResetViewport = viewModel::resetViewport,
    )

    if (isShowcaseImagePickerOpen) {
        ShowcaseImagePickerDialog(
            imageNames = showcaseImageNames,
            onSelectImage = { fileName ->
                importShowcaseImage(fileName)
                isShowcaseImagePickerOpen = false
            },
            onBrowseDevice = {
                isShowcaseImagePickerOpen = false
                imageLauncher.launch(arrayOf("image/*"))
            },
            onDismiss = {
                isShowcaseImagePickerOpen = false
            },
        )
    }
}

@Composable
private fun ShowcaseImagePickerDialog(
    imageNames: List<String>,
    onSelectImage: (String) -> Unit,
    onBrowseDevice: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Vali demopilt")
        },
        text = {
            if (imageNames.isEmpty()) {
                Text(
                    text = "Pane pildid kausta app/src/main/assets/imgs ja ehita app uuesti.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(imageNames) { fileName ->
                        TextButton(
                            onClick = { onSelectImage(fileName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = fileName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBrowseDevice) {
                Text(text = "Sirvi seadmest")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Sulge")
            }
        },
    )
}

private fun String.isShowcaseImageName(): Boolean {
    val name = lowercase()
    return name.endsWith(".png") ||
        name.endsWith(".jpg") ||
        name.endsWith(".jpeg") ||
        name.endsWith(".webp")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentScreen(
    uiState: StudentUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNotesChanged: (String) -> Unit,
    onClearNotes: () -> Unit,
    onImportWhiteboardImage: () -> Unit,
    onDraftMessageChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onAskWorkspace: () -> Unit,
    onToggleTextBox: () -> Unit,
    onDrawingToolChanged: (DrawingTool) -> Unit,
    onDrawingCanvasSizeChanged: (Int, Int) -> Unit,
    onBeginDrawing: (Float, Float) -> Unit,
    onContinueDrawing: (Float, Float) -> Unit,
    onPanWorkspace: (Float, Float) -> Unit,
    onEndDrawing: () -> Unit,
    onUndoDrawing: () -> Unit,
    onClearDrawing: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetViewport: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Õpilase tööala") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Tagasi",
                        )
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            WorkspacePanel(
                uiState = uiState,
                onNotesChanged = onNotesChanged,
                onClearNotes = onClearNotes,
                onImportWhiteboardImage = onImportWhiteboardImage,
                onAskWorkspace = onAskWorkspace,
                onToggleTextBox = onToggleTextBox,
                onDrawingToolChanged = onDrawingToolChanged,
                onDrawingCanvasSizeChanged = onDrawingCanvasSizeChanged,
                onBeginDrawing = onBeginDrawing,
                onContinueDrawing = onContinueDrawing,
                onPanWorkspace = onPanWorkspace,
                onEndDrawing = onEndDrawing,
                onUndoDrawing = onUndoDrawing,
                onClearDrawing = onClearDrawing,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onResetViewport = onResetViewport,
                modifier = Modifier.fillMaxSize(),
            )

            val chatModifier = if (maxWidth >= 840.dp) {
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(20.dp)
                    .fillMaxHeight(0.9f)
                    .widthIn(min = 300.dp, max = 360.dp)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.44f)
            }

            ChatPanel(
                teacherMaterial = uiState.teacherMaterial,
                messages = uiState.messages,
                draftMessage = uiState.draftMessage,
                isTyping = uiState.isTyping,
                onDraftMessageChanged = onDraftMessageChanged,
                onSendMessage = onSendMessage,
                modifier = chatModifier,
            )
        }
    }
}

@Composable
private fun WorkspacePanel(
    uiState: StudentUiState,
    onNotesChanged: (String) -> Unit,
    onClearNotes: () -> Unit,
    onImportWhiteboardImage: () -> Unit,
    onAskWorkspace: () -> Unit,
    onToggleTextBox: () -> Unit,
    onDrawingToolChanged: (DrawingTool) -> Unit,
    onDrawingCanvasSizeChanged: (Int, Int) -> Unit,
    onBeginDrawing: (Float, Float) -> Unit,
    onContinueDrawing: (Float, Float) -> Unit,
    onPanWorkspace: (Float, Float) -> Unit,
    onEndDrawing: () -> Unit,
    onUndoDrawing: () -> Unit,
    onClearDrawing: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetViewport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(uiState.notes, selection = TextRange(uiState.notes.length)))
    }

    LaunchedEffect(uiState.notes) {
        if (uiState.notes != fieldValue.text) {
            fieldValue = TextFieldValue(uiState.notes, selection = TextRange(uiState.notes.length))
        }
    }

    fun updateNotes(value: TextFieldValue) {
        fieldValue = value
        onNotesChanged(value.text)
    }

    Box(modifier = modifier) {
        DrawingCanvas(
            uiState = uiState,
            strokes = uiState.drawingStrokes,
            activeStroke = uiState.activeDrawingStroke,
            onDrawingCanvasSizeChanged = onDrawingCanvasSizeChanged,
            onBeginDrawing = onBeginDrawing,
            onContinueDrawing = onContinueDrawing,
            onPanWorkspace = onPanWorkspace,
            onEndDrawing = onEndDrawing,
            modifier = Modifier.fillMaxSize(),
        )
        WorkspaceToolbar(
            uiState = uiState,
            onAskWorkspace = onAskWorkspace,
            onImportWhiteboardImage = onImportWhiteboardImage,
            onToggleTextBox = onToggleTextBox,
            onDrawingToolChanged = onDrawingToolChanged,
            onUndoDrawing = onUndoDrawing,
            onClearDrawing = onClearDrawing,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onResetViewport = onResetViewport,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
        )
        if (uiState.isTextBoxVisible) {
            TextOverlay(
                fieldValue = fieldValue,
                onValueChange = ::updateNotes,
                onClearNotes = {
                    fieldValue = TextFieldValue("")
                    onClearNotes()
                },
                onClose = onToggleTextBox,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WorkspaceToolbar(
    uiState: StudentUiState,
    onAskWorkspace: () -> Unit,
    onImportWhiteboardImage: () -> Unit,
    onToggleTextBox: () -> Unit,
    onDrawingToolChanged: (DrawingTool) -> Unit,
    onUndoDrawing: () -> Unit,
    onClearDrawing: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetViewport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onToggleTextBox) {
                Text(
                    text = "Tekst",
                    fontWeight = if (uiState.isTextBoxVisible) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                )
            }
            DrawingToolButton(
                selected = uiState.whiteboardImage != null,
                onClick = onImportWhiteboardImage,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.UploadFile,
                        contentDescription = "Lisa pilt",
                    )
                },
            )
            DrawingToolButton(
                selected = uiState.drawingTool == DrawingTool.Pen,
                onClick = { onDrawingToolChanged(DrawingTool.Pen) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Pliiats",
                    )
                },
            )
            DrawingToolButton(
                selected = uiState.drawingTool == DrawingTool.Eraser,
                onClick = { onDrawingToolChanged(DrawingTool.Eraser) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Kustutaja",
                    )
                },
            )
            DrawingToolButton(
                selected = uiState.drawingTool == DrawingTool.Select,
                onClick = { onDrawingToolChanged(DrawingTool.Select) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Gesture,
                        contentDescription = "Vabakäe valik",
                    )
                },
            )
            DrawingToolButton(
                selected = uiState.drawingTool == DrawingTool.Pan,
                onClick = { onDrawingToolChanged(DrawingTool.Pan) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.OpenWith,
                        contentDescription = "Liiguta",
                    )
                },
            )
            IconButton(
                onClick = onUndoDrawing,
                enabled = uiState.drawingStrokes.isNotEmpty() ||
                    uiState.highlightSelection != null ||
                    uiState.activeHighlightSelection != null,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Võta tagasi",
                )
            }
            IconButton(
                onClick = onClearDrawing,
                enabled = uiState.drawingStrokes.isNotEmpty() ||
                    uiState.activeDrawingStroke != null ||
                    uiState.highlightSelection != null ||
                    uiState.activeHighlightSelection != null,
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Tühjenda joonistus",
                )
            }
            IconButton(onClick = onZoomOut) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Vähenda",
                )
            }
            Text(
                text = "${(uiState.viewportScale * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.widthIn(min = 44.dp),
            )
            IconButton(onClick = onZoomIn) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Suurenda",
                )
            }
            IconButton(onClick = onResetViewport) {
                Icon(
                    imageVector = Icons.Filled.ZoomOutMap,
                    contentDescription = "Lähtesta vaade",
                )
            }
            Button(
                onClick = onAskWorkspace,
                enabled = !uiState.isTyping &&
                    (uiState.notes.isNotBlank() ||
                        uiState.drawingStrokes.isNotEmpty() ||
                        uiState.activeDrawingStroke != null ||
                        uiState.whiteboardImage != null ||
                        uiState.highlightSelection != null ||
                        uiState.activeHighlightSelection != null ||
                        uiState.draftMessage.isNotBlank()),
            ) {
                Text(text = "Küsi")
            }
        }
    }
}

@Composable
private fun TextOverlay(
    fieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onClearNotes: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 520.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onValueChange(fieldValue.withBoldMarkers()) },
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatBold,
                        contentDescription = "Paks kiri",
                    )
                }
                IconButton(
                    onClick = { onValueChange(fieldValue.withBulletList()) },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = "Täpploend",
                    )
                }
                IconButton(onClick = onClearNotes) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Tühjenda märkmed",
                    )
                }
                TextButton(onClick = onClose) {
                    Text(text = "Valmis")
                }
            }
            OutlinedTextField(
                value = fieldValue,
                onValueChange = onValueChange,
                placeholder = { Text(text = "Kirjuta ülesanne või märkmed...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 104.dp, max = 180.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 24.sp,
                ),
            )
        }
    }
}

@Composable
private fun DrawingToolButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    if (selected) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(44.dp),
            content = icon,
        )
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(44.dp),
            content = icon,
        )
    }
}

@Composable
private fun DrawingCanvas(
    uiState: StudentUiState,
    strokes: List<DrawingStroke>,
    activeStroke: DrawingStroke?,
    onDrawingCanvasSizeChanged: (Int, Int) -> Unit,
    onBeginDrawing: (Float, Float) -> Unit,
    onContinueDrawing: (Float, Float) -> Unit,
    onPanWorkspace: (Float, Float) -> Unit,
    onEndDrawing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.White,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    onDrawingCanvasSizeChanged(size.width, size.height)
                }
                .pointerInput(uiState.drawingTool) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onBeginDrawing(offset.x, offset.y)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (uiState.drawingTool == DrawingTool.Pan) {
                                onPanWorkspace(dragAmount.x, dragAmount.y)
                            } else {
                                onContinueDrawing(change.position.x, change.position.y)
                            }
                        },
                        onDragEnd = onEndDrawing,
                        onDragCancel = onEndDrawing,
                    )
                },
        ) {
            drawDottedBackground(
                viewportOffsetX = uiState.viewportOffsetX,
                viewportOffsetY = uiState.viewportOffsetY,
                viewportScale = uiState.viewportScale,
            )
            drawWhiteboardTextObject(
                text = uiState.notes,
                viewportOffsetX = uiState.viewportOffsetX,
                viewportOffsetY = uiState.viewportOffsetY,
                viewportScale = uiState.viewportScale,
            )
            uiState.whiteboardImage?.let { bitmap ->
                drawWhiteboardImageObject(
                    bitmap = bitmap,
                    top = uiState.notes.nextWhiteboardObjectTop(),
                    viewportOffsetX = uiState.viewportOffsetX,
                    viewportOffsetY = uiState.viewportOffsetY,
                    viewportScale = uiState.viewportScale,
                )
            }
            (strokes + listOfNotNull(activeStroke)).forEach { stroke ->
                drawStroke(
                    stroke = stroke,
                    viewportOffsetX = uiState.viewportOffsetX,
                    viewportOffsetY = uiState.viewportOffsetY,
                    viewportScale = uiState.viewportScale,
                )
            }
            val highlightSelection = uiState.activeHighlightSelection ?: uiState.highlightSelection
            if (highlightSelection != null) {
                drawHighlightSelection(
                    highlightSelection = highlightSelection,
                    viewportOffsetX = uiState.viewportOffsetX,
                    viewportOffsetY = uiState.viewportOffsetY,
                    viewportScale = uiState.viewportScale,
                    isActive = uiState.activeHighlightSelection != null,
                )
            }
        }
    }
}

private const val DOT_GRID_SPACING_PX = 32f
private const val DOT_GRID_RADIUS_PX = 1.35f
private const val TEXT_OBJECT_LEFT_PX = 64f
private const val TEXT_OBJECT_TOP_PX = 96f
private const val TEXT_OBJECT_WIDTH_PX = 680f
private const val TEXT_OBJECT_PADDING_PX = 20f
private const val TEXT_OBJECT_TEXT_SIZE_PX = 25f
private const val TEXT_OBJECT_LINE_HEIGHT_PX = 34f
private const val TEXT_OBJECT_RADIUS_PX = 14f
private const val IMAGE_OBJECT_PADDING_PX = 12f
private const val IMAGE_OBJECT_MAX_WIDTH_PX = 680f
private const val IMAGE_OBJECT_MAX_HEIGHT_PX = 420f
private const val OBJECT_GAP_PX = 24f

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWhiteboardTextObject(
    text: String,
    viewportOffsetX: Float,
    viewportOffsetY: Float,
    viewportScale: Float,
) {
    if (text.isBlank()) return

    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(31, 41, 55)
        textSize = TEXT_OBJECT_TEXT_SIZE_PX * viewportScale
    }
    val lines = text.toWrappedCanvasLines(
        paint = paint,
        maxLineWidth = (TEXT_OBJECT_WIDTH_PX - 2 * TEXT_OBJECT_PADDING_PX) * viewportScale,
    )
    val left = (TEXT_OBJECT_LEFT_PX + viewportOffsetX) * viewportScale
    val top = (TEXT_OBJECT_TOP_PX + viewportOffsetY) * viewportScale
    val width = TEXT_OBJECT_WIDTH_PX * viewportScale
    val padding = TEXT_OBJECT_PADDING_PX * viewportScale
    val lineHeight = TEXT_OBJECT_LINE_HEIGHT_PX * viewportScale
    val bottom = top + padding * 2 + lines.size * lineHeight
    val radius = TEXT_OBJECT_RADIUS_PX * viewportScale
    val bounds = AndroidRectF(left, top, left + width, bottom)
    val backgroundPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = AndroidPaint.Style.FILL
    }
    val borderPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(209, 213, 219)
        style = AndroidPaint.Style.STROKE
        strokeWidth = (2f * viewportScale).coerceAtLeast(1f)
    }

    drawContext.canvas.nativeCanvas.apply {
        drawRoundRect(bounds, radius, radius, backgroundPaint)
        drawRoundRect(bounds, radius, radius, borderPaint)
        var y = top + padding + paint.textSize
        lines.forEach { line ->
            drawText(line, left + padding, y, paint)
            y += lineHeight
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWhiteboardImageObject(
    bitmap: Bitmap,
    top: Float,
    viewportOffsetX: Float,
    viewportOffsetY: Float,
    viewportScale: Float,
) {
    val imageScale = min(
        IMAGE_OBJECT_MAX_WIDTH_PX / bitmap.width.toFloat(),
        IMAGE_OBJECT_MAX_HEIGHT_PX / bitmap.height.toFloat(),
    ).coerceAtMost(1f)
    val imageWidth = bitmap.width * imageScale * viewportScale
    val imageHeight = bitmap.height * imageScale * viewportScale
    val padding = IMAGE_OBJECT_PADDING_PX * viewportScale
    val left = (TEXT_OBJECT_LEFT_PX + viewportOffsetX) * viewportScale
    val screenTop = (top + viewportOffsetY) * viewportScale
    val backgroundBounds = AndroidRectF(
        left,
        screenTop,
        left + imageWidth + padding * 2,
        screenTop + imageHeight + padding * 2,
    )
    val imageBounds = AndroidRectF(
        backgroundBounds.left + padding,
        backgroundBounds.top + padding,
        backgroundBounds.left + padding + imageWidth,
        backgroundBounds.top + padding + imageHeight,
    )
    val radius = TEXT_OBJECT_RADIUS_PX * viewportScale
    val backgroundPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = AndroidPaint.Style.FILL
    }
    val borderPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(209, 213, 219)
        style = AndroidPaint.Style.STROKE
        strokeWidth = (2f * viewportScale).coerceAtLeast(1f)
    }

    drawContext.canvas.nativeCanvas.apply {
        drawRoundRect(backgroundBounds, radius, radius, backgroundPaint)
        drawRoundRect(backgroundBounds, radius, radius, borderPaint)
        drawBitmap(bitmap, null, imageBounds, null)
    }
}

private fun String.toWrappedCanvasLines(
    paint: AndroidPaint,
    maxLineWidth: Float,
): List<String> {
    return buildList {
        lineSequence().forEach { paragraph ->
            val words = paragraph.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) {
                add("")
                return@forEach
            }

            var line = ""
            words.forEach { word ->
                val candidate = if (line.isBlank()) word else "$line $word"
                if (paint.measureText(candidate) <= maxLineWidth || line.isBlank()) {
                    line = candidate
                } else {
                    add(line)
                    line = word
                }
            }
            if (line.isNotBlank()) {
                add(line)
            }
        }
    }
}

private fun String.nextWhiteboardObjectTop(): Float {
    if (isBlank()) return TEXT_OBJECT_TOP_PX

    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        textSize = TEXT_OBJECT_TEXT_SIZE_PX
    }
    val lines = toWrappedCanvasLines(
        paint = paint,
        maxLineWidth = TEXT_OBJECT_WIDTH_PX - 2 * TEXT_OBJECT_PADDING_PX,
    )
    return TEXT_OBJECT_TOP_PX +
        TEXT_OBJECT_PADDING_PX * 2 +
        lines.size * TEXT_OBJECT_LINE_HEIGHT_PX +
        OBJECT_GAP_PX
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDottedBackground(
    viewportOffsetX: Float,
    viewportOffsetY: Float,
    viewportScale: Float,
) {
    val leftWorld = -viewportOffsetX
    val topWorld = -viewportOffsetY
    val rightWorld = size.width / viewportScale - viewportOffsetX
    val bottomWorld = size.height / viewportScale - viewportOffsetY
    val dotRadius = (DOT_GRID_RADIUS_PX * viewportScale).coerceIn(1f, 2.4f)
    val dotColor = Color(0xFFD7DCE5)

    var x = floor(leftWorld / DOT_GRID_SPACING_PX) * DOT_GRID_SPACING_PX
    while (x <= rightWorld + DOT_GRID_SPACING_PX) {
        var y = floor(topWorld / DOT_GRID_SPACING_PX) * DOT_GRID_SPACING_PX
        while (y <= bottomWorld + DOT_GRID_SPACING_PX) {
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(
                    x = (x + viewportOffsetX) * viewportScale,
                    y = (y + viewportOffsetY) * viewportScale,
                ),
            )
            y += DOT_GRID_SPACING_PX
        }
        x += DOT_GRID_SPACING_PX
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    stroke: DrawingStroke,
    viewportOffsetX: Float,
    viewportOffsetY: Float,
    viewportScale: Float,
) {
    if (stroke.points.isEmpty()) return

    val color = if (stroke.isEraser) Color.White else Color(stroke.color)
    fun DrawingPoint.toScreenOffset(): Offset {
        return Offset(
            x = (x + viewportOffsetX) * viewportScale,
            y = (y + viewportOffsetY) * viewportScale,
        )
    }

    if (stroke.points.size == 1) {
        val point = stroke.points.first()
        drawCircle(
            color = color,
            radius = (stroke.widthPx * viewportScale) / 2f,
            center = point.toScreenOffset(),
        )
        return
    }

    stroke.points.zipWithNext().forEach { (start, end) ->
        drawLine(
            color = color,
            start = start.toScreenOffset(),
            end = end.toScreenOffset(),
            strokeWidth = stroke.widthPx * viewportScale,
            cap = StrokeCap.Round,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHighlightSelection(
    highlightSelection: HighlightSelection,
    viewportOffsetX: Float,
    viewportOffsetY: Float,
    viewportScale: Float,
    isActive: Boolean,
) {
    val path = highlightSelection.toScreenPath(
        viewportOffsetX = viewportOffsetX,
        viewportOffsetY = viewportOffsetY,
        viewportScale = viewportScale,
    )
    if (path == null) return

    if (highlightSelection.points.size >= 3) {
        drawPath(
            path = path,
            color = Color(0x33FACC15),
        )
    }
    drawPath(
        path = path,
        color = if (isActive) Color(0xFFCA8A04) else Color(0xFFEAB308),
        style = Stroke(width = 3f),
    )
}

private fun HighlightSelection.toScreenPath(
    viewportOffsetX: Float,
    viewportOffsetY: Float,
    viewportScale: Float,
): Path? {
    val first = points.firstOrNull() ?: return null
    return Path().apply {
        moveTo(
            x = (first.x + viewportOffsetX) * viewportScale,
            y = (first.y + viewportOffsetY) * viewportScale,
        )
        points.drop(1).forEach { point ->
            lineTo(
                x = (point.x + viewportOffsetX) * viewportScale,
                y = (point.y + viewportOffsetY) * viewportScale,
            )
        }
        if (points.size >= 3) {
            close()
        }
    }
}

@Composable
private fun ChatPanel(
    teacherMaterial: String,
    messages: List<ChatMessage>,
    draftMessage: String,
    isTyping: Boolean,
    onDraftMessageChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isTyping) {
        val itemCount = messages.size + if (isTyping) 1 else 0
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "AI vestlus",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (teacherMaterial.isBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF7ED),
                ) {
                    Text(
                        text = "Õpetaja ei ole tänase tunni materjali veel lisanud. AI vastab üldistele küsimustele.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9A3412),
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = messages,
                    key = { message -> message.id },
                ) { message ->
                    ChatBubble(message = message)
                }
                if (isTyping) {
                    item(key = "typing") {
                        TypingIndicator()
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draftMessage,
                    onValueChange = onDraftMessageChanged,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp, max = 128.dp),
                    placeholder = { Text(text = "Küsi küsimus...") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { onSendMessage() },
                    ),
                )
                FilledIconButton(
                    onClick = onSendMessage,
                    enabled = draftMessage.isNotBlank() && !isTyping,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Saada",
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
) {
    val isStudent = message.role == ChatRole.Student
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isStudent) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 292.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomEnd = if (isStudent) 4.dp else 18.dp,
                bottomStart = if (isStudent) 18.dp else 4.dp,
            ),
            color = if (isStudent) Color(0xFF6D28D9) else Color(0xFFF1F3F6),
        ) {
            Text(
                text = message.text.toLatexAnnotatedString(),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (isStudent) Color.White else Color(0xFF1F2937),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private val SuperscriptStyle = SpanStyle(
    baselineShift = BaselineShift.Superscript,
    fontSize = 0.78.em,
)

private val SubscriptStyle = SpanStyle(
    baselineShift = BaselineShift.Subscript,
    fontSize = 0.78.em,
)

internal fun String.toLatexAnnotatedString(): AnnotatedString {
    return buildAnnotatedString {
        appendLatexAwareText(this@toLatexAnnotatedString)
    }
}

private fun AnnotatedString.Builder.appendLatexAwareText(value: String) {
    var index = 0
    while (index < value.length) {
        when {
            value.startsWith("$$", index) -> {
                val endIndex = value.indexOf("$$", startIndex = index + 2)
                if (endIndex == -1) {
                    append(value[index])
                    index += 1
                } else {
                    appendLatexMath(value.substring(index + 2, endIndex))
                    index = endIndex + 2
                }
            }
            value[index] == '$' -> {
                val endIndex = value.indexOf('$', startIndex = index + 1)
                if (endIndex == -1) {
                    append(value[index])
                    index += 1
                } else {
                    appendLatexMath(value.substring(index + 1, endIndex))
                    index = endIndex + 1
                }
            }
            value.startsWith("\\(", index) -> {
                val endIndex = value.indexOf("\\)", startIndex = index + 2)
                if (endIndex == -1) {
                    append(value[index])
                    index += 1
                } else {
                    appendLatexMath(value.substring(index + 2, endIndex))
                    index = endIndex + 2
                }
            }
            value.startsWith("\\[", index) -> {
                val endIndex = value.indexOf("\\]", startIndex = index + 2)
                if (endIndex == -1) {
                    append(value[index])
                    index += 1
                } else {
                    appendLatexMath(value.substring(index + 2, endIndex))
                    index = endIndex + 2
                }
            }
            else -> {
                append(value[index])
                index += 1
            }
        }
    }
}

private fun AnnotatedString.Builder.appendLatexMath(value: String) {
    var index = 0
    while (index < value.length) {
        when (val char = value[index]) {
            '^', '_' -> {
                val argument = value.readLatexArgument(index + 1)
                if (argument.text.isNotBlank()) {
                    withStyle(if (char == '^') SuperscriptStyle else SubscriptStyle) {
                        appendLatexMath(argument.text)
                    }
                }
                index = argument.nextIndex
            }
            '\\' -> {
                index = appendLatexCommand(value, index)
            }
            '{' -> {
                val argument = value.readLatexArgument(index)
                appendLatexMath(argument.text)
                index = argument.nextIndex
            }
            '}' -> {
                index += 1
            }
            else -> {
                append(char)
                index += 1
            }
        }
    }
}

private fun AnnotatedString.Builder.appendLatexCommand(
    value: String,
    startIndex: Int,
): Int {
    val command = value.readLatexCommand(startIndex)
    return when (command.name) {
        "frac" -> appendLatexFraction(value, command.nextIndex)
        "sqrt" -> appendLatexRoot(value, command.nextIndex)
        "left", "right" -> command.nextIndex
        ",", ";", " " -> {
            append(" ")
            command.nextIndex
        }
        else -> {
            append(command.name.toLatexSymbol())
            command.nextIndex
        }
    }
}

private fun AnnotatedString.Builder.appendLatexFraction(
    value: String,
    startIndex: Int,
): Int {
    val numerator = value.readLatexArgument(startIndex)
    val denominator = value.readLatexArgument(numerator.nextIndex)
    if (numerator.text.isBlank() || denominator.text.isBlank()) {
        append("frac")
    } else {
        append("(")
        appendLatexMath(numerator.text)
        append(" / ")
        appendLatexMath(denominator.text)
        append(")")
    }
    return denominator.nextIndex
}

private fun AnnotatedString.Builder.appendLatexRoot(
    value: String,
    startIndex: Int,
): Int {
    val argument = value.readLatexArgument(startIndex)
    if (argument.text.isBlank()) {
        append("sqrt")
    } else {
        append("√(")
        appendLatexMath(argument.text)
        append(")")
    }
    return argument.nextIndex
}

private data class LatexCommand(
    val name: String,
    val nextIndex: Int,
)

private data class LatexArgument(
    val text: String,
    val nextIndex: Int,
)

private fun String.readLatexCommand(startIndex: Int): LatexCommand {
    val commandStart = startIndex + 1
    if (commandStart >= length) {
        return LatexCommand(name = "", nextIndex = length)
    }

    if (!this[commandStart].isLetter()) {
        return LatexCommand(
            name = this[commandStart].toString(),
            nextIndex = commandStart + 1,
        )
    }

    var index = commandStart
    while (index < length && this[index].isLetter()) {
        index += 1
    }
    return LatexCommand(
        name = substring(commandStart, index),
        nextIndex = index,
    )
}

private fun String.readLatexArgument(startIndex: Int): LatexArgument {
    var index = startIndex
    while (index < length && this[index].isWhitespace()) {
        index += 1
    }
    if (index >= length) return LatexArgument(text = "", nextIndex = index)

    if (this[index] != '{') {
        return if (this[index] == '\\') {
            val command = readLatexCommand(index)
            LatexArgument(
                text = substring(index, command.nextIndex),
                nextIndex = command.nextIndex,
            )
        } else {
            LatexArgument(
                text = this[index].toString(),
                nextIndex = index + 1,
            )
        }
    }

    val contentStart = index + 1
    var depth = 1
    index += 1
    while (index < length && depth > 0) {
        when (this[index]) {
            '{' -> depth += 1
            '}' -> depth -= 1
        }
        index += 1
    }

    return if (depth == 0) {
        LatexArgument(
            text = substring(contentStart, index - 1),
            nextIndex = index,
        )
    } else {
        LatexArgument(
            text = substring(contentStart),
            nextIndex = length,
        )
    }
}

private fun String.toLatexSymbol(): String {
    return when (this) {
        "alpha" -> "α"
        "beta" -> "β"
        "gamma" -> "γ"
        "delta" -> "δ"
        "epsilon" -> "ε"
        "theta" -> "θ"
        "lambda" -> "λ"
        "mu" -> "μ"
        "pi" -> "π"
        "rho" -> "ρ"
        "sigma" -> "σ"
        "phi" -> "φ"
        "omega" -> "ω"
        "cdot" -> "·"
        "times" -> "×"
        "div" -> "÷"
        "pm" -> "±"
        "le", "leq" -> "≤"
        "ge", "geq" -> "≥"
        "neq", "ne" -> "≠"
        "approx" -> "≈"
        "infty" -> "∞"
        "to", "rightarrow" -> "→"
        "leftarrow" -> "←"
        "in" -> "∈"
        "notin" -> "∉"
        "sum" -> "Σ"
        "int" -> "∫"
        "{" -> "{"
        "}" -> "}"
        else -> this
    }
}

@Composable
private fun TypingIndicator() {
    var dotCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(350)
            dotCount = if (dotCount == 3) 1 else dotCount + 1
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF1F3F6),
        ) {
            Text(
                text = "AI kirjutab${".".repeat(dotCount)}",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = Color(0xFF4B5563),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun TextFieldValue.withBoldMarkers(): TextFieldValue {
    val start = min(selection.start, selection.end)
    val end = max(selection.start, selection.end)
    return if (start == end) {
        val updatedText = text.replaceRange(start, end, "****")
        copy(
            text = updatedText,
            selection = TextRange(start + 2),
        )
    } else {
        val selectedText = text.substring(start, end)
        val updatedText = text.replaceRange(start, end, "**$selectedText**")
        copy(
            text = updatedText,
            selection = TextRange(end + 4),
        )
    }
}

private fun TextFieldValue.withBulletList(): TextFieldValue {
    val selectedStart = min(selection.start, selection.end)
    val selectedEnd = max(selection.start, selection.end)
    val lineStart = text.lastIndexOf(
        char = '\n',
        startIndex = (selectedStart - 1).coerceAtLeast(0),
    ).let { index -> if (index == -1) 0 else index + 1 }
    val lineEnd = text.indexOf(
        char = '\n',
        startIndex = selectedEnd,
    ).let { index -> if (index == -1) text.length else index }
    val block = text.substring(lineStart, lineEnd)
    val bulletedBlock = block
        .split('\n')
        .joinToString(separator = "\n") { line ->
            if (line.startsWith("- ")) line else "- $line"
        }
    val updatedText = text.replaceRange(lineStart, lineEnd, bulletedBlock)
    val cursor = (lineStart + bulletedBlock.length).coerceIn(0, updatedText.length)
    return copy(
        text = updatedText,
        selection = TextRange(cursor),
    )
}

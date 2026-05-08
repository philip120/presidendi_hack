package com.haridushakk.classroomai.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haridushakk.classroomai.data.ChatMessage
import com.haridushakk.classroomai.data.ChatRole
import com.haridushakk.classroomai.ui.DrawingStroke
import com.haridushakk.classroomai.ui.DrawingTool
import com.haridushakk.classroomai.ui.StudentUiState
import com.haridushakk.classroomai.ui.StudentViewModel
import com.haridushakk.classroomai.ui.WorkspaceMode
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

@Composable
fun StudentRoute(
    viewModel: StudentViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
        onDraftMessageChanged = viewModel::onDraftMessageChanged,
        onSendMessage = viewModel::sendMessage,
        onAskWorkspace = viewModel::askAboutWorkspace,
        onWorkspaceModeChanged = viewModel::onWorkspaceModeChanged,
        onDrawingToolChanged = viewModel::onDrawingToolChanged,
        onDrawingCanvasSizeChanged = viewModel::onDrawingCanvasSizeChanged,
        onBeginDrawing = viewModel::beginDrawing,
        onContinueDrawing = viewModel::continueDrawing,
        onEndDrawing = viewModel::endDrawing,
        onUndoDrawing = viewModel::undoDrawing,
        onClearDrawing = viewModel::clearDrawing,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentScreen(
    uiState: StudentUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNotesChanged: (String) -> Unit,
    onClearNotes: () -> Unit,
    onDraftMessageChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onAskWorkspace: () -> Unit,
    onWorkspaceModeChanged: (WorkspaceMode) -> Unit,
    onDrawingToolChanged: (DrawingTool) -> Unit,
    onDrawingCanvasSizeChanged: (Int, Int) -> Unit,
    onBeginDrawing: (Float, Float) -> Unit,
    onContinueDrawing: (Float, Float) -> Unit,
    onEndDrawing: () -> Unit,
    onUndoDrawing: () -> Unit,
    onClearDrawing: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Student Workspace") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            if (maxWidth >= 840.dp) {
                Row(modifier = Modifier.fillMaxSize()) {
                    WorkspacePanel(
                        uiState = uiState,
                        onNotesChanged = onNotesChanged,
                        onClearNotes = onClearNotes,
                        onAskWorkspace = onAskWorkspace,
                        onWorkspaceModeChanged = onWorkspaceModeChanged,
                        onDrawingToolChanged = onDrawingToolChanged,
                        onDrawingCanvasSizeChanged = onDrawingCanvasSizeChanged,
                        onBeginDrawing = onBeginDrawing,
                        onContinueDrawing = onContinueDrawing,
                        onEndDrawing = onEndDrawing,
                        onUndoDrawing = onUndoDrawing,
                        onClearDrawing = onClearDrawing,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(3f),
                    )
                    VerticalDivider()
                    ChatPanel(
                        teacherMaterial = uiState.teacherMaterial,
                        messages = uiState.messages,
                        draftMessage = uiState.draftMessage,
                        isTyping = uiState.isTyping,
                        onDraftMessageChanged = onDraftMessageChanged,
                        onSendMessage = onSendMessage,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(2f),
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    WorkspacePanel(
                        uiState = uiState,
                        onNotesChanged = onNotesChanged,
                        onClearNotes = onClearNotes,
                        onAskWorkspace = onAskWorkspace,
                        onWorkspaceModeChanged = onWorkspaceModeChanged,
                        onDrawingToolChanged = onDrawingToolChanged,
                        onDrawingCanvasSizeChanged = onDrawingCanvasSizeChanged,
                        onBeginDrawing = onBeginDrawing,
                        onContinueDrawing = onContinueDrawing,
                        onEndDrawing = onEndDrawing,
                        onUndoDrawing = onUndoDrawing,
                        onClearDrawing = onClearDrawing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(3f),
                    )
                    HorizontalDivider()
                    ChatPanel(
                        teacherMaterial = uiState.teacherMaterial,
                        messages = uiState.messages,
                        draftMessage = uiState.draftMessage,
                        isTyping = uiState.isTyping,
                        onDraftMessageChanged = onDraftMessageChanged,
                        onSendMessage = onSendMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(2f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspacePanel(
    uiState: StudentUiState,
    onNotesChanged: (String) -> Unit,
    onClearNotes: () -> Unit,
    onAskWorkspace: () -> Unit,
    onWorkspaceModeChanged: (WorkspaceMode) -> Unit,
    onDrawingToolChanged: (DrawingTool) -> Unit,
    onDrawingCanvasSizeChanged: (Int, Int) -> Unit,
    onBeginDrawing: (Float, Float) -> Unit,
    onContinueDrawing: (Float, Float) -> Unit,
    onEndDrawing: () -> Unit,
    onUndoDrawing: () -> Unit,
    onClearDrawing: () -> Unit,
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

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Workspace",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onAskWorkspace,
                enabled = !uiState.isTyping &&
                    (uiState.notes.isNotBlank() ||
                        uiState.drawingStrokes.isNotEmpty() ||
                        uiState.activeDrawingStroke != null ||
                        uiState.draftMessage.isNotBlank()),
            ) {
                Text(text = "Ask")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkspaceMode.entries.forEach { mode ->
                TextButton(
                    onClick = { onWorkspaceModeChanged(mode) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = mode.label,
                        fontWeight = if (uiState.workspaceMode == mode) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                    )
                }
            }
        }
        when (uiState.workspaceMode) {
            WorkspaceMode.Text -> {
                TextEditor(
                    fieldValue = fieldValue,
                    onValueChange = ::updateNotes,
                    onClearNotes = {
                        fieldValue = TextFieldValue("")
                        onClearNotes()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            WorkspaceMode.Draw -> {
                DrawingWorkspace(
                    uiState = uiState,
                    onDrawingToolChanged = onDrawingToolChanged,
                    onDrawingCanvasSizeChanged = onDrawingCanvasSizeChanged,
                    onBeginDrawing = onBeginDrawing,
                    onContinueDrawing = onContinueDrawing,
                    onEndDrawing = onEndDrawing,
                    onUndoDrawing = onUndoDrawing,
                    onClearDrawing = onClearDrawing,
                    modifier = Modifier.weight(1f),
                )
            }
            WorkspaceMode.Mixed -> {
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = ::updateNotes,
                    placeholder = { Text(text = "Type problem text or notes...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 180.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 24.sp,
                    ),
                )
                DrawingWorkspace(
                    uiState = uiState,
                    onDrawingToolChanged = onDrawingToolChanged,
                    onDrawingCanvasSizeChanged = onDrawingCanvasSizeChanged,
                    onBeginDrawing = onBeginDrawing,
                    onContinueDrawing = onContinueDrawing,
                    onEndDrawing = onEndDrawing,
                    onUndoDrawing = onUndoDrawing,
                    onClearDrawing = onClearDrawing,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TextEditor(
    fieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onClearNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(
                onClick = { onValueChange(fieldValue.withBoldMarkers()) },
            ) {
                Icon(
                    imageVector = Icons.Filled.FormatBold,
                    contentDescription = "Bold",
                )
            }
            IconButton(
                onClick = { onValueChange(fieldValue.withBulletList()) },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = "Bullet list",
                )
            }
            IconButton(onClick = onClearNotes) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Clear notes",
                )
            }
        }
        OutlinedTextField(
            value = fieldValue,
            onValueChange = onValueChange,
            placeholder = { Text(text = "Start taking notes here...") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = 24.sp,
            ),
        )
    }
}

private val WorkspaceMode.label: String
    get() = when (this) {
        WorkspaceMode.Text -> "Text"
        WorkspaceMode.Draw -> "Draw"
        WorkspaceMode.Mixed -> "Mixed"
    }

@Composable
private fun DrawingWorkspace(
    uiState: StudentUiState,
    onDrawingToolChanged: (DrawingTool) -> Unit,
    onDrawingCanvasSizeChanged: (Int, Int) -> Unit,
    onBeginDrawing: (Float, Float) -> Unit,
    onContinueDrawing: (Float, Float) -> Unit,
    onEndDrawing: () -> Unit,
    onUndoDrawing: () -> Unit,
    onClearDrawing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DrawingToolButton(
                selected = uiState.drawingTool == DrawingTool.Pen,
                onClick = { onDrawingToolChanged(DrawingTool.Pen) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Pen",
                    )
                },
            )
            DrawingToolButton(
                selected = uiState.drawingTool == DrawingTool.Eraser,
                onClick = { onDrawingToolChanged(DrawingTool.Eraser) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Eraser",
                    )
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onUndoDrawing,
                enabled = uiState.drawingStrokes.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                )
            }
            IconButton(
                onClick = onClearDrawing,
                enabled = uiState.drawingStrokes.isNotEmpty() || uiState.activeDrawingStroke != null,
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Clear drawing",
                )
            }
        }
        DrawingCanvas(
            strokes = uiState.drawingStrokes,
            activeStroke = uiState.activeDrawingStroke,
            onDrawingCanvasSizeChanged = onDrawingCanvasSizeChanged,
            onBeginDrawing = onBeginDrawing,
            onContinueDrawing = onContinueDrawing,
            onEndDrawing = onEndDrawing,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
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
    strokes: List<DrawingStroke>,
    activeStroke: DrawingStroke?,
    onDrawingCanvasSizeChanged: (Int, Int) -> Unit,
    onBeginDrawing: (Float, Float) -> Unit,
    onContinueDrawing: (Float, Float) -> Unit,
    onEndDrawing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp),
            ),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    onDrawingCanvasSizeChanged(size.width, size.height)
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onBeginDrawing(offset.x, offset.y)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            onContinueDrawing(change.position.x, change.position.y)
                        },
                        onDragEnd = onEndDrawing,
                        onDragCancel = onEndDrawing,
                    )
                },
        ) {
            (strokes + listOfNotNull(activeStroke)).forEach { stroke ->
                drawStroke(stroke)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(stroke: DrawingStroke) {
    if (stroke.points.isEmpty()) return

    val color = if (stroke.isEraser) Color.White else Color(stroke.color)
    if (stroke.points.size == 1) {
        val point = stroke.points.first()
        drawCircle(
            color = color,
            radius = stroke.widthPx / 2f,
            center = Offset(point.x, point.y),
        )
        return
    }

    stroke.points.zipWithNext().forEach { (start, end) ->
        drawLine(
            color = color,
            start = Offset(start.x, start.y),
            end = Offset(end.x, end.y),
            strokeWidth = stroke.widthPx,
            cap = StrokeCap.Round,
        )
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

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "AI Chat",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (teacherMaterial.isBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFF7ED),
            ) {
                Text(
                    text = "Your teacher hasn't loaded today's material yet. The AI will answer general questions.",
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
                placeholder = { Text(text = "Ask a question...") },
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
                    contentDescription = "Send",
                )
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
            modifier = Modifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomEnd = if (isStudent) 4.dp else 18.dp,
                bottomStart = if (isStudent) 18.dp else 4.dp,
            ),
            color = if (isStudent) Color(0xFF6D28D9) else Color(0xFFF1F3F6),
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (isStudent) Color.White else Color(0xFF1F2937),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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
                text = "AI is typing${".".repeat(dotCount)}",
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

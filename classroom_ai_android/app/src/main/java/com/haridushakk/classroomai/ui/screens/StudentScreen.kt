package com.haridushakk.classroomai.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haridushakk.classroomai.data.ChatMessage
import com.haridushakk.classroomai.data.ChatRole
import com.haridushakk.classroomai.ui.StudentUiState
import com.haridushakk.classroomai.ui.StudentViewModel
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
                    NotebookPanel(
                        notes = uiState.notes,
                        onNotesChanged = onNotesChanged,
                        onClearNotes = onClearNotes,
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
                    NotebookPanel(
                        notes = uiState.notes,
                        onNotesChanged = onNotesChanged,
                        onClearNotes = onClearNotes,
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
private fun NotebookPanel(
    notes: String,
    onNotesChanged: (String) -> Unit,
    onClearNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(notes, selection = TextRange(notes.length)))
    }

    LaunchedEffect(notes) {
        if (notes != fieldValue.text) {
            fieldValue = TextFieldValue(notes, selection = TextRange(notes.length))
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
        ) {
            Text(
                text = "Notebook",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { updateNotes(fieldValue.withBoldMarkers()) },
            ) {
                Icon(
                    imageVector = Icons.Filled.FormatBold,
                    contentDescription = "Bold",
                )
            }
            IconButton(
                onClick = { updateNotes(fieldValue.withBulletList()) },
            ) {
                Icon(
                    imageVector = Icons.Filled.FormatListBulleted,
                    contentDescription = "Bullet list",
                )
            }
            IconButton(onClick = {
                fieldValue = TextFieldValue("")
                onClearNotes()
            }) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Clear notes",
                )
            }
        }
        OutlinedTextField(
            value = fieldValue,
            onValueChange = ::updateNotes,
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
                    imageVector = Icons.Filled.Send,
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

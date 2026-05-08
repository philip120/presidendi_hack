package com.haridushakk.classroomai.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color as AndroidColor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haridushakk.classroomai.ai.GeminiAssistant
import com.haridushakk.classroomai.data.ChatMessage
import com.haridushakk.classroomai.data.ChatRole
import com.haridushakk.classroomai.data.ClassroomRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentUiState(
    val teacherMaterial: String = "",
    val notes: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val draftMessage: String = "",
    val workspaceMode: WorkspaceMode = WorkspaceMode.Text,
    val drawingTool: DrawingTool = DrawingTool.Pen,
    val drawingStrokes: List<DrawingStroke> = emptyList(),
    val activeDrawingStroke: DrawingStroke? = null,
    val drawingCanvasWidth: Int = 0,
    val drawingCanvasHeight: Int = 0,
    val isTyping: Boolean = false,
    val errorMessage: String? = null,
)

enum class WorkspaceMode {
    Text,
    Draw,
    Mixed,
}

enum class DrawingTool {
    Pen,
    Eraser,
}

data class DrawingPoint(
    val x: Float,
    val y: Float,
)

data class DrawingStroke(
    val points: List<DrawingPoint>,
    val color: Int,
    val widthPx: Float,
    val isEraser: Boolean,
)

class StudentViewModel(
    private val repository: ClassroomRepository,
    private val geminiAssistant: GeminiAssistant,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StudentUiState())
    val uiState: StateFlow<StudentUiState> = _uiState.asStateFlow()

    private var notesSaveJob: Job? = null

    init {
        viewModelScope.launch {
            repository.teacherMaterial.collect { material ->
                _uiState.update { state ->
                    state.copy(teacherMaterial = material)
                }
            }
        }

        viewModelScope.launch {
            repository.studentNotes.collect { notes ->
                _uiState.update { state ->
                    if (state.notes == notes) state else state.copy(notes = notes)
                }
            }
        }
    }

    fun onNotesChanged(value: String) {
        _uiState.update { state ->
            state.copy(notes = value)
        }
        notesSaveJob?.cancel()
        notesSaveJob = viewModelScope.launch {
            delay(NOTES_SAVE_DEBOUNCE_MS)
            repository.saveStudentNotes(value)
        }
    }

    fun clearNotes() {
        notesSaveJob?.cancel()
        _uiState.update { state ->
            state.copy(notes = "")
        }
        viewModelScope.launch {
            repository.saveStudentNotes("")
        }
    }

    fun onDraftMessageChanged(value: String) {
        _uiState.update { state ->
            state.copy(draftMessage = value)
        }
    }

    fun onWorkspaceModeChanged(value: WorkspaceMode) {
        _uiState.update { state ->
            state.copy(workspaceMode = value)
        }
    }

    fun onDrawingToolChanged(value: DrawingTool) {
        _uiState.update { state ->
            state.copy(drawingTool = value)
        }
    }

    fun onDrawingCanvasSizeChanged(width: Int, height: Int) {
        _uiState.update { state ->
            if (state.drawingCanvasWidth == width && state.drawingCanvasHeight == height) {
                state
            } else {
                state.copy(
                    drawingCanvasWidth = width,
                    drawingCanvasHeight = height,
                )
            }
        }
    }

    fun beginDrawing(x: Float, y: Float) {
        val state = _uiState.value
        val stroke = DrawingStroke(
            points = listOf(DrawingPoint(x, y)),
            color = PEN_COLOR,
            widthPx = if (state.drawingTool == DrawingTool.Eraser) ERASER_WIDTH_PX else PEN_WIDTH_PX,
            isEraser = state.drawingTool == DrawingTool.Eraser,
        )
        _uiState.update { it.copy(activeDrawingStroke = stroke) }
    }

    fun continueDrawing(x: Float, y: Float) {
        _uiState.update { state ->
            val activeStroke = state.activeDrawingStroke ?: return@update state
            state.copy(
                activeDrawingStroke = activeStroke.copy(
                    points = activeStroke.points + DrawingPoint(x, y),
                ),
            )
        }
    }

    fun endDrawing() {
        _uiState.update { state ->
            val activeStroke = state.activeDrawingStroke ?: return@update state
            state.copy(
                drawingStrokes = state.drawingStrokes + activeStroke,
                activeDrawingStroke = null,
            )
        }
    }

    fun undoDrawing() {
        _uiState.update { state ->
            state.copy(
                drawingStrokes = state.drawingStrokes.dropLast(1),
                activeDrawingStroke = null,
            )
        }
    }

    fun clearDrawing() {
        _uiState.update { state ->
            state.copy(
                drawingStrokes = emptyList(),
                activeDrawingStroke = null,
            )
        }
    }

    fun sendMessage() {
        sendStudentMessage(includeWorkspaceImage = false)
    }

    fun askAboutWorkspace() {
        sendStudentMessage(includeWorkspaceImage = true)
    }

    private fun sendStudentMessage(includeWorkspaceImage: Boolean) {
        val state = _uiState.value
        val question = state.draftMessage.trim()
        if (state.isTyping) return
        if (question.isBlank() && !includeWorkspaceImage) return

        val finalQuestion = question.ifBlank {
            "What should I focus on in this workspace?"
        }
        val workspaceImage = if (includeWorkspaceImage) {
            renderWorkspaceBitmap(state)
        } else {
            null
        }

        val previousHistory = state.messages
        val studentMessage = ChatMessage(
            role = ChatRole.Student,
            text = if (includeWorkspaceImage) {
                "Workspace question: $finalQuestion"
            } else {
                finalQuestion
            },
        )

        _uiState.update {
            it.copy(
                messages = previousHistory + studentMessage,
                draftMessage = "",
                isTyping = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                geminiAssistant.sendStudentMessage(
                    teacherMaterial = state.teacherMaterial,
                    notebookContent = state.notes,
                    history = previousHistory,
                    newStudentMessage = finalQuestion,
                    workspaceImage = workspaceImage,
                )
            }.onSuccess { answer ->
                _uiState.update { currentState ->
                    currentState.copy(
                        messages = currentState.messages + ChatMessage(
                            role = ChatRole.Assistant,
                            text = answer.ifBlank {
                                "I could not form a useful response. Try asking that another way."
                            },
                        ),
                        isTyping = false,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isTyping = false,
                        errorMessage = throwable.message?.takeIf { it.isNotBlank() }
                            ?: "Something went wrong, please try again",
                    )
                }
            }
        }
    }

    fun onErrorShown() {
        _uiState.update { state ->
            state.copy(errorMessage = null)
        }
    }

    private fun renderWorkspaceBitmap(state: StudentUiState): Bitmap {
        val width = state.drawingCanvasWidth.takeIf { it > 0 } ?: DEFAULT_WORKSPACE_IMAGE_WIDTH
        val height = state.drawingCanvasHeight.takeIf { it > 0 } ?: DEFAULT_WORKSPACE_IMAGE_HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)

        if (state.workspaceMode != WorkspaceMode.Draw && state.notes.isNotBlank()) {
            drawWrappedText(
                canvas = canvas,
                text = state.notes,
                width = width,
            )
        }

        val allStrokes = state.activeDrawingStroke?.let { state.drawingStrokes + it }
            ?: state.drawingStrokes
        allStrokes.forEach { stroke ->
            drawStroke(canvas, stroke)
        }

        return bitmap
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        width: Int,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(31, 41, 55)
            textSize = 36f
        }
        val maxLineWidth = width - 2 * WORKSPACE_IMAGE_PADDING_PX
        var y = WORKSPACE_IMAGE_PADDING_PX + paint.textSize

        text.lineSequence().forEach { paragraph ->
            val words = paragraph.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) {
                y += TEXT_LINE_HEIGHT_PX
                return@forEach
            }

            var line = ""
            words.forEach { word ->
                val candidate = if (line.isBlank()) word else "$line $word"
                if (paint.measureText(candidate) <= maxLineWidth || line.isBlank()) {
                    line = candidate
                } else {
                    canvas.drawText(line, WORKSPACE_IMAGE_PADDING_PX, y, paint)
                    y += TEXT_LINE_HEIGHT_PX
                    line = word
                }
            }
            if (line.isNotBlank()) {
                canvas.drawText(line, WORKSPACE_IMAGE_PADDING_PX, y, paint)
                y += TEXT_LINE_HEIGHT_PX
            }
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: DrawingStroke) {
        if (stroke.points.isEmpty()) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (stroke.isEraser) AndroidColor.WHITE else stroke.color
            strokeWidth = stroke.widthPx
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        if (stroke.points.size == 1) {
            val point = stroke.points.first()
            canvas.drawPoint(point.x, point.y, paint)
            return
        }

        val path = Path().apply {
            val first = stroke.points.first()
            moveTo(first.x, first.y)
            stroke.points.drop(1).forEach { point ->
                lineTo(point.x, point.y)
            }
        }
        canvas.drawPath(path, paint)
    }

    private companion object {
        const val NOTES_SAVE_DEBOUNCE_MS = 1_000L
        const val PEN_COLOR = 0xFF111827.toInt()
        const val PEN_WIDTH_PX = 7f
        const val ERASER_WIDTH_PX = 30f
        const val DEFAULT_WORKSPACE_IMAGE_WIDTH = 1200
        const val DEFAULT_WORKSPACE_IMAGE_HEIGHT = 900
        const val WORKSPACE_IMAGE_PADDING_PX = 32f
        const val TEXT_LINE_HEIGHT_PX = 46f
    }
}

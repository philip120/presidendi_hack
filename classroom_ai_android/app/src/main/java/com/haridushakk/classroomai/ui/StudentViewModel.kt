package com.haridushakk.classroomai.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Color as AndroidColor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haridushakk.classroomai.ai.GeminiAssistant
import com.haridushakk.classroomai.data.ChatMessage
import com.haridushakk.classroomai.data.ChatRole
import com.haridushakk.classroomai.data.ClassroomRepository
import com.haridushakk.classroomai.data.ConversationExchange
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class StudentUiState(
    val teacherMaterial: String = "",
    val notes: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val draftMessage: String = "",
    val isTextBoxVisible: Boolean = false,
    val drawingTool: DrawingTool = DrawingTool.Pen,
    val drawingStrokes: List<DrawingStroke> = emptyList(),
    val activeDrawingStroke: DrawingStroke? = null,
    val highlightSelection: HighlightSelection? = null,
    val activeHighlightSelection: HighlightSelection? = null,
    val drawingCanvasWidth: Int = 0,
    val drawingCanvasHeight: Int = 0,
    val viewportOffsetX: Float = 0f,
    val viewportOffsetY: Float = 0f,
    val viewportScale: Float = 1f,
    val isTyping: Boolean = false,
    val errorMessage: String? = null,
)

enum class DrawingTool {
    Pen,
    Eraser,
    Select,
    Pan,
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

data class HighlightSelection(
    val points: List<DrawingPoint>,
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

    fun toggleTextBox() {
        _uiState.update { state ->
            state.copy(isTextBoxVisible = !state.isTextBoxVisible)
        }
    }

    fun onDrawingToolChanged(value: DrawingTool) {
        _uiState.update { state ->
            state.copy(drawingTool = value)
        }
    }

    fun zoomIn() {
        zoomBy(ZOOM_STEP)
    }

    fun zoomOut() {
        zoomBy(1f / ZOOM_STEP)
    }

    fun resetViewport() {
        _uiState.update { state ->
            state.copy(
                viewportOffsetX = 0f,
                viewportOffsetY = 0f,
                viewportScale = 1f,
            )
        }
    }

    private fun zoomBy(multiplier: Float) {
        _uiState.update { state ->
            val nextScale = (state.viewportScale * multiplier)
                .coerceIn(MIN_VIEWPORT_SCALE, MAX_VIEWPORT_SCALE)
            state.copy(viewportScale = nextScale)
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
        if (state.drawingTool == DrawingTool.Pan) return

        val worldPoint = state.toWorldPoint(x, y)
        if (state.drawingTool == DrawingTool.Select) {
            _uiState.update {
                it.copy(activeHighlightSelection = HighlightSelection(points = listOf(worldPoint)))
            }
            return
        }

        val stroke = DrawingStroke(
            points = listOf(worldPoint),
            color = PEN_COLOR,
            widthPx = if (state.drawingTool == DrawingTool.Eraser) {
                ERASER_WIDTH_PX
            } else {
                PEN_WIDTH_PX
            },
            isEraser = state.drawingTool == DrawingTool.Eraser,
        )
        _uiState.update { it.copy(activeDrawingStroke = stroke) }
    }

    fun continueDrawing(x: Float, y: Float) {
        _uiState.update { state ->
            if (state.drawingTool == DrawingTool.Pan) return@update state
            val worldPoint = state.toWorldPoint(x, y)

            if (state.drawingTool == DrawingTool.Select) {
                val activeSelection = state.activeHighlightSelection ?: return@update state
                return@update state.copy(
                    activeHighlightSelection = activeSelection.copy(
                        points = activeSelection.points + worldPoint,
                    ),
                )
            }

            val activeStroke = state.activeDrawingStroke ?: return@update state
            state.copy(
                activeDrawingStroke = activeStroke.copy(
                    points = activeStroke.points + worldPoint,
                ),
            )
        }
    }

    fun panWorkspace(deltaX: Float, deltaY: Float) {
        _uiState.update { state ->
            state.copy(
                viewportOffsetX = state.viewportOffsetX + deltaX / state.viewportScale,
                viewportOffsetY = state.viewportOffsetY + deltaY / state.viewportScale,
            )
        }
    }

    fun endDrawing() {
        _uiState.update { state ->
            state.activeHighlightSelection?.let { activeSelection ->
                return@update state.copy(
                    highlightSelection = activeSelection.takeIf { it.hasUsableArea() }
                        ?: state.highlightSelection,
                    activeHighlightSelection = null,
                )
            }

            val activeStroke = state.activeDrawingStroke ?: return@update state
            state.copy(
                drawingStrokes = state.drawingStrokes + activeStroke,
                activeDrawingStroke = null,
            )
        }
    }

    fun undoDrawing() {
        _uiState.update { state ->
            if (state.activeHighlightSelection != null || state.highlightSelection != null) {
                return@update state.copy(
                    highlightSelection = null,
                    activeHighlightSelection = null,
                )
            }

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
                highlightSelection = null,
                activeHighlightSelection = null,
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
            "Millele peaksin selles tööalas tähelepanu pöörama?"
        }
        val workspaceImage = if (includeWorkspaceImage) {
            renderWorkspaceBitmap(state)
        } else {
            null
        }
        val hasHighlight = includeWorkspaceImage && state.highlightSelection != null
        val modelQuestion = if (hasHighlight) {
            "$finalQuestion\n\nLisatud tööala pilt sisaldab ainult vabakäega valitud osa tööalast. Keskendu ainult sellele valitud piirkonnale."
        } else {
            finalQuestion
        }
        val notebookContent = if (hasHighlight) "" else state.notes

        val previousHistory = state.messages
        val studentMessage = ChatMessage(
            role = ChatRole.Student,
            text = if (includeWorkspaceImage) {
                if (hasHighlight) {
                    "Valitud tööala küsimus: $finalQuestion"
                } else {
                    "Tööala küsimus: $finalQuestion"
                }
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
                    notebookContent = notebookContent,
                    history = previousHistory,
                    newStudentMessage = modelQuestion,
                    workspaceImage = workspaceImage,
                )
            }.onSuccess { answer ->
                val answerText = answer.ifBlank {
                    "Ma ei saanud kasulikku vastust koostada. Proovi küsida teise sõnastusega."
                }
                _uiState.update { currentState ->
                    currentState.copy(
                        messages = currentState.messages + ChatMessage(
                            role = ChatRole.Assistant,
                            text = answerText,
                        ),
                        isTyping = false,
                    )
                }
                repository.saveConversationExchange(
                    ConversationExchange(
                        id = studentMessage.id,
                        question = finalQuestion,
                        answer = answerText,
                        askedAtMillis = System.currentTimeMillis(),
                        includedWorkspace = includeWorkspaceImage,
                        usedHighlight = hasHighlight,
                    ),
                )
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isTyping = false,
                        errorMessage = throwable.message?.takeIf { it.isNotBlank() }
                            ?: "Midagi läks valesti. Palun proovi uuesti.",
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
        val bitmap = renderFullWorkspaceBitmap(state)
        val highlightSelection = state.highlightSelection ?: return bitmap
        return cropWorkspaceToHighlight(bitmap, state, highlightSelection)
    }

    private fun renderFullWorkspaceBitmap(state: StudentUiState): Bitmap {
        val width = state.drawingCanvasWidth.takeIf { it > 0 } ?: DEFAULT_WORKSPACE_IMAGE_WIDTH
        val height = state.drawingCanvasHeight.takeIf { it > 0 } ?: DEFAULT_WORKSPACE_IMAGE_HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)
        canvas.scale(state.viewportScale, state.viewportScale)
        canvas.translate(state.viewportOffsetX, state.viewportOffsetY)

        if (state.notes.isNotBlank()) {
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

    private fun cropWorkspaceToHighlight(
        source: Bitmap,
        state: StudentUiState,
        highlightSelection: HighlightSelection,
    ): Bitmap {
        val bounds = highlightSelection.toScreenBounds(state)
        val left = floor(bounds.left - HIGHLIGHT_IMAGE_PADDING_PX)
            .toInt()
            .coerceIn(0, source.width - 1)
        val top = floor(bounds.top - HIGHLIGHT_IMAGE_PADDING_PX)
            .toInt()
            .coerceIn(0, source.height - 1)
        val right = ceil(bounds.right + HIGHLIGHT_IMAGE_PADDING_PX)
            .toInt()
            .coerceIn(left + 1, source.width)
        val bottom = ceil(bounds.bottom + HIGHLIGHT_IMAGE_PADDING_PX)
            .toInt()
            .coerceIn(top + 1, source.height)

        val output = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(AndroidColor.WHITE)

        val selectionPath = highlightSelection.toScreenPath(
            state = state,
            offsetX = -left.toFloat(),
            offsetY = -top.toFloat(),
        )
        canvas.save()
        canvas.clipPath(selectionPath)
        canvas.drawBitmap(source, -left.toFloat(), -top.toFloat(), null)
        canvas.restore()

        return output
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

    private fun StudentUiState.toWorldPoint(x: Float, y: Float): DrawingPoint {
        return DrawingPoint(
            x = x / viewportScale - viewportOffsetX,
            y = y / viewportScale - viewportOffsetY,
        )
    }

    private fun HighlightSelection.hasUsableArea(): Boolean {
        val bounds = toWorldBounds()
        return points.size >= MIN_HIGHLIGHT_POINT_COUNT &&
            bounds.width() >= MIN_HIGHLIGHT_SIZE_PX &&
            bounds.height() >= MIN_HIGHLIGHT_SIZE_PX
    }

    private fun HighlightSelection.toScreenBounds(state: StudentUiState): RectF {
        val worldBounds = toWorldBounds()
        return RectF(
            (worldBounds.left + state.viewportOffsetX) * state.viewportScale,
            (worldBounds.top + state.viewportOffsetY) * state.viewportScale,
            (worldBounds.right + state.viewportOffsetX) * state.viewportScale,
            (worldBounds.bottom + state.viewportOffsetY) * state.viewportScale,
        )
    }

    private fun HighlightSelection.toScreenPath(
        state: StudentUiState,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
    ): Path {
        return Path().apply {
            val first = points.firstOrNull() ?: return@apply
            moveTo(
                (first.x + state.viewportOffsetX) * state.viewportScale + offsetX,
                (first.y + state.viewportOffsetY) * state.viewportScale + offsetY,
            )
            points.drop(1).forEach { point ->
                lineTo(
                    (point.x + state.viewportOffsetX) * state.viewportScale + offsetX,
                    (point.y + state.viewportOffsetY) * state.viewportScale + offsetY,
                )
            }
            close()
        }
    }

    private fun HighlightSelection.toWorldBounds(): RectF {
        val first = points.firstOrNull() ?: return RectF()
        var left = first.x
        var top = first.y
        var right = first.x
        var bottom = first.y
        points.drop(1).forEach { point ->
            left = min(left, point.x)
            top = min(top, point.y)
            right = max(right, point.x)
            bottom = max(bottom, point.y)
        }
        return RectF(
            left,
            top,
            right,
            bottom,
        )
    }

    private companion object {
        const val NOTES_SAVE_DEBOUNCE_MS = 1_000L
        const val PEN_COLOR = 0xFF111827.toInt()
        const val PEN_WIDTH_PX = 7f
        const val ERASER_WIDTH_PX = 30f
        const val MIN_HIGHLIGHT_POINT_COUNT = 3
        const val MIN_HIGHLIGHT_SIZE_PX = 16f
        const val HIGHLIGHT_IMAGE_PADDING_PX = 12f
        const val DEFAULT_WORKSPACE_IMAGE_WIDTH = 1200
        const val DEFAULT_WORKSPACE_IMAGE_HEIGHT = 900
        const val WORKSPACE_IMAGE_PADDING_PX = 32f
        const val TEXT_LINE_HEIGHT_PX = 46f
        const val MIN_VIEWPORT_SCALE = 0.5f
        const val MAX_VIEWPORT_SCALE = 3f
        const val ZOOM_STEP = 1.25f
    }
}

package com.haridushakk.classroomai.ui

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
    val isTyping: Boolean = false,
    val errorMessage: String? = null,
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

    fun sendMessage() {
        val state = _uiState.value
        val question = state.draftMessage.trim()
        if (question.isBlank() || state.isTyping) return

        val previousHistory = state.messages
        val studentMessage = ChatMessage(
            role = ChatRole.Student,
            text = question,
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
                    newStudentMessage = question,
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
            }.onFailure {
                _uiState.update { currentState ->
                    currentState.copy(
                        isTyping = false,
                        errorMessage = "Something went wrong, please try again",
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

    private companion object {
        const val NOTES_SAVE_DEBOUNCE_MS = 1_000L
    }
}

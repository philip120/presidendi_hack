package com.haridushakk.classroomai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haridushakk.classroomai.data.ClassroomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeacherUiState(
    val materialInput: String = "",
    val savedMaterial: String = "",
    val showSavedConfirmation: Boolean = false,
)

class TeacherViewModel(
    private val repository: ClassroomRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TeacherUiState())
    val uiState: StateFlow<TeacherUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.teacherMaterial.collect { material ->
                _uiState.update { state ->
                    state.copy(savedMaterial = material)
                }
            }
        }
    }

    fun onMaterialInputChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                materialInput = value,
                showSavedConfirmation = false,
            )
        }
    }

    fun saveMaterial() {
        val material = _uiState.value.materialInput
        viewModelScope.launch {
            repository.saveTeacherMaterial(material)
            _uiState.update { state ->
                state.copy(showSavedConfirmation = true)
            }
        }
    }

    fun clearMaterial() {
        viewModelScope.launch {
            repository.clearTeacherMaterial()
            _uiState.update { state ->
                state.copy(
                    materialInput = "",
                    showSavedConfirmation = false,
                )
            }
        }
    }
}

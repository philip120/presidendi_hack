package com.haridushakk.classroomai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.haridushakk.classroomai.ai.GeminiAssistant
import com.haridushakk.classroomai.data.ClassroomRepository

class TeacherViewModelFactory(
    private val repository: ClassroomRepository,
    private val geminiAssistant: GeminiAssistant,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TeacherViewModel::class.java)) {
            return TeacherViewModel(repository, geminiAssistant) as T
        }
        error("Tundmatu ViewModeli klass: ${modelClass.name}")
    }
}

class StudentViewModelFactory(
    private val repository: ClassroomRepository,
    private val geminiAssistant: GeminiAssistant,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudentViewModel::class.java)) {
            return StudentViewModel(repository, geminiAssistant) as T
        }
        error("Tundmatu ViewModeli klass: ${modelClass.name}")
    }
}

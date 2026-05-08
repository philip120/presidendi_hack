package com.haridushakk.classroomai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val Context.classroomDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "classroom_ai",
)

class ClassroomRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val teacherMaterial: Flow<String> = dataStore.data
        .map { preferences -> preferences[Keys.TeacherMaterial].orEmpty() }
        .distinctUntilChanged()

    val studentNotes: Flow<String> = dataStore.data
        .map { preferences -> preferences[Keys.StudentNotes].orEmpty() }
        .distinctUntilChanged()

    suspend fun saveTeacherMaterial(material: String) {
        dataStore.edit { preferences ->
            preferences[Keys.TeacherMaterial] = material.trim()
        }
    }

    suspend fun clearTeacherMaterial() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.TeacherMaterial)
        }
    }

    suspend fun saveStudentNotes(notes: String) {
        dataStore.edit { preferences ->
            preferences[Keys.StudentNotes] = notes
        }
    }

    private object Keys {
        val TeacherMaterial = stringPreferencesKey("teacher_material")
        val StudentNotes = stringPreferencesKey("student_notes")
    }
}

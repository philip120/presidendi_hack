package com.haridushakk.classroomai.data

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

val Context.classroomDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "classroom_ai",
)

class ClassroomRepository(
    private val dataStore: DataStore<Preferences>,
    private val filesDir: File,
) {
    val teacherMaterial: Flow<String> = dataStore.data
        .map { preferences -> preferences[Keys.TeacherMaterial].orEmpty() }
        .distinctUntilChanged()

    val studentNotes: Flow<String> = dataStore.data
        .map { preferences -> preferences[Keys.StudentNotes].orEmpty() }
        .distinctUntilChanged()

    val conversationExchanges: Flow<List<ConversationExchange>> = dataStore.data
        .map { preferences ->
            preferences[Keys.ConversationExchanges]
                .orEmpty()
                .toConversationExchanges()
        }
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

    suspend fun saveConversationExchange(exchange: ConversationExchange) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.ConversationExchanges]
                .orEmpty()
                .toConversationExchanges()
            val next = (current + exchange).takeLast(MAX_SAVED_EXCHANGES)
            preferences[Keys.ConversationExchanges] = next.toJson()
            deleteConversationImagesExcept(next.mapNotNull { it.modelImagePath }.toSet())
        }
    }

    suspend fun clearConversationExchanges() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.ConversationExchanges)
        }
        clearConversationImages()
    }

    suspend fun saveConversationImage(
        exchangeId: Long,
        bitmap: Bitmap,
    ): String = withContext(Dispatchers.IO) {
        val imageDirectory = conversationImageDirectory()
        if (!imageDirectory.exists()) {
            imageDirectory.mkdirs()
        }
        val imageFile = File(imageDirectory, "conversation_$exchangeId.jpg")
        imageFile.outputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, outputStream)
        }
        imageFile.absolutePath
    }

    private fun String.toConversationExchanges(): List<ConversationExchange> {
        if (isBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(this)
            buildList {
                repeat(json.length()) { index ->
                    val item = json.optJSONObject(index) ?: return@repeat
                    val question = item.optString(JsonKeys.Question).trim()
                    val answer = item.optString(JsonKeys.Answer).trim()
                    if (question.isBlank() && answer.isBlank()) return@repeat
                    add(
                        ConversationExchange(
                            id = item.optLong(JsonKeys.Id),
                            question = question,
                            modelInput = item.optString(JsonKeys.ModelInput)
                                .trim()
                                .ifBlank { question },
                            modelImagePath = item.optString(JsonKeys.ModelImagePath)
                                .trim()
                                .ifBlank { null },
                            answer = answer,
                            askedAtMillis = item.optLong(JsonKeys.AskedAtMillis),
                            includedWorkspace = item.optBoolean(JsonKeys.IncludedWorkspace),
                            usedHighlight = item.optBoolean(JsonKeys.UsedHighlight),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun List<ConversationExchange>.toJson(): String {
        val json = JSONArray()
        forEach { exchange ->
            json.put(
                JSONObject()
                    .put(JsonKeys.Id, exchange.id)
                    .put(JsonKeys.Question, exchange.question)
                    .put(JsonKeys.ModelInput, exchange.modelInput)
                    .put(JsonKeys.ModelImagePath, exchange.modelImagePath.orEmpty())
                    .put(JsonKeys.Answer, exchange.answer)
                    .put(JsonKeys.AskedAtMillis, exchange.askedAtMillis)
                    .put(JsonKeys.IncludedWorkspace, exchange.includedWorkspace)
                    .put(JsonKeys.UsedHighlight, exchange.usedHighlight),
            )
        }
        return json.toString()
    }

    private object Keys {
        val TeacherMaterial = stringPreferencesKey("teacher_material")
        val StudentNotes = stringPreferencesKey("student_notes")
        val ConversationExchanges = stringPreferencesKey("conversation_exchanges")
    }

    private object JsonKeys {
        const val Id = "id"
        const val Question = "question"
        const val ModelInput = "modelInput"
        const val ModelImagePath = "modelImagePath"
        const val Answer = "answer"
        const val AskedAtMillis = "askedAtMillis"
        const val IncludedWorkspace = "includedWorkspace"
        const val UsedHighlight = "usedHighlight"
    }

    private fun conversationImageDirectory(): File {
        return File(filesDir, "conversation_images")
    }

    private fun clearConversationImages() {
        conversationImageDirectory().listFiles()?.forEach { file ->
            file.delete()
        }
    }

    private fun deleteConversationImagesExcept(keptPaths: Set<String>) {
        conversationImageDirectory().listFiles()?.forEach { file ->
            if (file.absolutePath !in keptPaths) {
                file.delete()
            }
        }
    }

    private companion object {
        const val MAX_SAVED_EXCHANGES = 300
        const val IMAGE_QUALITY = 82
    }
}

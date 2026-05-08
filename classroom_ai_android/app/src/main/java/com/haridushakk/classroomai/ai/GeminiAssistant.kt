package com.haridushakk.classroomai.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.haridushakk.classroomai.data.ChatMessage
import com.haridushakk.classroomai.data.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiAssistant(
    private val apiKey: String,
    private val modelName: String,
) {
    suspend fun sendStudentMessage(
        teacherMaterial: String,
        notebookContent: String,
        history: List<ChatMessage>,
        newStudentMessage: String,
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            error("Set GEMINI_API_KEY in local.properties before calling Gemini.")
        }

        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            systemInstruction = content {
                text(buildSystemPrompt(teacherMaterial, notebookContent))
            },
        )

        val chat = generativeModel.startChat(
            history = history.map { message ->
                content(role = message.geminiRole) {
                    text(message.text)
                }
            },
        )

        chat.sendMessage(newStudentMessage).text?.trim().orEmpty()
    }

    private val ChatMessage.geminiRole: String
        get() = when (role) {
            ChatRole.Student -> "user"
            ChatRole.Assistant -> "model"
        }

    private fun buildSystemPrompt(
        teacherMaterial: String,
        notebookContent: String,
    ): String {
        val materialPrompt = if (teacherMaterial.isBlank()) {
            """
            You are a helpful classroom AI assistant. No teacher material has been loaded for today's lesson yet.
            Answer general student questions in simple terms, but never complete assignments on the student's behalf.
            Guide students with hints, ask clarifying questions, and avoid writing the student's notes for them.
            """.trimIndent()
        } else {
            """
            You are a helpful classroom AI assistant. Your role is to help students understand
            the current lesson - but never give away direct answers. Instead, guide them with
            hints, ask clarifying questions, and explain concepts in simple terms.
            Always base your answers on the following course material:

            $teacherMaterial

            If a student asks something outside this material, say:
            "That seems outside today's lesson - let's focus on what we're covering today."
            Do not write the student's notes for them. Do not complete assignments on their behalf.
            """.trimIndent()
        }

        return """
            $materialPrompt

            The student's current notes read as follows - use this as context for their questions
            but do not rewrite or complete their notes:
            ${notebookContent.ifBlank { "(empty)" }}
        """.trimIndent()
    }
}

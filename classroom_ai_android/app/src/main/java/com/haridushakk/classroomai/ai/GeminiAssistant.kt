package com.haridushakk.classroomai.ai

import android.graphics.Bitmap
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
        workspaceImage: Bitmap? = null,
    ): String = withContext(Dispatchers.IO) {
        val tutorInput = buildTutorInput(
            teacherMaterial = teacherMaterial,
            notebookContent = notebookContent,
            history = history,
            newStudentMessage = newStudentMessage,
            hasWorkspaceImage = workspaceImage != null,
        )

        MathGuard.buildControllerGuardResponse(tutorInput)?.let { guardedResponse ->
            return@withContext guardedResponse
        }

        if (apiKey.isBlank()) {
            error("Set GEMINI_API_KEY in local.properties before calling Gemini.")
        }

        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            systemInstruction = content {
                text(TutorPromptBuilder.TUTOR_SYSTEM_PROMPT)
            },
        )
        val prompt = TutorPromptBuilder.buildTutorPrompt(tutorInput)
        val promptContent = content {
            workspaceImage?.let { image(it) }
            text(prompt)
        }

        generativeModel.generateContent(promptContent).text?.trim().orEmpty()
    }

    private fun buildTutorInput(
        teacherMaterial: String,
        notebookContent: String,
        history: List<ChatMessage>,
        newStudentMessage: String,
        hasWorkspaceImage: Boolean,
    ): TutorInput {
        val teacherContext = if (teacherMaterial.isBlank()) {
            "No teacher material has been loaded for today's lesson yet. " +
                "Answer from the student's current work and question, but do not complete assignments on the student's behalf."
        } else {
            "Always base your answers on the following course material:\n\n$teacherMaterial\n\n" +
                "If a student asks something outside this material, say: " +
                "\"That seems outside today's lesson - let's focus on what we're covering today.\""
        }

        val problemText = listOf(
            notebookContent.trim(),
            newStudentMessage.trim(),
        ).filter { it.isNotBlank() }
            .joinToString(separator = "\n")

        return TutorInput(
            question = newStudentMessage,
            problemText = problemText,
            hasWorkspaceImage = hasWorkspaceImage,
            teacherContext = teacherContext,
            instructionStyle = DEFAULT_INSTRUCTION_STYLE,
            studentState = DEFAULT_STUDENT_STATE,
            recentExchanges = history.toRecentExchanges(maxExchanges = 3),
        )
    }

    private fun List<ChatMessage>.toRecentExchanges(maxExchanges: Int): List<RecentExchange> {
        val exchanges = mutableListOf<RecentExchange>()
        var pendingStudentMessage: String? = null

        forEach { message ->
            when (message.role) {
                ChatRole.Student -> pendingStudentMessage = message.text
                ChatRole.Assistant -> {
                    val studentMessage = pendingStudentMessage
                    if (!studentMessage.isNullOrBlank() || message.text.isNotBlank()) {
                        exchanges += RecentExchange(
                            student = studentMessage.orEmpty(),
                            assistant = message.text,
                        )
                    }
                    pendingStudentMessage = null
                }
            }
        }

        return exchanges.takeLast(maxExchanges)
    }

    private companion object {
        const val DEFAULT_INSTRUCTION_STYLE =
            "Use Socratic questioning. Do not give the final answer immediately unless the " +
                "student has already shown the key step. Give one hint at a time. Use the same " +
                "notation used in class. Do not loop: once the student gives the right operation " +
                "or a correct answer, confirm it directly and explain why."

        const val DEFAULT_STUDENT_STATE =
            "Use only this session's notes and recent messages to infer the student's current understanding. " +
                "Do not assume a stable profile beyond the current classroom session."
    }
}

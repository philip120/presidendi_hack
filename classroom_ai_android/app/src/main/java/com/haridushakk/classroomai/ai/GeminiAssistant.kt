package com.haridushakk.classroomai.ai

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.haridushakk.classroomai.data.ChatMessage
import com.haridushakk.classroomai.data.ChatRole
import com.haridushakk.classroomai.data.ConversationExchange
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
            error("Määra GEMINI_API_KEY failis local.properties enne Gemini kasutamist.")
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

    suspend fun summarizeTeacherConversations(
        conversations: List<ConversationExchange>,
    ): String = withContext(Dispatchers.IO) {
        if (conversations.isEmpty()) {
            return@withContext "Õpilaste vestlusi ei ole veel salvestatud."
        }

        if (apiKey.isBlank()) {
            error("Määra GEMINI_API_KEY failis local.properties enne Gemini kasutamist.")
        }

        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            systemInstruction = content {
                text(
                    "Oled õpetaja analüüsi assistent. Koosta ainult õpetajale mõeldud lühike, " +
                        "selge ja tegevustele suunatud kokkuvõte eesti keeles.",
                )
            },
        )
        val prompt = buildTeacherSummaryPrompt(conversations)
        generativeModel.generateContent(
            content {
                text(prompt)
            },
        ).text?.trim().orEmpty()
    }

    private fun buildTutorInput(
        teacherMaterial: String,
        notebookContent: String,
        history: List<ChatMessage>,
        newStudentMessage: String,
        hasWorkspaceImage: Boolean,
    ): TutorInput {
        val teacherContext = if (teacherMaterial.isBlank()) {
            "Õpetaja ei ole tänase tunni materjali veel lisanud. " +
                "Vasta õpilase praeguse töö ja küsimuse põhjal, aga ära lahenda ülesandeid õpilase eest lõpuni."
        } else {
            "Lähtuda tuleb alati järgmisest õppematerjalist:\n\n$teacherMaterial\n\n" +
                "Kui õpilane küsib midagi väljaspool seda materjali, ütle: " +
                "\"See paistab olevat väljaspool tänast tundi. Keskendume sellele, mida täna õpime.\""
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

    private fun buildTeacherSummaryPrompt(conversations: List<ConversationExchange>): String {
        val transcript = conversations
            .sortedBy { it.askedAtMillis }
            .joinToString(separator = "\n\n") { exchange ->
                """
                Vestlus ${exchange.id}
                Õpilase küsimus: ${exchange.question.clipForTeacherSummary(700)}
                Mudelile saadetud: ${exchange.modelInput.clipForTeacherSummary(1200)}
                AI vastus: ${exchange.answer.clipForTeacherSummary(900)}
                Tööala pilt: ${if (exchange.includedWorkspace) "jah" else "ei"}
                Vabakäe valik: ${if (exchange.usedHighlight) "jah" else "ei"}
                """.trimIndent()
            }

        return """
            Koosta õpetajale väga loetav ja lühike insight-kokkuvõte kõigi allolevate õpilasvestluste põhjal.

            Eesmärk ei ole loetleda täpseid küsimusi. Eesmärk on anda õpetajale 3-4 üldist ja kasulikku märksõna
            õpilaste käitumise ja arusaamise kohta, näiteks "ei saa üldse aru", "küsib lisaküsimusi",
            "tahab kohe vastust", "kontrollib lahendust".

            Kasuta seda vormi:
            **Peamised signaalid**
            - **Märksõna:** üks lühike selgitav lause.
            - **Märksõna:** üks lühike selgitav lause.
            - **Märksõna:** üks lühike selgitav lause.

            **Õpetaja järgmine samm**
            Üks konkreetne soovitus järgmise tunni alustamiseks.

            Reeglid:
            - Ära tee pikka esseed ega nelja suurt peatükki.
            - Kasuta 3 kuni 4 märksõna.
            - Keskendu käitumuslikele mustritele ja õppimisraskustele, mitte toorele sõnasagedusele.
            - Arvesta ka sellega, mida mudelile tegelikult saadeti.
            - Kui kasutad matemaatikat, kirjuta see LaTeXina.
            - Vasta ainult eesti keeles.

            <vestlused>
            $transcript
            </vestlused>
        """.trimIndent()
    }

    private fun String.clipForTeacherSummary(maxLength: Int): String {
        val normalized = trim()
        if (normalized.length <= maxLength) return normalized
        return normalized.take(maxLength).trimEnd() + "\n...(lühendatud)"
    }

    private companion object {
        const val DEFAULT_INSTRUCTION_STYLE =
            "Kasuta sokraatilisi suunavaid küsimusi. Ära anna lõppvastust kohe, välja arvatud juhul, " +
                "kui õpilane on juba näidanud peamist sammu. Anna üks vihje korraga. Kasuta sama tähistust, " +
                "mida kasutatakse tunnis. Ära jää kordama: kui õpilane annab õige tehte või õige vastuse, " +
                "kinnita seda otse ja selgita lühidalt, miks see on õige. Vasta alati eesti keeles."

        const val DEFAULT_STUDENT_STATE =
            "Õpilase praeguse arusaamise hindamiseks kasuta ainult selle sessiooni märkmeid ja viimaseid sõnumeid. " +
                "Ära eelda püsivat õpilasprofiili väljaspool käimasolevat klassisessiooni."
    }
}

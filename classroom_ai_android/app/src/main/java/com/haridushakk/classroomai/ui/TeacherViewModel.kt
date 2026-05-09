package com.haridushakk.classroomai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haridushakk.classroomai.ai.GeminiAssistant
import com.haridushakk.classroomai.data.ClassroomRepository
import com.haridushakk.classroomai.data.ConversationExchange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class TeacherUiState(
    val materialInput: String = "",
    val savedMaterial: String = "",
    val showSavedConfirmation: Boolean = false,
    val materialImportMessage: String? = null,
    val conversations: List<ConversationExchange> = emptyList(),
    val dashboard: TeacherDashboard = TeacherDashboard(),
    val generatedSummary: String = "",
    val isGeneratingSummary: Boolean = false,
    val summaryError: String? = null,
)

data class TeacherDashboard(
    val totalQuestions: Int = 0,
    val workspaceQuestions: Int = 0,
    val highlightedQuestions: Int = 0,
    val uniqueQuestions: Int = 0,
    val commonQuestions: List<QuestionInsight> = emptyList(),
    val commonTopics: List<TopicInsight> = emptyList(),
    val recentConversations: List<ConversationExchange> = emptyList(),
    val behaviorInsights: List<BehaviorInsight> = emptyList(),
)

data class QuestionInsight(
    val question: String,
    val count: Int,
)

data class TopicInsight(
    val topic: String,
    val count: Int,
)

data class BehaviorInsight(
    val label: String,
    val description: String,
    val count: Int,
)

class TeacherViewModel(
    private val repository: ClassroomRepository,
    private val geminiAssistant: GeminiAssistant,
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

        viewModelScope.launch {
            repository.conversationExchanges.collect { conversations ->
                _uiState.update { state ->
                    state.copy(
                        conversations = conversations,
                        dashboard = conversations.toDashboard(),
                        generatedSummary = state.generatedSummary.takeIf { conversations.isNotEmpty() }.orEmpty(),
                    )
                }
            }
        }
    }

    fun onMaterialInputChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                materialInput = value,
                showSavedConfirmation = false,
                materialImportMessage = null,
            )
        }
    }

    fun onMaterialImported(value: String) {
        _uiState.update { state ->
            val material = value.trim()
            if (material.isBlank()) {
                state.copy(
                    showSavedConfirmation = false,
                    materialImportMessage = "Valitud fail ei sisaldanud loetavat teksti.",
                )
            } else {
                state.copy(
                    materialInput = material,
                    showSavedConfirmation = false,
                    materialImportMessage = "Imporditi ${material.length} tähemärki. Vaata üle ja salvesta, kui materjal on valmis.",
                )
            }
        }
    }

    fun onMaterialImportFailed() {
        _uiState.update { state ->
            state.copy(
                showSavedConfirmation = false,
                materialImportMessage = "Faili ei õnnestunud importida. Proovi lihttekstifaili.",
            )
        }
    }

    fun saveMaterial() {
        val material = _uiState.value.materialInput
        viewModelScope.launch {
            repository.saveTeacherMaterial(material)
            _uiState.update { state ->
                state.copy(
                    showSavedConfirmation = true,
                    materialImportMessage = null,
                )
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
                    materialImportMessage = null,
                )
            }
        }
    }

    fun generateConversationSummary() {
        val conversations = _uiState.value.conversations
        if (conversations.isEmpty()) {
            _uiState.update { state ->
                state.copy(
                    generatedSummary = "Õpilaste vestlusi ei ole veel salvestatud.",
                    summaryError = null,
                )
            }
            return
        }

        _uiState.update { state ->
            state.copy(
                isGeneratingSummary = true,
                summaryError = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                geminiAssistant.summarizeTeacherConversations(conversations)
            }.onSuccess { summary ->
                _uiState.update { state ->
                    state.copy(
                        generatedSummary = summary.ifBlank { conversations.toTeacherSummary() },
                        isGeneratingSummary = false,
                        summaryError = null,
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        generatedSummary = conversations.toTeacherSummary(),
                        isGeneratingSummary = false,
                        summaryError = "AI kokkuvõtet ei saanud luua. Kuvan kohaliku kokkuvõtte.",
                    )
                }
            }
        }
    }

    fun clearConversationHistory() {
        viewModelScope.launch {
            repository.clearConversationExchanges()
            _uiState.update { state ->
                state.copy(
                    generatedSummary = "",
                    isGeneratingSummary = false,
                    summaryError = null,
                )
            }
        }
    }

    private fun List<ConversationExchange>.toDashboard(): TeacherDashboard {
        if (isEmpty()) return TeacherDashboard()

        val questionGroups = groupBy { it.question.normalizedQuestion() }
            .filterKeys { it.isNotBlank() }
            .values
            .map { exchanges ->
                QuestionInsight(
                    question = exchanges.maxBy { it.askedAtMillis }.question,
                    count = exchanges.size,
                )
            }
            .sortedWith(
                compareByDescending<QuestionInsight> { it.count }
                    .thenBy { it.question.lowercase() },
            )

        val topics = flatMap { it.question.topicWords() }
            .groupingBy { it }
            .eachCount()
            .entries
            .map { (topic, count) -> TopicInsight(topic = topic, count = count) }
            .sortedWith(
                compareByDescending<TopicInsight> { it.count }
                    .thenBy { it.topic },
            )

        return TeacherDashboard(
            totalQuestions = size,
            workspaceQuestions = count { it.includedWorkspace },
            highlightedQuestions = count { it.usedHighlight },
            uniqueQuestions = questionGroups.size,
            commonQuestions = questionGroups.take(5),
            commonTopics = topics.take(8),
            recentConversations = sortedByDescending { it.askedAtMillis }.take(6),
            behaviorInsights = toBehaviorInsights(),
        )
    }

    private fun List<ConversationExchange>.toTeacherSummary(): String {
        if (isEmpty()) {
            return "Õpilaste vestlusi ei ole veel salvestatud."
        }

        val dashboard = toDashboard()
        val behaviorLines = dashboard.behaviorInsights
            .joinToString(separator = "\n") { "- ${it.label}: ${it.count}×. ${it.description}" }
            .ifBlank { "- Selget korduvat mustrit pole veel piisavalt." }
        val workspaceShare = ((dashboard.workspaceQuestions.toFloat() / dashboard.totalQuestions) * 100f)
            .roundToInt()
        val highlightShare = ((dashboard.highlightedQuestions.toFloat() / dashboard.totalQuestions) * 100f)
            .roundToInt()

        return buildString {
            appendLine("Kokkuvõte põhineb ${dashboard.totalQuestions} salvestatud õpilasküsimusel.")
            appendLine()
            appendLine("Peamised signaalid:")
            appendLine(behaviorLines)
            appendLine()
            appendLine("Tööala kasutus: ${dashboard.workspaceQuestions} küsimust kasutas tööala pilti ($workspaceShare%).")
            appendLine("Fookustatud valikud: ${dashboard.highlightedQuestions} küsimust kasutas valitud piirkonda ($highlightShare%).")
            appendLine()
            append("Soovitus õpetajale: alusta järgmine tund lühikese esimese sammu näitega ja lase õpilastel põhjendada, miks just see samm valiti.")
        }.trim()
    }

    private fun List<ConversationExchange>.toBehaviorInsights(): List<BehaviorInsight> {
        val counts = InsightDefinitions.associateWith { 0 }.toMutableMap()
        forEach { exchange ->
            val matched = InsightDefinitions.filter { definition ->
                definition.matches(exchange)
            }
            if (matched.isEmpty()) {
                counts[DefaultInsight] = counts.getValue(DefaultInsight) + 1
            } else {
                matched.forEach { definition ->
                    counts[definition] = counts.getValue(definition) + 1
                }
            }
        }

        return InsightDefinitions
            .map { definition ->
                BehaviorInsight(
                    label = definition.label,
                    description = definition.description,
                    count = counts.getValue(definition),
                )
            }
            .filter { it.count > 0 }
            .sortedWith(
                compareByDescending<BehaviorInsight> { it.count }
                    .thenBy { insight -> InsightDefinitions.indexOfFirst { it.label == insight.label } },
            )
            .take(4)
    }

    private fun String.normalizedQuestion(): String {
        return lowercase()
            .replace(Regex("[^\\p{L}0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.topicWords(): List<String> {
        return normalizedQuestion()
            .split(' ')
            .filter { word ->
                word.length >= 4 && word !in StopWords
            }
    }

    private companion object {
        val StartTroubleInsight = InsightDefinition(
            label = "Ei saa üldse aru",
            description = "Õpilane vajab abi alustamiseks või ei leia järgmist sammu.",
            keywords = listOf(
                "ei saa aru",
                "ma ei saa aru",
                "dont understand",
                "don't understand",
                "i dont understand",
                "i don't understand",
                "what should i focus",
                "millele peaksin",
                "kust alustada",
                "aita",
                "help",
                "stuck",
                "kinni",
                "hätta",
            ),
        )
        val FollowUpInsight = InsightDefinition(
            label = "Küsib lisaküsimusi",
            description = "Õpilane otsib kinnitust, selgitust või järgmist vihjet.",
            keywords = listOf(
                "miks",
                "kuidas",
                "mida",
                "millal",
                "kas",
                "why",
                "how",
                "what",
                "explain",
                "selgita",
                "like this",
                "nii",
            ),
        )
        val WantsAnswerInsight = InsightDefinition(
            label = "Tahab kohe vastust",
            description = "Õpilane liigub vastuse küsimise poole enne põhjendamist.",
            keywords = listOf(
                "just give",
                "give me the answer",
                "give answer",
                "answer",
                "solution",
                "anna vastus",
                "lihtsalt vasta",
                "vastus",
                "lahendus",
            ),
        )
        val ChecksWorkInsight = InsightDefinition(
            label = "Kontrollib lahendust",
            description = "Õpilane tahab teada, kas tema vahe- või lõppsamm on õige.",
            keywords = listOf(
                "is it correct",
                "correct now",
                "correct",
                "kas see on õige",
                "õige",
                "kontrolli",
                "check",
                "verify",
            ),
        )
        val VisualHelpInsight = InsightDefinition(
            label = "Vajab visuaalset abi",
            description = "Õpilane kasutab tööala pilti või valitud piirkonda, et probleemi täpsustada.",
            keywords = emptyList(),
            predicate = { exchange -> exchange.includedWorkspace || exchange.usedHighlight },
        )
        val DefaultInsight = InsightDefinition(
            label = "Sisuline abi",
            description = "Õpilane küsib konkreetse ülesande või mõiste kohta.",
            keywords = emptyList(),
        )
        val InsightDefinitions = listOf(
            StartTroubleInsight,
            FollowUpInsight,
            WantsAnswerInsight,
            ChecksWorkInsight,
            VisualHelpInsight,
            DefaultInsight,
        )

        val StopWords = setOf(
            "about",
            "after",
            "again",
            "answer",
            "because",
            "before",
            "could",
            "does",
            "explain",
            "from",
            "have",
            "help",
            "into",
            "just",
            "like",
            "need",
            "please",
            "question",
            "should",
            "that",
            "their",
            "there",
            "this",
            "what",
            "when",
            "where",
            "which",
            "with",
            "would",
            "aga",
            "ainult",
            "aitäh",
            "alla",
            "enne",
            "ja",
            "kas",
            "kuidas",
            "kui",
            "küsimus",
            "kõik",
            "millal",
            "miks",
            "mille",
            "mida",
            "mis",
            "mul",
            "mulle",
            "ning",
            "nüüd",
            "palun",
            "peab",
            "peaks",
            "saan",
            "seda",
            "see",
            "selle",
            "selgita",
            "siin",
            "siis",
            "sul",
            "teha",
            "ülesanne",
            "vastus",
            "veel",
            "võiks",
            "või",
        )
    }
}

private data class InsightDefinition(
    val label: String,
    val description: String,
    val keywords: List<String>,
    val predicate: (ConversationExchange) -> Boolean = { false },
) {
    fun matches(exchange: ConversationExchange): Boolean {
        val combinedText = "${exchange.question}\n${exchange.modelInput}\n${exchange.answer}".lowercase()
        return predicate(exchange) || keywords.any { keyword -> keyword in combinedText }
    }
}

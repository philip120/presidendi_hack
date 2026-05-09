package com.haridushakk.classroomai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

data class TeacherDashboard(
    val totalQuestions: Int = 0,
    val workspaceQuestions: Int = 0,
    val highlightedQuestions: Int = 0,
    val uniqueQuestions: Int = 0,
    val commonQuestions: List<QuestionInsight> = emptyList(),
    val commonTopics: List<TopicInsight> = emptyList(),
    val recentConversations: List<ConversationExchange> = emptyList(),
)

data class QuestionInsight(
    val question: String,
    val count: Int,
)

data class TopicInsight(
    val topic: String,
    val count: Int,
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
        _uiState.update { state ->
            state.copy(generatedSummary = state.conversations.toTeacherSummary())
        }
    }

    fun clearConversationHistory() {
        viewModelScope.launch {
            repository.clearConversationExchanges()
            _uiState.update { state ->
                state.copy(generatedSummary = "")
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
        )
    }

    private fun List<ConversationExchange>.toTeacherSummary(): String {
        if (isEmpty()) {
            return "Õpilaste vestlusi ei ole veel salvestatud."
        }

        val dashboard = toDashboard()
        val topTopics = dashboard.commonTopics.take(5).joinToString { "${it.topic} (${it.count})" }
            .ifBlank { "korduvaid teemasid pole veel piisavalt" }
        val repeatedQuestions = dashboard.commonQuestions
            .filter { it.count > 1 }
            .take(3)
            .joinToString(separator = "\n") { "- ${it.question} (${it.count} korda)" }
            .ifBlank { "- Täpselt korduvaid küsimusi ei ole veel." }
        val workspaceShare = ((dashboard.workspaceQuestions.toFloat() / dashboard.totalQuestions) * 100f)
            .roundToInt()
        val highlightShare = ((dashboard.highlightedQuestions.toFloat() / dashboard.totalQuestions) * 100f)
            .roundToInt()

        return buildString {
            appendLine("Kokkuvõte põhineb ${dashboard.totalQuestions} salvestatud õpilasküsimusel.")
            appendLine()
            appendLine("Sagedasemad teemad: $topTopics.")
            appendLine()
            appendLine("Korduvad küsimused:")
            appendLine(repeatedQuestions)
            appendLine()
            appendLine("Tööala kasutus: ${dashboard.workspaceQuestions} küsimust kasutas tööala pilti ($workspaceShare%).")
            appendLine("Fookustatud valikud: ${dashboard.highlightedQuestions} küsimust kasutas valitud piirkonda ($highlightShare%).")
            appendLine()
            append("Soovitus õpetajale: korda sagedasemaid teemasid ja käsitle korduvaid küsimusi järgmise tunni alguses.")
        }.trim()
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

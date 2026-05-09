package com.haridushakk.classroomai.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haridushakk.classroomai.data.ConversationExchange
import com.haridushakk.classroomai.ui.BehaviorInsight
import com.haridushakk.classroomai.ui.QuestionInsight
import com.haridushakk.classroomai.ui.TeacherDashboard
import com.haridushakk.classroomai.ui.TeacherUiState
import com.haridushakk.classroomai.ui.TeacherViewModel
import com.haridushakk.classroomai.ui.TopicInsight
import java.text.SimpleDateFormat
import java.util.Date
import java.io.File
import java.util.Locale

private val InsightBarColors = listOf(
    listOf(Color(0xFF4F46E5), Color(0xFF06B6D4)),
    listOf(Color(0xFF059669), Color(0xFF84CC16)),
    listOf(Color(0xFFD97706), Color(0xFFF59E0B)),
    listOf(Color(0xFFDC2626), Color(0xFFEC4899)),
)

private val TeacherHeadingStyle = SpanStyle(
    color = Color(0xFF111827),
    fontSize = 17.sp,
    fontWeight = FontWeight.SemiBold,
)

private val TeacherBoldStyle = SpanStyle(
    color = Color(0xFF111827),
    fontWeight = FontWeight.SemiBold,
)

@Composable
fun TeacherRoute(
    viewModel: TeacherViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TeacherScreen(
        uiState = uiState,
        onBack = onBack,
        onMaterialChanged = viewModel::onMaterialInputChanged,
        onMaterialImported = viewModel::onMaterialImported,
        onMaterialImportFailed = viewModel::onMaterialImportFailed,
        onSaveMaterial = viewModel::saveMaterial,
        onClearMaterial = viewModel::clearMaterial,
        onGenerateSummary = viewModel::generateConversationSummary,
        onClearConversationHistory = viewModel::clearConversationHistory,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeacherScreen(
    uiState: TeacherUiState,
    onBack: () -> Unit,
    onMaterialChanged: (String) -> Unit,
    onMaterialImported: (String) -> Unit,
    onMaterialImportFailed: () -> Unit,
    onSaveMaterial: () -> Unit,
    onClearMaterial: () -> Unit,
    onGenerateSummary: () -> Unit,
    onClearConversationHistory: () -> Unit,
) {
    val context = LocalContext.current
    val materialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val importedText = runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
        }
        importedText
            .onSuccess(onMaterialImported)
            .onFailure { onMaterialImportFailed() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Õpetaja töölaud") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Tagasi",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DashboardHeader(dashboard = uiState.dashboard)
            ConversationInsights(
                uiState = uiState,
                onGenerateSummary = onGenerateSummary,
                onClearConversationHistory = onClearConversationHistory,
            )
            MaterialUploadPanel(
                uiState = uiState,
                onMaterialChanged = onMaterialChanged,
                onImportMaterial = { materialLauncher.launch("text/*") },
                onSaveMaterial = onSaveMaterial,
                onClearMaterial = onClearMaterial,
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    dashboard: TeacherDashboard,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Klassi ülevaade",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 760.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricCard(
                        label = "Küsimused",
                        value = dashboard.totalQuestions.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = "Erinevad",
                        value = dashboard.uniqueQuestions.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = "Tööala",
                        value = dashboard.workspaceQuestions.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = "Valitud",
                        value = dashboard.highlightedQuestions.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetricCard(
                            label = "Küsimused",
                            value = dashboard.totalQuestions.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        MetricCard(
                            label = "Erinevad",
                            value = dashboard.uniqueQuestions.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetricCard(
                            label = "Tööala",
                            value = dashboard.workspaceQuestions.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        MetricCard(
                            label = "Valitud",
                            value = dashboard.highlightedQuestions.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF4B5563),
            )
        }
    }
}

@Composable
private fun ConversationInsights(
    uiState: TeacherUiState,
    onGenerateSummary: () -> Unit,
    onClearConversationHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(
            title = "Õpilaste vestlused",
            subtitle = "Salvestatud selle seadme õnnestunud õpilasvestlustest.",
        )
        BehaviorInsightsPanel(
            insights = uiState.dashboard.behaviorInsights,
            totalQuestions = uiState.dashboard.totalQuestions,
        )
        SummaryPanel(
            summary = uiState.generatedSummary,
            hasConversations = uiState.conversations.isNotEmpty(),
            isGeneratingSummary = uiState.isGeneratingSummary,
            summaryError = uiState.summaryError,
            onGenerateSummary = onGenerateSummary,
            onClearConversationHistory = onClearConversationHistory,
        )
        ConversationLogPanel(conversations = uiState.conversations)
    }
}

@Composable
private fun CommonQuestionsPanel(
    questions: List<QuestionInsight>,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Kõige sagedasemad küsimused",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (questions.isEmpty()) {
                EmptyState(text = "Salvestatud õpilasküsimusi veel ei ole.")
            } else {
                questions.forEach { insight ->
                    QuestionInsightRow(insight = insight)
                }
            }
        }
    }
}

@Composable
private fun QuestionInsightRow(
    insight: QuestionInsight,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CountBadge(count = insight.count)
        Text(
            text = insight.question,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CountBadge(
    count: Int,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFEFF6FF),
    ) {
        Text(
            text = "${count}×",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF1D4ED8),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BehaviorInsightsPanel(
    insights: List<BehaviorInsight>,
    totalQuestions: Int,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Klassi signaalid",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Üldised mustrid vestlustest, mitte üksikute sõnade loend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7280),
                    )
                }
                CountBadge(count = totalQuestions)
            }
            if (insights.isEmpty()) {
                EmptyState(text = "Signaalid ilmuvad siia, kui õpilased on vestlust kasutanud.")
            } else {
                insights.forEachIndexed { index, insight ->
                    BehaviorInsightRow(
                        insight = insight,
                        totalQuestions = totalQuestions,
                        colors = InsightBarColors[index % InsightBarColors.size],
                    )
                }
            }
        }
    }
}

@Composable
private fun BehaviorInsightRow(
    insight: BehaviorInsight,
    totalQuestions: Int,
    colors: List<Color>,
) {
    val ratio = if (totalQuestions == 0) {
        0f
    } else {
        (insight.count.toFloat() / totalQuestions.toFloat()).coerceIn(0f, 1f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = insight.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827),
                )
                Text(
                    text = insight.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
                )
            }
            Text(
                text = "${insight.count}×",
                style = MaterialTheme.typography.labelLarge,
                color = colors.first(),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(Color(0xFFE5E7EB), RoundedCornerShape(8.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceAtLeast(0.08f))
                    .height(12.dp)
                    .background(
                        brush = Brush.horizontalGradient(colors),
                        shape = RoundedCornerShape(8.dp),
                    ),
            )
        }
    }
}

@Composable
private fun TopicPanel(
    topics: List<TopicInsight>,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Levinud teemad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (topics.isEmpty()) {
                EmptyState(text = "Teemad ilmuvad siia, kui õpilased küsimusi küsivad.")
            } else {
                topics.forEach { topic ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = topic.topic,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        CountBadge(count = topic.count)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryPanel(
    summary: String,
    hasConversations: Boolean,
    isGeneratingSummary: Boolean,
    summaryError: String?,
    onGenerateSummary: () -> Unit,
    onClearConversationHistory: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Õpetaja kokkuvõte",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onGenerateSummary,
                        enabled = hasConversations && !isGeneratingSummary,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Summarize,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (isGeneratingSummary) "Koostab..." else "Koosta kokkuvõte")
                    }
                    TextButton(
                        onClick = onClearConversationHistory,
                        enabled = hasConversations && !isGeneratingSummary,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Tühjenda")
                    }
                }
            }
            summaryError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB45309),
                )
            }
            SummaryBody(
                summary = summary,
                isGeneratingSummary = isGeneratingSummary,
            )
        }
    }
}

@Composable
private fun SummaryBody(
    summary: String,
    isGeneratingSummary: Boolean,
) {
    val bodyText = when {
        isGeneratingSummary -> "AI koostab kokkuvõtet kõigi salvestatud vestluste põhjal."
        summary.isBlank() -> "Vajuta nuppu, et koostada õpetajale kokkuvõte kõigist õpilaste vestlustest."
        else -> summary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF9FAFB),
    ) {
        SelectionContainer {
            Text(
                text = bodyText.toTeacherRichText(),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
                color = if (summary.isBlank()) Color(0xFF6B7280) else Color(0xFF111827),
            )
        }
    }
}

@Composable
private fun ConversationLogPanel(
    conversations: List<ConversationExchange>,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Vestluste logi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (conversations.isEmpty()) {
                EmptyState(text = "Õpilaste küsimused ja mudelile saadetud sisendid ilmuvad siia.")
            } else {
                conversations.sortedByDescending { it.askedAtMillis }.forEach { exchange ->
                    ConversationRow(exchange = exchange)
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    exchange: ConversationExchange,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF9FAFB),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTimestamp(exchange.askedAtMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6B7280),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (exchange.includedWorkspace) {
                        SmallLabel(text = "Tööala pilt")
                    }
                    if (exchange.usedHighlight) {
                        SmallLabel(text = "Valitud ala")
                    }
                }
            }
            ConversationField(
                label = "Õpilase küsimus",
                text = exchange.question,
                emphasize = true,
            )
            ConversationField(
                label = "Mudelile saadetud",
                text = exchange.modelInput.ifBlank { exchange.question },
                monospace = true,
            )
            ConversationImagePreview(
                imagePath = exchange.modelImagePath,
                usedHighlight = exchange.usedHighlight,
            )
            ConversationField(
                label = "AI vastus",
                text = exchange.answer,
            )
        }
    }
}

@Composable
private fun ConversationImagePreview(
    imagePath: String?,
    usedHighlight: Boolean,
) {
    if (imagePath.isNullOrBlank()) return

    val imageBitmap = remember(imagePath) {
        val imageFile = File(imagePath)
        if (imageFile.exists()) {
            BitmapFactory.decodeFile(imagePath)?.asImageBitmap()
        } else {
            null
        }
    } ?: return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (usedHighlight) "Mudelile saadetud valitud ala" else "Mudelile saadetud tööala pilt",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF6B7280),
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = if (usedHighlight) {
                    "Mudelile saadetud valitud ala"
                } else {
                    "Mudelile saadetud tööala pilt"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun ConversationField(
    label: String,
    text: String,
    emphasize: Boolean = false,
    monospace: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF6B7280),
            fontWeight = FontWeight.SemiBold,
        )
        SelectionContainer {
            Text(
                text = text.ifBlank { "(puudub)" }.toTeacherRichText(),
                style = if (monospace) {
                    MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 18.sp,
                    )
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (emphasize) Color(0xFF111827) else Color(0xFF374151),
                fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun SmallLabel(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF3F4F6),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF374151),
        )
    }
}

@Composable
private fun MaterialUploadPanel(
    uiState: TeacherUiState,
    onMaterialChanged: (String) -> Unit,
    onImportMaterial: () -> Unit,
    onSaveMaterial: () -> Unit,
    onClearMaterial: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(
            title = "Laadi materjalid",
            subtitle = "Salvestatud materjal suunab AI-d õpilaste küsimustele vastamisel.",
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = uiState.materialInput,
                    onValueChange = onMaterialChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp),
                    minLines = 9,
                    placeholder = { Text(text = "Kleebi tunnimärkmed, õpiku väljavõtted või juhised") },
                )
                MaterialActionRow(
                    savedMaterial = uiState.savedMaterial,
                    materialInput = uiState.materialInput,
                    onImportMaterial = onImportMaterial,
                    onSaveMaterial = onSaveMaterial,
                    onClearMaterial = onClearMaterial,
                )
                uiState.materialImportMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1D4ED8),
                    )
                }
                if (uiState.showSavedConfirmation) {
                    Text(
                        text = "Materjal salvestatud. Õpilased saavad seda AI assistendis kasutada.",
                        color = Color(0xFF15803D),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        SavedMaterialPreview(savedMaterial = uiState.savedMaterial)
    }
}

@Composable
private fun MaterialActionRow(
    savedMaterial: String,
    materialInput: String,
    onImportMaterial: () -> Unit,
    onSaveMaterial: () -> Unit,
    onClearMaterial: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val vertical = maxWidth < 560.dp
        val actions: @Composable () -> Unit = {
            OutlinedButton(onClick = onImportMaterial) {
                Icon(
                    imageVector = Icons.Filled.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Impordi tekst")
            }
            Button(
                onClick = onSaveMaterial,
                enabled = materialInput.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Salvesta")
            }
            TextButton(
                onClick = onClearMaterial,
                enabled = savedMaterial.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Tühjenda")
            }
        }

        if (vertical) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                actions()
            }
        }
    }
}

@Composable
private fun SavedMaterialPreview(
    savedMaterial: String,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 300.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Praegu salvestatud materjal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = savedMaterial.ifBlank { "Materjali pole veel salvestatud." },
                style = MaterialTheme.typography.bodyMedium,
                color = if (savedMaterial.isBlank()) Color(0xFF6B7280) else Color.Unspecified,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF6B7280),
        )
    }
}

@Composable
private fun EmptyState(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF6B7280),
    )
}

private fun String.toTeacherRichText(): AnnotatedString {
    return buildAnnotatedString {
        val lines = this@toTeacherRichText.lines()
        lines.forEachIndexed { index, rawLine ->
            val trimmed = rawLine.trim()
            when {
                trimmed.isBlank() -> append("\n")
                trimmed.startsWith("###") -> {
                    withStyle(TeacherHeadingStyle) {
                        appendMarkdownLatexInline(trimmed.removePrefix("#").trimStart('#').trim())
                    }
                    append("\n")
                }
                trimmed.startsWith("* ") || trimmed.startsWith("- ") -> {
                    append("• ")
                    appendMarkdownLatexInline(trimmed.drop(2).trim())
                    append("\n")
                }
                else -> {
                    appendMarkdownLatexInline(rawLine)
                    if (index < lines.lastIndex) append("\n")
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendMarkdownLatexInline(value: String) {
    var index = 0
    while (index < value.length) {
        val boldStart = value.indexOf("**", startIndex = index)
        if (boldStart == -1) {
            append(value.substring(index).toLatexAnnotatedString())
            return
        }

        append(value.substring(index, boldStart).toLatexAnnotatedString())
        val boldEnd = value.indexOf("**", startIndex = boldStart + 2)
        if (boldEnd == -1) {
            append(value.substring(boldStart).toLatexAnnotatedString())
            return
        }

        withStyle(TeacherBoldStyle) {
            append(value.substring(boldStart + 2, boldEnd).toLatexAnnotatedString())
        }
        index = boldEnd + 2
    }
}

private fun formatTimestamp(value: Long): String {
    if (value <= 0L) return "Tundmatu aeg"
    val formatter = SimpleDateFormat("d. MMM HH:mm", Locale.getDefault())
    return formatter.format(Date(value))
}

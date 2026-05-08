package com.haridushakk.classroomai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haridushakk.classroomai.ui.TeacherUiState
import com.haridushakk.classroomai.ui.TeacherViewModel

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
        onSaveMaterial = viewModel::saveMaterial,
        onClearMaterial = viewModel::clearMaterial,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeacherScreen(
    uiState: TeacherUiState,
    onBack: () -> Unit,
    onMaterialChanged: (String) -> Unit,
    onSaveMaterial: () -> Unit,
    onClearMaterial: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Material Upload") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "This material will guide the AI when students ask questions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            OutlinedTextField(
                value = uiState.materialInput,
                onValueChange = onMaterialChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp),
                minLines = 9,
                placeholder = { Text(text = "Paste lecture notes, textbook excerpts, or instructions") },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onSaveMaterial,
                    enabled = uiState.materialInput.isNotBlank(),
                ) {
                    Text(text = "Save Material")
                }
                TextButton(
                    onClick = onClearMaterial,
                    enabled = uiState.savedMaterial.isNotBlank(),
                ) {
                    Text(text = "Clear material")
                }
            }
            if (uiState.showSavedConfirmation) {
                Text(
                    text = "Material saved. Students can now use the AI assistant.",
                    color = Color(0xFF15803D),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Currently saved material",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text(
                        text = uiState.savedMaterial.ifBlank { "No material saved yet." },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

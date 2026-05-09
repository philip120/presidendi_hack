package com.haridushakk.classroomai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RoleSelectionScreen(
    onTeacherSelected: () -> Unit,
    onStudentSelected: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Klassiruumi AI",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onTeacherSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.School,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.widthIn(min = 12.dp))
                Text(text = "Olen õpetaja")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onStudentSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.widthIn(min = 12.dp))
                Text(text = "Olen õpilane")
            }
        }
    }
}

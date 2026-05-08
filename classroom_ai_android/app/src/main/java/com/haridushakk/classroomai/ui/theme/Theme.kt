package com.haridushakk.classroomai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClassroomLightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    secondary = Color(0xFF0F766E),
    tertiary = Color(0xFFB45309),
    background = Color(0xFFFAFBFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEFF2F7),
    outline = Color(0xFFC5CBD5),
)

@Composable
fun ClassroomAiTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ClassroomLightColors,
        content = content,
    )
}

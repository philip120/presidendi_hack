package com.haridushakk.classroomai

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.haridushakk.classroomai.ai.GeminiAssistant
import com.haridushakk.classroomai.data.ClassroomRepository
import com.haridushakk.classroomai.data.classroomDataStore
import com.haridushakk.classroomai.ui.ClassroomAiApp
import com.haridushakk.classroomai.ui.theme.ClassroomAiTheme

class MainActivity : ComponentActivity() {
    private val appContainer by lazy {
        ClassroomAiContainer(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClassroomAiTheme {
                ClassroomAiApp(
                    repository = appContainer.repository,
                    geminiAssistant = appContainer.geminiAssistant,
                )
            }
        }
    }
}

private class ClassroomAiContainer(context: Context) {
    val repository = ClassroomRepository(
        dataStore = context.classroomDataStore,
        filesDir = context.filesDir,
    )
    val geminiAssistant = GeminiAssistant(
        apiKey = BuildConfig.GEMINI_API_KEY,
        modelName = BuildConfig.GEMINI_MODEL_NAME,
    )
}

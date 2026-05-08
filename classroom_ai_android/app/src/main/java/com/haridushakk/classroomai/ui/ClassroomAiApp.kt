package com.haridushakk.classroomai.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.haridushakk.classroomai.ai.GeminiAssistant
import com.haridushakk.classroomai.data.ClassroomRepository
import com.haridushakk.classroomai.ui.screens.RoleSelectionScreen
import com.haridushakk.classroomai.ui.screens.StudentRoute
import com.haridushakk.classroomai.ui.screens.TeacherRoute

@Composable
fun ClassroomAiApp(
    repository: ClassroomRepository,
    geminiAssistant: GeminiAssistant,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.Entry.path,
    ) {
        composable(Route.Entry.path) {
            RoleSelectionScreen(
                onTeacherSelected = { navController.navigate(Route.Teacher.path) },
                onStudentSelected = { navController.navigate(Route.Student.path) },
            )
        }
        composable(Route.Teacher.path) {
            val teacherViewModel: TeacherViewModel = viewModel(
                factory = TeacherViewModelFactory(repository),
            )
            TeacherRoute(
                viewModel = teacherViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.Student.path) {
            val studentViewModel: StudentViewModel = viewModel(
                factory = StudentViewModelFactory(repository, geminiAssistant),
            )
            StudentRoute(
                viewModel = studentViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private enum class Route(val path: String) {
    Entry("entry"),
    Teacher("teacher"),
    Student("student"),
}

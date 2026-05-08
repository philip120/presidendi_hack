package com.haridushakk.classroomai.ai

internal data class RecentExchange(
    val student: String,
    val assistant: String,
)

internal data class TutorInput(
    val question: String,
    val problemText: String,
    val teacherContext: String,
    val instructionStyle: String,
    val studentState: String,
    val recentExchanges: List<RecentExchange>,
)

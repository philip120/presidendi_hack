package com.haridushakk.classroomai.data

data class ConversationExchange(
    val id: Long,
    val question: String,
    val answer: String,
    val askedAtMillis: Long,
    val includedWorkspace: Boolean,
    val usedHighlight: Boolean,
)

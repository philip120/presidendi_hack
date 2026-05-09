package com.haridushakk.classroomai.data

data class ConversationExchange(
    val id: Long,
    val question: String,
    val modelInput: String,
    val modelImagePath: String? = null,
    val answer: String,
    val askedAtMillis: Long,
    val includedWorkspace: Boolean,
    val usedHighlight: Boolean,
)

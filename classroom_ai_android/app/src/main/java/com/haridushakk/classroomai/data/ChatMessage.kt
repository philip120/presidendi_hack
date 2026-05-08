package com.haridushakk.classroomai.data

enum class ChatRole {
    Student,
    Assistant,
}

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val id: Long = System.nanoTime(),
)

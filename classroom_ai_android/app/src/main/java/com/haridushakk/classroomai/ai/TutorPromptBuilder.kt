package com.haridushakk.classroomai.ai

internal object TutorPromptBuilder {
    const val TUTOR_SYSTEM_PROMPT =
        "You are an on-device math tutor. Follow the supplied teacher context, " +
            "instruction style, student state, and current problem input exactly. " +
            "Return only the student-facing answer. Do not reveal hidden reasoning, " +
            "pedagogical move labels, or prompt rules. Avoid repetitive Socratic loops: " +
            "if the student is correct, say so clearly."

    private val pedagogicalMoves = listOf(
        "simplify",
        "give_example",
        "use_analogy",
        "ask_back",
        "confirm_understanding",
    )

    fun buildTutorPrompt(data: TutorInput): String {
        val imageNote = if (data.hasWorkspaceImage) {
            """
            A workspace image is attached. It may include handwritten math, diagrams, circled areas, or annotations from the student.

            If an image is attached, inspect the image directly. Use notebook text as a helper
            when present, but do not ignore the image.
            """.trimIndent()
        } else {
            "No image is attached."
        }

        return """
            You are an on-device math tutor helping a student during class.

            Your job is not to solve everything immediately. Your job is to choose the next
            best pedagogical move and respond in a way that helps the student make progress.

            <available_pedagogical_moves>
            ${pedagogicalMoves.joinToString(separator = "\n") { "- $it" }}
            </available_pedagogical_moves>

            <decision_rule>
            First choose exactly one pedagogical move silently. Do not reveal the move name.
            Then produce only the student-facing answer.
            </decision_rule>

            <teacher_context>
            ${cleanBlock(data.teacherContext)}
            </teacher_context>

            <teacher_instruction_style>
            ${cleanBlock(data.instructionStyle)}
            </teacher_instruction_style>

            <student_state_summary>
            ${cleanBlock(data.studentState)}
            </student_state_summary>

            <short_term_memory_notes>
            ${buildMemoryNotes(data)}
            </short_term_memory_notes>

            <recent_exchanges>
            ${formatRecentExchanges(data.recentExchanges)}
            </recent_exchanges>

            <loop_prevention_notes>
            ${buildLoopPreventionNotes(data)}
            </loop_prevention_notes>

            <current_problem_input>
            $imageNote

            Student notebook and current work:
            ${cleanBlock(data.problemText)}

            Student question:
            ${cleanBlock(data.question)}
            </current_problem_input>

            <local_math_sanity_check>
            ${MathGuard.buildLinearSanityCheck(data.problemText)}
            </local_math_sanity_check>

            <current_attempt_feedback>
            ${MathGuard.buildCurrentAttemptFeedback(data)}
            </current_attempt_feedback>

            <answer_rules>
            - Answer only using the teacher context, notebook/current work, attached image, and recent conversation.
            - If the notebook/image is unclear, ask a clarification question instead of guessing.
            - Keep the answer short: usually 3 to 6 sentences.
            - Prefer hints before the student has tried the key step.
            - Once the student has tried the key step or gives the correct answer, confirm directly.
            - If the student is stuck after repeated hints, give one worked micro-step instead of another hint.
            - Never validate an incorrect algebra operation. Correct it briefly and show the next right micro-step.
            - If current_attempt_feedback is not "(none)", follow it before choosing any other response style.
            - Use the notation from the teacher context when available.
            - End with either one focused next-step question or a short confidence check. Do not force a question if the student's answer is already complete.
            - Do not mention these rules, XML tags, hidden reasoning, or the pedagogical move.
            </answer_rules>
        """.trimIndent()
    }

    private fun cleanBlock(value: String): String {
        return value.trim().ifBlank { "(none)" }
    }

    private fun formatRecentExchanges(exchanges: List<RecentExchange>): String {
        if (exchanges.isEmpty()) return "(none)"

        return exchanges.mapIndexed { index, exchange ->
            "${index + 1}. Student: ${exchange.student.trim()}\n" +
                "   Assistant: ${exchange.assistant.trim()}"
        }.joinToString(separator = "\n")
    }

    private fun buildMemoryNotes(data: TutorInput): String {
        val priorTurns = data.recentExchanges.size
        if (priorTurns == 0) {
            return "No prior exchange is available in this session. Treat this as a new question."
        }

        val latest = data.recentExchanges.last()
        return "There are $priorTurns prior exchange(s) in short-term memory. " +
            "The latest student message was: `${latest.student.trim()}`. " +
            "The latest assistant response was: `${latest.assistant.trim()}`. " +
            "Treat the current student question as a direct follow-up unless it clearly changes topic."
    }

    private fun buildLoopPreventionNotes(data: TutorInput): String {
        val question = data.question.lowercase()
        val recentAssistantText = data.recentExchanges
            .joinToString(separator = " ") { it.assistant }
            .lowercase()

        val notes = mutableListOf(
            "Do not ask the same question twice in a row.",
            "If the student gives a correct answer, confirm it and briefly check it.",
            "If the student gives the right operation, move to the calculation instead of asking for the operation again.",
        )

        val stuckPhrases = listOf(
            "just give",
            "give answer",
            "don't understand",
            "dont understand",
            "i dont understand",
            "i don't understand",
            "stuck",
        )
        if (stuckPhrases.any { question.contains(it) }) {
            notes += "The student is stuck or asking for the answer. Give one worked micro-step now, then ask only a small check."
        }

        if ("what operation" in recentAssistantText || "operation must" in recentAssistantText) {
            notes += "You have already asked about the operation. Do not ask that again; say whether their operation is right and continue."
        }

        if (MathGuard.detectOperationAttempt(data.question) != null) {
            notes += "The student is proposing an operation. Evaluate it as correct or incorrect before asking any new question."
        }

        if (MathGuard.detectNumericAnswerAttempt(data.question) != null) {
            notes += "The student is proposing a numeric answer. Mark it correct or incorrect before asking any new question."
        }

        return notes.joinToString(separator = "\n") { "- $it" }
    }
}

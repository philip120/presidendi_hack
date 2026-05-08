package com.haridushakk.classroomai.ai

import kotlin.math.abs

internal object MathGuard {
    private val simpleLinearEquationRegex = Regex(
        pattern = """([+-]?(?:\d+(?:/\d+)?|\d*\.\d+)?)\s*\*?\s*x\s*=\s*([+-]?(?:\d+(?:/\d+)?|\d*\.\d+))""",
        option = RegexOption.IGNORE_CASE,
    )
    private val explicitXAnswerRegex = Regex(
        pattern = """\bx\s*=\s*([+-]?(?:\d+(?:/\d+)?|\d*\.\d+))""",
        option = RegexOption.IGNORE_CASE,
    )
    private val cuedAnswerRegex = Regex(
        pattern = """\b(?:answer|solution|equals|equal to|is)\s*(?:x\s*)?(?:=|:)?\s*([+-]?(?:\d+(?:/\d+)?|\d*\.\d+))""",
        option = RegexOption.IGNORE_CASE,
    )

    fun buildLinearSanityCheck(problemText: String): String {
        val parsed = parseSimpleLinearEquation(problemText) ?: return "(none)"
        parsed.error?.let { return it }

        val coefficient = parsed.coefficient ?: return "(none)"
        val rightSide = parsed.rightSide ?: return "(none)"
        val solution = parsed.solution ?: return "(none)"
        return "Detected simple equation `${parsed.equation}`. " +
            "The inverse operation is to divide both sides by ${coefficient.format()}. " +
            "${rightSide.format()} / ${coefficient.format()} = ${solution.format()}, " +
            "so x = ${solution.format()}. " +
            "If the student says x = ${solution.format()}, confirm it as correct."
    }

    fun buildCurrentAttemptFeedback(data: TutorInput): String {
        val parsed = parseSimpleLinearEquation(data.problemText) ?: return "(none)"
        if (parsed.error != null) return "(none)"

        val coefficient = parsed.coefficient ?: return "(none)"
        val rightSide = parsed.rightSide ?: return "(none)"
        val solution = parsed.solution ?: return "(none)"
        val operationAttempt = detectOperationAttempt(data.question)

        if (operationAttempt != null) {
            return when (operationAttempt) {
                "division" -> {
                    "The student proposed division. For this equation, that is the correct operation. " +
                        "Do not ask for the operation again. Continue with the calculation: " +
                        "divide both sides by ${coefficient.format()}, so x = ${solution.format()}."
                }
                "multiplication" -> {
                    "The student proposed multiplication. For this equation, that is incorrect. " +
                        "In `${parsed.equation}`, x is already multiplied by ${coefficient.format()}, " +
                        "so multiplying again makes the expression more complicated. " +
                        "Correct them gently and state that division by ${coefficient.format()} is the inverse operation."
                }
                else -> {
                    "The student proposed $operationAttempt. For `${parsed.equation}`, that is incorrect " +
                        "because x is multiplied by ${coefficient.format()}. Correct them gently and say " +
                        "the needed inverse operation is division by ${coefficient.format()}."
                }
            }
        }

        val answerAttempt = detectNumericAnswerAttempt(data.question)
        if (answerAttempt != null) {
            return if (answerAttempt == solution) {
                "The student proposed x = ${answerAttempt.format()}. That is correct. " +
                    "Confirm directly and show the check: ${coefficient.format()} * ${solution.format()} = ${rightSide.format()}."
            } else {
                "The student proposed x = ${answerAttempt.format()}. That is incorrect for `${parsed.equation}`. " +
                    "The correct calculation is ${rightSide.format()} / ${coefficient.format()} = ${solution.format()}."
            }
        }

        return "(none)"
    }

    fun buildControllerGuardResponse(data: TutorInput): String? {
        val parsed = parseSimpleLinearEquation(data.problemText) ?: return null
        if (parsed.error != null) return null

        val coefficient = parsed.coefficient ?: return null
        val rightSide = parsed.rightSide ?: return null
        val solution = parsed.solution ?: return null
        val operationAttempt = detectOperationAttempt(data.question)

        if (operationAttempt == "division") {
            return "Yes, division is the right operation here. In `${parsed.equation}`, `x` is multiplied by `${coefficient.format()}`, " +
                "so we undo that by dividing both sides by `${coefficient.format()}`. " +
                "That gives `x = ${rightSide.format()} / ${coefficient.format()}`. What does that simplify to?"
        }

        if (operationAttempt in setOf("addition", "subtraction", "multiplication")) {
            return "Not quite. In `${parsed.equation}`, `x` is being multiplied by `${coefficient.format()}`, " +
                "so $operationAttempt will not isolate `x`. The inverse operation is division: " +
                "divide both sides by `${coefficient.format()}`. What is `${rightSide.format()} / ${coefficient.format()}`?"
        }

        val answerAttempt = detectNumericAnswerAttempt(data.question)
        if (answerAttempt != null) {
            return if (answerAttempt == solution) {
                "Yes, `x = ${answerAttempt.format()}` is correct. Check it by substituting back: " +
                    "`${coefficient.format()} * ${solution.format()} = ${rightSide.format()}`. " +
                    "That matches the original equation."
            } else {
                "Not quite. For `${parsed.equation}`, divide both sides by `${coefficient.format()}`: " +
                    "`x = ${rightSide.format()} / ${coefficient.format()} = ${solution.format()}`. " +
                    "So the answer is `x = ${solution.format()}`."
            }
        }

        return null
    }

    fun detectOperationAttempt(question: String): String? {
        val text = question.lowercase()
        val operationWords = listOf(
            "addition" to listOf("addition", "add", "plus"),
            "subtraction" to listOf("subtraction", "subtract", "minus"),
            "multiplication" to listOf("multiplication", "multiply", "times"),
            "division" to listOf("division", "divide", "dividing", "divided", "÷"),
        )
        return operationWords.firstOrNull { (_, words) ->
            words.any { word -> text.contains(word) }
        }?.first
    }

    fun detectNumericAnswerAttempt(question: String): Rational? {
        explicitXAnswerRegex.findAll(question).lastOrNull()?.let { match ->
            return Rational.parse(match.groupValues[1])
        }

        cuedAnswerRegex.findAll(question).lastOrNull()?.let { match ->
            return Rational.parse(match.groupValues[1])
        }

        return null
    }

    private fun parseSimpleLinearEquation(problemText: String): ParsedSimpleLinearEquation? {
        val normalized = problemText
            .replace("−", "-")
            .replace("·", "*")

        normalized.lineSequence().forEach { rawLine ->
            val match = simpleLinearEquationRegex.find(rawLine.trim()) ?: return@forEach
            val coefficient = parseCoefficient(match.groupValues[1])
                ?: return ParsedSimpleLinearEquation(
                    equation = match.value,
                    error = "Detected an unsupported coefficient in `${match.value}`.",
                )
            val rightSide = Rational.parse(match.groupValues[2])
                ?: return ParsedSimpleLinearEquation(
                    equation = match.value,
                    error = "Detected an unsupported right side in `${match.value}`.",
                )

            if (coefficient == Rational.ZERO) {
                return ParsedSimpleLinearEquation(
                    equation = match.value,
                    error = "Detected coefficient 0; cannot isolate x by division.",
                )
            }

            return ParsedSimpleLinearEquation(
                equation = match.value,
                coefficient = coefficient,
                rightSide = rightSide,
                solution = rightSide / coefficient,
            )
        }

        return null
    }

    private fun parseCoefficient(raw: String): Rational? {
        return when (val value = raw.trim()) {
            "", "+" -> Rational.ONE
            "-" -> Rational.NEGATIVE_ONE
            else -> Rational.parse(value)
        }
    }

    private data class ParsedSimpleLinearEquation(
        val equation: String,
        val coefficient: Rational? = null,
        val rightSide: Rational? = null,
        val solution: Rational? = null,
        val error: String? = null,
    )
}

internal data class Rational private constructor(
    val numerator: Long,
    val denominator: Long,
) {
    operator fun div(other: Rational): Rational {
        return of(numerator * other.denominator, denominator * other.numerator)
    }

    fun format(): String {
        return if (denominator == 1L) {
            numerator.toString()
        } else {
            "$numerator/$denominator"
        }
    }

    companion object {
        val ZERO = Rational(0, 1)
        val ONE = Rational(1, 1)
        val NEGATIVE_ONE = Rational(-1, 1)

        fun parse(raw: String): Rational? {
            val value = raw.trim()
            if (value.isBlank()) return null

            if ("/" in value) {
                val parts = value.split("/")
                if (parts.size != 2) return null
                val numerator = parts[0].toLongOrNull() ?: return null
                val denominator = parts[1].toLongOrNull() ?: return null
                if (denominator == 0L) return null
                return of(numerator, denominator)
            }

            if ("." in value) {
                return parseDecimal(value)
            }

            return value.toLongOrNull()?.let { of(it, 1) }
        }

        private fun parseDecimal(raw: String): Rational? {
            val negative = raw.startsWith("-")
            val unsigned = raw.removePrefix("+").removePrefix("-")
            val parts = unsigned.split(".")
            if (parts.size != 2 || parts[1].isEmpty()) return null

            val whole = parts[0].ifBlank { "0" }.toLongOrNull() ?: return null
            val fractional = parts[1].toLongOrNull() ?: return null
            val denominator = 10L.pow(parts[1].length)
            val numerator = whole * denominator + fractional
            return of(if (negative) -numerator else numerator, denominator)
        }

        private fun of(numerator: Long, denominator: Long): Rational {
            require(denominator != 0L) { "Denominator cannot be zero." }
            if (numerator == 0L) return ZERO

            val sign = if (denominator < 0) -1 else 1
            val normalizedNumerator = numerator * sign
            val normalizedDenominator = abs(denominator)
            val divisor = gcd(abs(normalizedNumerator), normalizedDenominator)
            return Rational(
                numerator = normalizedNumerator / divisor,
                denominator = normalizedDenominator / divisor,
            )
        }

        private fun gcd(left: Long, right: Long): Long {
            var a = left
            var b = right
            while (b != 0L) {
                val remainder = a % b
                a = b
                b = remainder
            }
            return if (a == 0L) 1L else a
        }

        private fun Long.pow(exponent: Int): Long {
            var result = 1L
            repeat(exponent) {
                result *= this
            }
            return result
        }
    }
}

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
        pattern = """\b(?:answer|solution|equals|equal to|is|vastus|lahendus|võrdub|on)\s*(?:x\s*)?(?:=|:)?\s*([+-]?(?:\d+(?:/\d+)?|\d*\.\d+))""",
        option = RegexOption.IGNORE_CASE,
    )

    fun buildLinearSanityCheck(problemText: String): String {
        val parsed = parseSimpleLinearEquation(problemText) ?: return "(none)"
        parsed.error?.let { return it }

        val coefficient = parsed.coefficient ?: return "(none)"
        val rightSide = parsed.rightSide ?: return "(none)"
        val solution = parsed.solution ?: return "(none)"
        return "Tuvastatud lihtne võrrand `${parsed.equation}`. " +
            "Pöördtehe on mõlema poole jagamine arvuga ${coefficient.format()}. " +
            "${rightSide.format()} / ${coefficient.format()} = ${solution.format()}, " +
            "seega x = ${solution.format()}. " +
            "Kui õpilane ütleb x = ${solution.format()}, kinnita see õigeks."
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
                    "Õpilane pakkus jagamist. Selle võrrandi puhul on see õige tehe. " +
                        "Ära küsi tehet uuesti. Jätka arvutusega: " +
                        "jaga mõlemad pooled arvuga ${coefficient.format()}, seega x = ${solution.format()}."
                }
                "multiplication" -> {
                    "Õpilane pakkus korrutamist. Selle võrrandi puhul on see vale. " +
                        "Võrrandis `${parsed.equation}` on x juba korrutatud arvuga ${coefficient.format()}, " +
                        "nii et uuesti korrutamine teeb avaldise keerulisemaks. " +
                        "Paranda seda rahulikult ja ütle, et pöördtehe on jagamine arvuga ${coefficient.format()}."
                }
                else -> {
                    "Õpilane pakkus tehet ${operationAttempt.toEstonianOperationName()}. Võrrandi `${parsed.equation}` puhul on see vale, " +
                        "sest x on korrutatud arvuga ${coefficient.format()}. Paranda seda rahulikult ja ütle, " +
                        "et vajalik pöördtehe on jagamine arvuga ${coefficient.format()}."
                }
            }
        }

        val answerAttempt = detectNumericAnswerAttempt(data.question)
        if (answerAttempt != null) {
            return if (answerAttempt == solution) {
                "Õpilane pakkus x = ${answerAttempt.format()}. See on õige. " +
                    "Kinnita seda otse ja näita kontrolli: ${coefficient.format()} * ${solution.format()} = ${rightSide.format()}."
            } else {
                "Õpilane pakkus x = ${answerAttempt.format()}. See ei ole võrrandi `${parsed.equation}` puhul õige. " +
                    "Õige arvutus on ${rightSide.format()} / ${coefficient.format()} = ${solution.format()}."
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
            return "Jah, jagamine on siin õige tehe. Võrrandis `${parsed.equation}` on `x` korrutatud arvuga `${coefficient.format()}`, " +
                "seega võtame selle tagasi, jagades mõlemad pooled arvuga `${coefficient.format()}`. " +
                "Saame `x = ${rightSide.format()} / ${coefficient.format()}`. Mis see lihtsustatult on?"
        }

        if (operationAttempt != null && operationAttempt in setOf("addition", "subtraction", "multiplication")) {
            return "Mitte päris. Võrrandis `${parsed.equation}` korrutatakse `x` arvuga `${coefficient.format()}`, " +
                "nii et ${operationAttempt.toEstonianOperationName()} ei jäta `x`-i üksinda. Pöördtehe on jagamine: " +
                "jaga mõlemad pooled arvuga `${coefficient.format()}`. Kui palju on `${rightSide.format()} / ${coefficient.format()}`?"
        }

        val answerAttempt = detectNumericAnswerAttempt(data.question)
        if (answerAttempt != null) {
            return if (answerAttempt == solution) {
                "Jah, `x = ${answerAttempt.format()}` on õige. Kontrollime asendamisega: " +
                    "`${coefficient.format()} * ${solution.format()} = ${rightSide.format()}`. " +
                    "See sobib algse võrrandiga."
            } else {
                "Mitte päris. Võrrandis `${parsed.equation}` jaga mõlemad pooled arvuga `${coefficient.format()}`: " +
                    "`x = ${rightSide.format()} / ${coefficient.format()} = ${solution.format()}`. " +
                    "Seega vastus on `x = ${solution.format()}`."
            }
        }

        return null
    }

    fun detectOperationAttempt(question: String): String? {
        val text = question.lowercase()
        val operationWords = listOf(
            "addition" to listOf("addition", "add", "plus", "liitmine", "liita", "liidan", "pluss", "juurde"),
            "subtraction" to listOf("subtraction", "subtract", "minus", "lahutamine", "lahuta", "lahutan", "miinus"),
            "multiplication" to listOf("multiplication", "multiply", "times", "korrutamine", "korruta", "korrutan", "korda"),
            "division" to listOf("division", "divide", "dividing", "divided", "÷", "jagamine", "jaga", "jagan", "jagada"),
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
                    error = "Tuvastati toetamata kordaja avaldises `${match.value}`.",
                )
            val rightSide = Rational.parse(match.groupValues[2])
                ?: return ParsedSimpleLinearEquation(
                    equation = match.value,
                    error = "Tuvastati toetamata parem pool avaldises `${match.value}`.",
                )

            if (coefficient == Rational.ZERO) {
                return ParsedSimpleLinearEquation(
                    equation = match.value,
                    error = "Tuvastati kordaja 0; x-i ei saa jagamisega eraldada.",
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

    private fun String.toEstonianOperationName(): String {
        return when (this) {
            "addition" -> "liitmine"
            "subtraction" -> "lahutamine"
            "multiplication" -> "korrutamine"
            "division" -> "jagamine"
            else -> this
        }
    }
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
            require(denominator != 0L) { "Nimetaja ei tohi olla null." }
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

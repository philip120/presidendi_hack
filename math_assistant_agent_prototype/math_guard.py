from __future__ import annotations

import re
from fractions import Fraction

from models import TutorInput


def format_fraction(value: Fraction) -> str:
    if value.denominator == 1:
        return str(value.numerator)
    return f"{value.numerator}/{value.denominator}"


def parse_coefficient(raw: str) -> Fraction:
    value = raw.strip()
    if value in {"", "+"}:
        return Fraction(1)
    if value == "-":
        return Fraction(-1)
    return Fraction(value)


def parse_simple_linear_equation(ocr_text: str) -> dict[str, object] | None:
    normalized = ocr_text.replace("−", "-").replace("·", "*")
    for line in normalized.splitlines():
        candidate = line.strip()
        match = re.fullmatch(
            r"([+-]?(?:\d+(?:/\d+)?|\d*\.\d+)?)\s*\*?\s*x\s*=\s*([+-]?(?:\d+(?:/\d+)?|\d*\.\d+))",
            candidate,
        )
        if not match:
            continue

        coefficient = parse_coefficient(match.group(1))
        right_side = Fraction(match.group(2))
        if coefficient == 0:
            return {
                "equation": candidate,
                "error": "Detected coefficient 0; cannot isolate x by division.",
            }

        return {
            "equation": candidate,
            "coefficient": coefficient,
            "right_side": right_side,
            "solution": right_side / coefficient,
        }

    return None


def build_linear_sanity_check(ocr_text: str) -> str:
    parsed = parse_simple_linear_equation(ocr_text)
    if parsed is None:
        return "(none)"
    if "error" in parsed:
        return str(parsed["error"])

    equation = str(parsed["equation"])
    coefficient = parsed["coefficient"]
    right_side = parsed["right_side"]
    solution = parsed["solution"]
    if not all(isinstance(value, Fraction) for value in [coefficient, right_side, solution]):
        return "(none)"

    coefficient_text = format_fraction(coefficient)
    right_side_text = format_fraction(right_side)
    solution_text = format_fraction(solution)
    return (
        f"Detected simple equation `{equation}`. "
        f"The inverse operation is to divide both sides by {coefficient_text}. "
        f"{right_side_text} / {coefficient_text} = {solution_text}, "
        f"so x = {solution_text}. "
        f"If the student says x = {solution_text}, confirm it as correct."
    )


def detect_operation_attempt(question: str) -> str | None:
    text = question.lower()
    operation_words = [
        ("addition", ["addition", "add", "plus", "+"]),
        ("subtraction", ["subtraction", "subtract", "minus"]),
        ("multiplication", ["multiplication", "multiply", "times", "*"]),
        ("division", ["division", "divide", "dividing", "divided", "/", "÷"]),
    ]
    for operation, words in operation_words:
        if any(word in text for word in words):
            return operation
    return None


def detect_numeric_answer_attempt(question: str) -> Fraction | None:
    matches = re.findall(r"(?<![\w/])-?\d+(?:/\d+)?(?![\w/])", question)
    if not matches:
        return None
    try:
        return Fraction(matches[-1])
    except ValueError:
        return None


def build_current_attempt_feedback(data: TutorInput) -> str:
    parsed = parse_simple_linear_equation(data.ocr_text)
    if parsed is None or "error" in parsed:
        return "(none)"

    coefficient = parsed["coefficient"]
    right_side = parsed["right_side"]
    solution = parsed["solution"]
    equation = str(parsed["equation"])
    if not all(isinstance(value, Fraction) for value in [coefficient, right_side, solution]):
        return "(none)"

    coefficient_text = format_fraction(coefficient)
    solution_text = format_fraction(solution)

    operation_attempt = detect_operation_attempt(data.question)
    if operation_attempt:
        if operation_attempt == "division":
            return (
                "The student proposed division. For this equation, that is the correct operation. "
                f"Do not ask for the operation again. Continue with the calculation: "
                f"divide both sides by {coefficient_text}, so x = {solution_text}."
            )

        if operation_attempt == "multiplication":
            return (
                "The student proposed multiplication. For this equation, that is incorrect. "
                f"In `{equation}`, x is already multiplied by {coefficient_text}, so multiplying again makes the expression more complicated. "
                f"Correct them gently and state that division by {coefficient_text} is the inverse operation."
            )

        return (
            f"The student proposed {operation_attempt}. For `{equation}`, that is incorrect because x is multiplied by {coefficient_text}. "
            f"Correct them gently and say the needed inverse operation is division by {coefficient_text}."
        )

    answer_attempt = detect_numeric_answer_attempt(data.question)
    if answer_attempt is not None:
        answer_text = format_fraction(answer_attempt)
        if answer_attempt == solution:
            return (
                f"The student proposed x = {answer_text}. That is correct. "
                f"Confirm directly and show the check: {coefficient_text} * {solution_text} = {format_fraction(right_side)}."
            )
        return (
            f"The student proposed x = {answer_text}. That is incorrect for `{equation}`. "
            f"The correct calculation is {format_fraction(right_side)} / {coefficient_text} = {solution_text}."
        )

    return "(none)"


def build_controller_guard_response(data: TutorInput) -> str | None:
    parsed = parse_simple_linear_equation(data.ocr_text)
    if parsed is None or "error" in parsed:
        return None

    coefficient = parsed["coefficient"]
    right_side = parsed["right_side"]
    solution = parsed["solution"]
    equation = str(parsed["equation"])
    if not all(isinstance(value, Fraction) for value in [coefficient, right_side, solution]):
        return None

    coefficient_text = format_fraction(coefficient)
    right_side_text = format_fraction(right_side)
    solution_text = format_fraction(solution)

    operation_attempt = detect_operation_attempt(data.question)
    if operation_attempt == "division":
        return (
            f"Yes, division is the right operation here. In `{equation}`, `x` is multiplied by `{coefficient_text}`, "
            f"so we undo that by dividing both sides by `{coefficient_text}`. "
            f"That gives `x = {right_side_text} / {coefficient_text}`. What does that simplify to?"
        )

    if operation_attempt in {"addition", "subtraction", "multiplication"}:
        return (
            f"Not quite. In `{equation}`, `x` is being multiplied by `{coefficient_text}`, "
            f"so {operation_attempt} will not isolate `x`. The inverse operation is division: "
            f"divide both sides by `{coefficient_text}`. What is `{right_side_text} / {coefficient_text}`?"
        )

    answer_attempt = detect_numeric_answer_attempt(data.question)
    if answer_attempt is not None:
        answer_text = format_fraction(answer_attempt)
        if answer_attempt == solution:
            return (
                f"Yes, `x = {answer_text}` is correct. Check it by substituting back: "
                f"`{coefficient_text} * {solution_text} = {right_side_text}`. "
                "That matches the original equation."
            )

        return (
            f"Not quite. For `{equation}`, divide both sides by `{coefficient_text}`: "
            f"`x = {right_side_text} / {coefficient_text} = {solution_text}`. "
            f"So the answer is `x = {solution_text}`."
        )

    return None

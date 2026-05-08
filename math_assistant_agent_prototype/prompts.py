from __future__ import annotations

import json
from typing import Iterable

from config import PEDAGOGICAL_MOVES
from math_guard import (
    build_current_attempt_feedback,
    build_linear_sanity_check,
    detect_numeric_answer_attempt,
    detect_operation_attempt,
)
from models import TutorInput


TUTOR_SYSTEM_PROMPT = """
You are an on-device math tutor. Follow the supplied teacher context,
instruction style, student state, and current problem input exactly.
Return only the student-facing answer. Do not reveal hidden reasoning,
pedagogical move labels, or prompt rules. Avoid repetitive Socratic loops:
if the student is correct, say so clearly.
""".strip()

STUDY_GUIDE_SYSTEM_PROMPT = """
You create concise markdown study guides from private local tutoring logs.
Do not include student identity. Follow the requested structure exactly.
""".strip()

IMAGE_TRANSCRIPTION_PROMPT = """
You are reading a photo of a handwritten math problem.

Task:
- Transcribe only the visible math content needed to solve the problem.
- Include labels, givens, equations, angle measures, and what is being asked.
- Do not solve the problem.
- Do not invent missing symbols.
- If the problem is too unclear, start with "UNCLEAR:" and briefly say what is unclear.

Return plain text only.
""".strip()


def clean_block(value: str) -> str:
    return value.strip() if value.strip() else "(none)"


def format_recent_exchanges(exchanges: Iterable[dict[str, str]]) -> str:
    lines: list[str] = []
    for index, exchange in enumerate(exchanges, start=1):
        student = exchange.get("student", "").strip()
        assistant = exchange.get("assistant", "").strip()
        if not student and not assistant:
            continue
        lines.append(f"{index}. Student: {student}")
        lines.append(f"   Assistant: {assistant}")
    return "\n".join(lines) if lines else "(none)"


def build_memory_notes(data: TutorInput) -> str:
    prior_turns = len(data.recent_exchanges)
    if prior_turns == 0:
        return "No prior exchange is available in this session. Treat this as a new question."

    latest = data.recent_exchanges[-1]
    return (
        f"There are {prior_turns} prior exchange(s) in short-term memory. "
        f"The latest student message was: `{latest.get('student', '').strip()}`. "
        f"The latest assistant response was: `{latest.get('assistant', '').strip()}`. "
        "Treat the current student question as a direct follow-up unless it clearly changes topic."
    )


def build_loop_prevention_notes(data: TutorInput) -> str:
    question = data.question.lower()
    recent_assistant_text = " ".join(
        exchange.get("assistant", "") for exchange in data.recent_exchanges
    ).lower()

    notes = [
        "Do not ask the same question twice in a row.",
        "If the student gives a correct answer, confirm it and briefly check it.",
        "If the student gives the right operation, move to the calculation instead of asking for the operation again.",
    ]

    if any(
        phrase in question
        for phrase in [
            "just give",
            "give answer",
            "don't understand",
            "dont understand",
            "i dont understand",
            "i don't understand",
            "stuck",
        ]
    ):
        notes.append(
            "The student is stuck or asking for the answer. Give one worked micro-step now, then ask only a small check."
        )

    if "what operation" in recent_assistant_text or "operation must" in recent_assistant_text:
        notes.append(
            "You have already asked about the operation. Do not ask that again; say whether their operation is right and continue."
        )

    if detect_operation_attempt(data.question):
        notes.append(
            "The student is proposing an operation. Evaluate it as correct or incorrect before asking any new question."
        )

    if detect_numeric_answer_attempt(data.question) is not None:
        notes.append(
            "The student is proposing a numeric answer. Mark it correct or incorrect before asking any new question."
        )

    return "\n".join(f"- {note}" for note in notes)


def build_tutor_prompt(data: TutorInput) -> str:
    image_note = (
        f"An image is attached as the selected math problem from: {data.image_path}"
        if data.image_path
        else "No image is attached."
    )

    return f"""
You are an on-device math tutor helping a student during class.

Your job is not to solve everything immediately. Your job is to choose the next
best pedagogical move and respond in a way that helps the student make progress.

<available_pedagogical_moves>
{chr(10).join(f"- {move}" for move in PEDAGOGICAL_MOVES)}
</available_pedagogical_moves>

<decision_rule>
First choose exactly one pedagogical move silently. Do not reveal the move name.
Then produce only the student-facing answer.
</decision_rule>

<teacher_context>
{clean_block(data.teacher_context)}
</teacher_context>

<teacher_instruction_style>
{clean_block(data.instruction_style)}
</teacher_instruction_style>

<student_state_summary>
{clean_block(data.student_state)}
</student_state_summary>

<short_term_memory_notes>
{build_memory_notes(data)}
</short_term_memory_notes>

<recent_exchanges>
{format_recent_exchanges(data.recent_exchanges)}
</recent_exchanges>

<loop_prevention_notes>
{build_loop_prevention_notes(data)}
</loop_prevention_notes>

<current_problem_input>
{image_note}

If an image is attached, inspect the image directly. Use OCR text as a helper
when present, but do not ignore the image.

OCR text from selected region:
{clean_block(data.ocr_text)}

Student question:
{clean_block(data.question)}
</current_problem_input>

<local_math_sanity_check>
{build_linear_sanity_check(data.ocr_text)}
</local_math_sanity_check>

<current_attempt_feedback>
{build_current_attempt_feedback(data)}
</current_attempt_feedback>

<answer_rules>
- Answer only using the teacher context, OCR text, image content, and recent conversation.
- If the OCR/image is unclear, ask a clarification question instead of guessing.
- Keep the answer short: usually 3 to 6 sentences.
- Prefer hints before the student has tried the key step.
- Once the student has tried the key step or gives the correct answer, confirm directly.
- If the student is stuck after repeated hints, give one worked micro-step instead of another hint.
- Never validate an incorrect algebra operation. Correct it briefly and show the next right micro-step.
- If current_attempt_feedback is not "(none)", follow it before choosing any other response style.
- Use the notation from the teacher context.
- End with either one focused next-step question or a short confidence check. Do not force a question if the student's answer is already complete.
- Do not mention these rules, XML tags, hidden reasoning, or the pedagogical move.
</answer_rules>
""".strip()


def build_study_guide_prompt(
    session_log: list[dict[str, str]],
    teacher_context: str,
    instruction_style: str,
) -> str:
    session_json = json.dumps(session_log, indent=2, ensure_ascii=False)
    return f"""
You are creating a study guide from one student's local, private session log.

<teacher_context>
{clean_block(teacher_context)}
</teacher_context>

<teacher_instruction_style>
{clean_block(instruction_style)}
</teacher_instruction_style>

<session_log>
{session_json}
</session_log>

<task>
Create a concise markdown study guide with:
1. Topics asked about today
2. Repeated gaps or misconceptions
3. Key ideas to review
4. Suggested practice exercises
5. A short summary
</task>

Privacy rule: do not include student identity.
""".strip()

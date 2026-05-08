from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class TutorInput:
    question: str
    ocr_text: str
    image_path: str | None
    teacher_context: str
    instruction_style: str
    student_state: str
    recent_exchanges: list[dict[str, str]]

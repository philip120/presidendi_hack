from __future__ import annotations

from pathlib import Path


OLLAMA_MODEL = "gemma4:26b"
GEMINI_MODEL = "gemini-3-flash-preview"

MODEL_REPO = "unsloth/gemma-4-26B-A4B-it-GGUF"
MODEL_FILE = "gemma-4-26B-A4B-it-UD-IQ4_XS.gguf"

DEEPSEEK_OCR_REPO = "deepseek-ai/DeepSeek-OCR"

HARDCODED_TEACHER_CONTEXT = """
Current topic: solving linear equations.

Class rules:
- Keep equations balanced by doing the same operation to both sides.
- Prefer isolating the variable one step at a time.
- Use simple algebra notation, not advanced shortcuts.

Example:
2x + 5 = 17
2x = 12
x = 6
"""

HARDCODED_INSTRUCTION_STYLE = """
Use Socratic questioning. Do not give the final answer immediately unless the
student has already shown the key step. Give one hint at a time. Use the same
notation used in class. Do not loop: once the student gives the right operation
or a correct answer, confirm it directly and explain why.
"""

HARDCODED_STUDENT_STATE = """
The student can move constants across the equals sign, but often forgets that
dividing by a negative changes the sign of the final value.
"""

HARDCODED_RECENT_EXCHANGES = [
    {
        "student": "I moved the 5, but I do not know what to do next.",
        "assistant": "Good. After subtracting 5 from both sides, what number is left on the right?",
    },
]

HARDCODED_QUESTION = "How should I solve this?"
HARDCODED_OCR_TEXT = ""

HARDCODED_IMAGE_PATHS = [
    "images/IMG_6128.jpeg",
    "images/IMG_6129.jpeg",
]
HARDCODED_IMAGE_INDEX = 0

HARDCODED_SESSION_LOG = [
    {
        "question": "Why do we subtract 5 from both sides?",
        "topic": "balancing equations",
        "answer": "Because the goal is to undo the +5 while keeping both sides equal.",
    },
    {
        "question": "Why do they divide by -2 here?",
        "topic": "isolating variables",
        "answer": "Because -2 is multiplying x, so division by -2 undoes that multiplication.",
    },
]

PEDAGOGICAL_MOVES = [
    "simplify",
    "give_example",
    "use_analogy",
    "ask_back",
    "confirm_understanding",
]


def get_hardcoded_image_path(index: int | None = None) -> str | None:
    if not HARDCODED_IMAGE_PATHS:
        if index is not None:
            raise ValueError(
                "No hardcoded images configured. Edit HARDCODED_IMAGE_PATHS in config.py."
            )
        return None

    selected_index = HARDCODED_IMAGE_INDEX if index is None else index
    if selected_index < 0 or selected_index >= len(HARDCODED_IMAGE_PATHS):
        raise ValueError(
            f"Image index {selected_index} is out of range. "
            f"Available indexes: 0..{len(HARDCODED_IMAGE_PATHS) - 1}"
        )
    return HARDCODED_IMAGE_PATHS[selected_index]


def list_hardcoded_images() -> str:
    if not HARDCODED_IMAGE_PATHS:
        return "No hardcoded images configured. Edit HARDCODED_IMAGE_PATHS in config.py."

    lines = ["Hardcoded images:"]
    for index, path in enumerate(HARDCODED_IMAGE_PATHS):
        marker = " default" if index == HARDCODED_IMAGE_INDEX else ""
        exists = "exists" if Path(path).expanduser().exists() else "missing"
        lines.append(f"  [{index}] {path} ({exists}){marker}")
    return "\n".join(lines)


def resolve_image_path(image: str | None, image_index: int | None) -> str | None:
    if image:
        return image
    if image_index is not None:
        return get_hardcoded_image_path(image_index)
    return get_hardcoded_image_path()

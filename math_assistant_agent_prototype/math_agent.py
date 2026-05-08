#!/usr/bin/env python3
"""
Small prototype for the on-device math assistant prompt workflow.

Edit the HARDCODED_* values below, or override them from the command line.
By default this prints the final prompt so you can inspect it. If you have
llama-cpp-python installed, pass --backend llama to run the GGUF model. If you
have Ollama running locally, pass --backend ollama to run through Ollama.
"""

from __future__ import annotations

import argparse
import base64
import json
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime
from fractions import Fraction
from pathlib import Path
from typing import Iterable


# ---------------------------------------------------------------------------
# Local GGUF model config.
# ---------------------------------------------------------------------------

MODEL_REPO = "unsloth/gemma-4-E4B-it-GGUF"
MODEL_FILE = "gemma-4-E4B-it-UD-Q8_K_XL.gguf"

_llama_model = None


# ---------------------------------------------------------------------------
# Hardcode demo inputs here.
# ---------------------------------------------------------------------------

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

HARDCODED_QUESTION = "Why do they divide by -2 here?"
HARDCODED_OCR_TEXT = "-2x = 6"
HARDCODED_IMAGE_PATH = None  # Example: "./equation_photo.jpg"

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


@dataclass(frozen=True)
class TutorInput:
    question: str
    ocr_text: str
    image_path: str | None
    teacher_context: str
    instruction_style: str
    student_state: str
    recent_exchanges: list[dict[str, str]]


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

        solution = right_side / coefficient
        return {
            "equation": candidate,
            "coefficient": coefficient,
            "right_side": right_side,
            "solution": solution,
        }

    return None


def build_linear_sanity_check(ocr_text: str) -> str:
    """
    Handles simple one-step equations like -2x = 6.

    This is intentionally tiny. It is not a symbolic math engine; it just gives
    the model a factual anchor for common classroom examples.
    """
    parsed = parse_simple_linear_equation(ocr_text)
    if parsed is None:
        return "(none)"
    if "error" in parsed:
        return str(parsed["error"])

    equation = str(parsed["equation"])
    coefficient = parsed["coefficient"]
    right_side = parsed["right_side"]
    solution = parsed["solution"]
    if not isinstance(coefficient, Fraction):
        return "(none)"
    if not isinstance(right_side, Fraction):
        return "(none)"
    if not isinstance(solution, Fraction):
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
    if not isinstance(coefficient, Fraction):
        return "(none)"
    if not isinstance(right_side, Fraction):
        return "(none)"
    if not isinstance(solution, Fraction):
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
    """
    Deterministic tutor guard for tiny algebra cases.

    This prevents the model from getting stuck in a Socratic loop when the
    student proposes an operation or answer that can be checked locally.
    """
    parsed = parse_simple_linear_equation(data.ocr_text)
    if parsed is None or "error" in parsed:
        return None

    coefficient = parsed["coefficient"]
    right_side = parsed["right_side"]
    solution = parsed["solution"]
    equation = str(parsed["equation"])
    if not isinstance(coefficient, Fraction):
        return None
    if not isinstance(right_side, Fraction):
        return None
    if not isinstance(solution, Fraction):
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


def build_memory_notes(data: TutorInput) -> str:
    prior_turns = len(data.recent_exchanges)
    if prior_turns == 0:
        return (
            "No prior exchange is available in this session. Treat this as a new question."
        )

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

    notes: list[str] = [
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
    """
    Builds a Claude-Code-inspired structured prompt.

    The app/tool layer is deterministic: capture image, OCR, assemble prompt.
    The model only chooses the pedagogical move and produces a student-facing
    answer.
    """

    image_note = (
        f"An image is attached from: {data.image_path}"
        if data.image_path
        else "No image is attached."
    )

    moves = "\n".join(f"- {move}" for move in PEDAGOGICAL_MOVES)
    linear_sanity_check = build_linear_sanity_check(data.ocr_text)
    loop_prevention_notes = build_loop_prevention_notes(data)
    memory_notes = build_memory_notes(data)
    current_attempt_feedback = build_current_attempt_feedback(data)

    return f"""
You are an on-device math tutor helping a student during class.

Your job is not to solve everything immediately. Your job is to choose the next
best pedagogical move and respond in a way that helps the student make progress.

<available_pedagogical_moves>
{moves}
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
{memory_notes}
</short_term_memory_notes>

<recent_exchanges>
{format_recent_exchanges(data.recent_exchanges)}
</recent_exchanges>

<loop_prevention_notes>
{loop_prevention_notes}
</loop_prevention_notes>

<current_problem_input>
{image_note}

OCR text from selected region:
{clean_block(data.ocr_text)}

Student question:
{clean_block(data.question)}
</current_problem_input>

<local_math_sanity_check>
{linear_sanity_check}
</local_math_sanity_check>

<current_attempt_feedback>
{current_attempt_feedback}
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


def image_to_base64(path: str) -> str:
    image_path = Path(path).expanduser()
    if not image_path.exists():
        raise FileNotFoundError(f"Image not found: {image_path}")
    return base64.b64encode(image_path.read_bytes()).decode("ascii")


def infer_topic(question: str, ocr_text: str) -> str:
    text = f"{question} {ocr_text}".lower()
    if any(word in text for word in ["divide", "dividing", "/", "÷"]):
        return "division while isolating a variable"
    if any(word in text for word in ["subtract", "minus", "-"]):
        return "balancing equations with subtraction"
    if any(word in text for word in ["bracket", "parentheses", "distribute"]):
        return "distributive rule"
    if any(word in text for word in ["sign", "negative", "-"]):
        return "negative signs"
    if any(word in text for word in ["solve", "equation", "x"]):
        return "solving equations"
    return "uncategorized math question"


def summarize_session_locally(
    session_log: list[dict[str, str]],
    previous_summary: str,
) -> str:
    """
    Tiny deterministic stand-in for the app's cheap summarization pass.

    In the Flutter app this would be a small Gemma call every few turns. For
    this prototype, keep it local so --backend mock can demo context updates.
    """
    topics = []
    for event in session_log:
        topic = event.get("topic", "").strip()
        if topic and topic not in topics:
            topics.append(topic)

    recent_questions = [event["question"] for event in session_log[-3:]]
    return (
        f"{clean_block(previous_summary)}\n\n"
        f"Session so far: {len(session_log)} question(s). "
        f"Topics: {', '.join(topics) if topics else 'none yet'}. "
        f"Recent questions: {' | '.join(recent_questions) if recent_questions else 'none'}."
    ).strip()


def mock_tutor_answer(data: TutorInput) -> str:
    """
    Deterministic fake answer for demos without a local model.

    This is intentionally simple. It tests the Q&A loop, not model quality.
    """
    question = data.question.lower()
    ocr = data.ocr_text.strip()

    if not ocr:
        return (
            "I need the selected equation or step before I can help. "
            "Can you crop the exact line you want to ask about?"
        )

    if "divide" in question or "/" in question or "÷" in question:
        return (
            f"In `{ocr}`, the number next to `x` is multiplying the variable. "
            "To isolate `x`, use the opposite operation, which is division. "
            "Do that to both sides so the equation stays balanced. "
            "What do you get when you divide the right side by that same number?"
        )

    if "next" in question or "stuck" in question or "do" in question:
        return (
            f"Look at `{ocr}` and find what operation is still attached to `x`. "
            "Your next step is to undo that operation on both sides. "
            "Which operation would undo it?"
        )

    if "right" in question or "correct" in question:
        return (
            f"I can check the step shown as `{ocr}`, but I need your attempted next line too. "
            "What did you write after this line?"
        )

    return (
        f"The key idea in `{ocr}` is to keep both sides balanced while isolating `x`. "
        "Focus on the operation attached to the variable and undo just that operation. "
        "What is the smallest next step you can take?"
    )


def load_llama_model(
    *,
    repo_id: str = MODEL_REPO,
    filename: str = MODEL_FILE,
    n_ctx: int = 8192,
    n_gpu_layers: int = 0,
):
    """
    Lazily load the GGUF model once and keep it cached globally.

    This mirrors the pattern you pasted, but imports llama_cpp only when the
    llama backend is actually used. That keeps --backend print usable on
    machines without llama-cpp-python installed.
    """
    global _llama_model
    if _llama_model is not None:
        return _llama_model

    try:
        from llama_cpp import Llama
    except ImportError as exc:
        raise RuntimeError(
            "Missing dependency: llama-cpp-python. Install it with "
            "`pip install llama-cpp-python`."
        ) from exc

    print(f"Loading {filename} from {repo_id} ...", file=sys.stderr)
    _llama_model = Llama.from_pretrained(
        repo_id=repo_id,
        filename=filename,
        n_ctx=n_ctx,
        n_gpu_layers=n_gpu_layers,
        verbose=False,
    )
    print("Model ready.", file=sys.stderr)
    return _llama_model


def call_llama_cpp(
    prompt: str,
    *,
    system_prompt: str,
    repo_id: str,
    filename: str,
    n_ctx: int,
    n_gpu_layers: int,
    max_tokens: int,
    temperature: float,
) -> str:
    model = load_llama_model(
        repo_id=repo_id,
        filename=filename,
        n_ctx=n_ctx,
        n_gpu_layers=n_gpu_layers,
    )
    response = model.create_chat_completion(
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": prompt},
        ],
        max_tokens=max_tokens,
        temperature=temperature,
    )
    return response["choices"][0]["message"]["content"].strip()


def call_ollama(
    prompt: str,
    *,
    model: str,
    image_path: str | None,
    host: str,
    stream: bool,
) -> str:
    payload: dict[str, object] = {
        "model": model,
        "prompt": prompt,
        "stream": stream,
    }

    if image_path:
        payload["images"] = [image_to_base64(image_path)]

    request = urllib.request.Request(
        f"{host.rstrip('/')}/api/generate",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            if stream:
                chunks: list[str] = []
                for raw_line in response:
                    if not raw_line.strip():
                        continue
                    event = json.loads(raw_line)
                    token = event.get("response", "")
                    if token:
                        print(token, end="", flush=True)
                        chunks.append(token)
                    if event.get("done"):
                        print()
                        break
                return "".join(chunks)

            body = json.loads(response.read().decode("utf-8"))
            return str(body.get("response", "")).strip()
    except urllib.error.URLError as exc:
        raise RuntimeError(
            "Could not reach Ollama. Start it with `ollama serve`, then try again."
        ) from exc


def answer_once(
    *,
    backend: str,
    prompt: str,
    tutor_input: TutorInput,
    system_prompt: str,
    args: argparse.Namespace,
    image_path: str | None,
) -> str:
    if not args.disable_controller_guard:
        controller_response = build_controller_guard_response(tutor_input)
        if controller_response is not None:
            return controller_response

    if backend == "mock":
        return mock_tutor_answer(tutor_input)

    if backend == "llama":
        if image_path:
            print(
                "Warning: the llama GGUF backend is text-only here. "
                "Use OCR text via --ocr, or use --backend ollama with a "
                "vision-capable model for image bytes.",
                file=sys.stderr,
            )
        return call_llama_cpp(
            prompt,
            system_prompt=system_prompt,
            repo_id=args.llama_repo,
            filename=args.llama_file,
            n_ctx=args.n_ctx,
            n_gpu_layers=args.n_gpu_layers,
            max_tokens=args.max_tokens,
            temperature=args.temperature,
        )

    if backend == "ollama":
        return call_ollama(
            prompt,
            model=args.model,
            image_path=image_path,
            host=args.ollama_host,
            stream=args.stream,
        )

    raise ValueError(f"Unsupported answer backend: {backend}")


def run_chat_loop(args: argparse.Namespace) -> int:
    print("Math agent Q&A session")
    print("Commands: /ocr <text>, /image <path>, /study-guide, /quit")
    print()

    ocr_text = args.ocr
    image_path = args.image
    student_state = HARDCODED_STUDENT_STATE
    recent_exchanges: list[dict[str, str]] = []
    session_log: list[dict[str, str]] = []

    while True:
        try:
            question = input("Student> ").strip()
        except EOFError:
            print()
            break

        if not question:
            continue

        if question in {"/q", "/quit", "quit", "exit"}:
            break

        if question.startswith("/ocr "):
            ocr_text = question.removeprefix("/ocr ").strip()
            print(f"OCR set to: {ocr_text or '(empty)'}")
            continue

        if question.startswith("/image "):
            image_path = question.removeprefix("/image ").strip() or None
            print(f"Image set to: {image_path or '(none)'}")
            continue

        if question == "/study-guide":
            prompt = build_study_guide_prompt(
                session_log or HARDCODED_SESSION_LOG,
                HARDCODED_TEACHER_CONTEXT,
                HARDCODED_INSTRUCTION_STYLE,
            )
            if args.backend == "print":
                print(prompt)
            elif args.backend == "mock":
                print(
                    "Assistant> Study guide demo: review the topics you asked about, "
                    "especially the repeated equation-balancing steps."
                )
            else:
                answer = answer_once(
                    backend=args.backend,
                    prompt=prompt,
                    tutor_input=TutorInput(
                        question="Create study guide",
                        ocr_text="",
                        image_path=None,
                        teacher_context=HARDCODED_TEACHER_CONTEXT,
                        instruction_style=HARDCODED_INSTRUCTION_STYLE,
                        student_state=student_state,
                        recent_exchanges=recent_exchanges[-3:],
                    ),
                    system_prompt=STUDY_GUIDE_SYSTEM_PROMPT,
                    args=args,
                    image_path=None,
                )
                if not args.stream:
                    print(f"Assistant>\n{answer}")
            continue

        tutor_input = TutorInput(
            question=question,
            ocr_text=ocr_text,
            image_path=image_path,
            teacher_context=HARDCODED_TEACHER_CONTEXT,
            instruction_style=HARDCODED_INSTRUCTION_STYLE,
            student_state=student_state,
            recent_exchanges=recent_exchanges[-3:],
        )
        prompt = build_tutor_prompt(tutor_input)

        if args.backend == "print":
            print(prompt)
            answer = "[prompt printed only; no model answer generated]"
        else:
            answer = answer_once(
                backend=args.backend,
                prompt=prompt,
                tutor_input=tutor_input,
                system_prompt=TUTOR_SYSTEM_PROMPT,
                args=args,
                image_path=image_path,
            )
            if not args.stream:
                print(f"Assistant> {answer}")

        topic = infer_topic(question, ocr_text)
        recent_exchanges.append({"student": question, "assistant": answer})
        session_log.append(
            {
                "question": question,
                "topic": topic,
                "answer": answer,
                "timestamp": datetime.now().isoformat(timespec="seconds"),
            }
        )

        if len(session_log) % 3 == 0:
            student_state = summarize_session_locally(session_log, student_state)
            print("[context] student state summary refreshed")

    print("Session ended.")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Prototype math tutor prompt builder / local runner."
    )
    parser.add_argument(
        "--question",
        default=HARDCODED_QUESTION,
        help="Student question. Defaults to HARDCODED_QUESTION in the script.",
    )
    parser.add_argument(
        "--ocr",
        default=HARDCODED_OCR_TEXT,
        help="OCR text from the selected image region.",
    )
    parser.add_argument(
        "--image",
        default=HARDCODED_IMAGE_PATH,
        help="Optional image path. Used by vision-capable Ollama models.",
    )
    parser.add_argument(
        "--backend",
        choices=["print", "mock", "llama", "ollama"],
        default="print",
        help="Use 'print' to inspect, 'mock' to demo, 'llama' for GGUF, or 'ollama'.",
    )
    parser.add_argument(
        "--llama-repo",
        default=MODEL_REPO,
        help="Hugging Face repo id for llama-cpp-python from_pretrained.",
    )
    parser.add_argument(
        "--llama-file",
        default=MODEL_FILE,
        help="GGUF filename inside --llama-repo.",
    )
    parser.add_argument(
        "--n-ctx",
        type=int,
        default=8192,
        help="Context window for llama-cpp-python.",
    )
    parser.add_argument(
        "--n-gpu-layers",
        type=int,
        default=0,
        help="GPU layers for llama-cpp-python. Use 0 for CPU only.",
    )
    parser.add_argument(
        "--max-tokens",
        type=int,
        default=512,
        help="Maximum generated tokens for local model backends.",
    )
    parser.add_argument(
        "--temperature",
        type=float,
        default=0.2,
        help="Sampling temperature for llama-cpp-python.",
    )
    parser.add_argument(
        "--model",
        default="gemma3:4b",
        help="Ollama model name. Use a vision-capable model if passing --image.",
    )
    parser.add_argument(
        "--ollama-host",
        default="http://localhost:11434",
        help="Ollama host URL.",
    )
    parser.add_argument(
        "--stream",
        action="store_true",
        help="Stream Ollama output token-by-token.",
    )
    parser.add_argument(
        "--study-guide",
        action="store_true",
        help="Build/run the session-end study guide prompt instead.",
    )
    parser.add_argument(
        "--chat",
        action="store_true",
        help="Run an interactive Q&A session that keeps recent context.",
    )
    parser.add_argument(
        "--disable-controller-guard",
        action="store_true",
        help="Disable deterministic local corrections for simple algebra attempts.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    if args.chat:
        return run_chat_loop(args)

    if args.study_guide:
        prompt = build_study_guide_prompt(
            HARDCODED_SESSION_LOG,
            HARDCODED_TEACHER_CONTEXT,
            HARDCODED_INSTRUCTION_STYLE,
        )
        image_path = None
        system_prompt = STUDY_GUIDE_SYSTEM_PROMPT
        tutor_input = TutorInput(
            question="Create study guide",
            ocr_text="",
            image_path=None,
            teacher_context=HARDCODED_TEACHER_CONTEXT,
            instruction_style=HARDCODED_INSTRUCTION_STYLE,
            student_state=HARDCODED_STUDENT_STATE,
            recent_exchanges=HARDCODED_RECENT_EXCHANGES,
        )
    else:
        tutor_input = TutorInput(
            question=args.question,
            ocr_text=args.ocr,
            image_path=args.image,
            teacher_context=HARDCODED_TEACHER_CONTEXT,
            instruction_style=HARDCODED_INSTRUCTION_STYLE,
            student_state=HARDCODED_STUDENT_STATE,
            recent_exchanges=HARDCODED_RECENT_EXCHANGES,
        )
        prompt = build_tutor_prompt(tutor_input)
        image_path = args.image
        system_prompt = TUTOR_SYSTEM_PROMPT

    if args.backend == "print":
        print(prompt)
        return 0

    answer = answer_once(
        backend=args.backend,
        prompt=prompt,
        tutor_input=tutor_input,
        system_prompt=system_prompt,
        args=args,
        image_path=image_path,
    )

    if not args.stream:
        print(answer)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        raise SystemExit(1)

from __future__ import annotations

from datetime import datetime
from types import SimpleNamespace

from config import (
    HARDCODED_INSTRUCTION_STYLE,
    HARDCODED_SESSION_LOG,
    HARDCODED_STUDENT_STATE,
    HARDCODED_TEACHER_CONTEXT,
    get_hardcoded_image_path,
    list_hardcoded_images,
)
from llm_backends import answer_once
from models import TutorInput
from ocr_backends import transcribe_image
from prompts import (
    STUDY_GUIDE_SYSTEM_PROMPT,
    TUTOR_SYSTEM_PROMPT,
    build_study_guide_prompt,
    build_tutor_prompt,
    clean_block,
)


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


def run_ocr_pipeline(args: SimpleNamespace, image_path: str) -> str:
    return transcribe_image(
        backend=args.ocr_backend,
        image_path=image_path,
        ollama_model=args.ocr_model or args.model,
        ollama_host=args.ollama_host,
        deepseek_repo=args.deepseek_repo,
        deepseek_device=args.deepseek_device,
        deepseek_output_dir=args.deepseek_output_dir,
        deepseek_base_size=args.deepseek_base_size,
        deepseek_image_size=args.deepseek_image_size,
        deepseek_crop_mode=args.deepseek_crop_mode,
        ocr_max_dim=args.ocr_max_dim,
        ocr_jpeg_quality=args.ocr_jpeg_quality,
        ocr_prompt=args.ocr_prompt,
    )


def answer_from_runtime(
    *,
    args: SimpleNamespace,
    prompt: str,
    tutor_input: TutorInput,
    system_prompt: str,
    image_path: str | None,
) -> str:
    return answer_once(
        backend=args.backend,
        prompt=prompt,
        tutor_input=tutor_input,
        system_prompt=system_prompt,
        image_path=image_path,
        disable_controller_guard=args.disable_controller_guard,
        ollama_model=args.model,
        ollama_host=args.ollama_host,
        stream=args.stream,
        image_max_dim=args.image_max_dim,
        image_jpeg_quality=args.image_jpeg_quality,
        llama_repo=args.llama_repo,
        llama_file=args.llama_file,
        n_ctx=args.n_ctx,
        n_gpu_layers=args.n_gpu_layers,
        max_tokens=args.max_tokens,
        temperature=args.temperature,
    )


def run_chat_loop(args: SimpleNamespace) -> int:
    print("Math agent Q&A session")

    ocr_text = args.ocr
    image_path = args.image

    if args.auto_transcribe_image and image_path and not ocr_text:
        print(f"Transcribing image with {args.ocr_backend}: {image_path}")
        ocr_text = run_ocr_pipeline(args, image_path)
        print(f"Image transcript: {ocr_text or '(empty)'}")

    print(
        "Commands: /ocr <text>, /clear-ocr, /transcribe, /image <path-or-index>, "
        "/clear-image, /images, /study-guide, /quit"
    )
    print(f"Initial OCR: {ocr_text or '(none)'}")
    print(f"Initial image: {image_path or '(none)'}")
    print()

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

        if question in {"/ocr", "/clear-ocr"}:
            ocr_text = ""
            print("OCR cleared.")
            continue

        if question.startswith("/ocr "):
            ocr_text = question.removeprefix("/ocr ").strip()
            print(f"OCR set to: {ocr_text or '(empty)'}")
            continue

        if question == "/transcribe":
            if not image_path:
                print("No image selected.")
                continue
            print(f"Transcribing image with {args.ocr_backend}: {image_path}")
            ocr_text = run_ocr_pipeline(args, image_path)
            print(f"Image transcript: {ocr_text or '(empty)'}")
            continue

        if question == "/images":
            print(list_hardcoded_images())
            continue

        if question == "/clear-image":
            image_path = None
            print("Image cleared.")
            continue

        if question.startswith("/image "):
            image_arg = question.removeprefix("/image ").strip()
            if image_arg.isdigit():
                image_path = get_hardcoded_image_path(int(image_arg))
            else:
                image_path = image_arg or None
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
                answer = answer_from_runtime(
                    args=args,
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
            answer = answer_from_runtime(
                args=args,
                prompt=prompt,
                tutor_input=tutor_input,
                system_prompt=TUTOR_SYSTEM_PROMPT,
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

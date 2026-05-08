#!/usr/bin/env python3
"""
CLI for the modular math assistant prototype.

Pipeline:
  image and/or OCR text -> tutoring prompt -> answer backend
"""

from __future__ import annotations

import argparse
import sys

from config import (
    DEEPSEEK_OCR_REPO,
    HARDCODED_INSTRUCTION_STYLE,
    HARDCODED_OCR_TEXT,
    HARDCODED_QUESTION,
    HARDCODED_RECENT_EXCHANGES,
    HARDCODED_SESSION_LOG,
    HARDCODED_STUDENT_STATE,
    HARDCODED_TEACHER_CONTEXT,
    MODEL_FILE,
    MODEL_REPO,
    OLLAMA_MODEL,
    list_hardcoded_images,
    resolve_image_path,
)
from models import TutorInput
from ocr_backends import transcribe_image
from prompts import (
    STUDY_GUIDE_SYSTEM_PROMPT,
    TUTOR_SYSTEM_PROMPT,
    build_study_guide_prompt,
    build_tutor_prompt,
)
from session import answer_from_runtime, run_chat_loop


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Prototype math tutor prompt builder / local runner."
    )
    parser.add_argument(
        "--question",
        default=HARDCODED_QUESTION,
        help="Student question. Defaults to HARDCODED_QUESTION in config.py.",
    )
    parser.add_argument(
        "--ocr",
        default=HARDCODED_OCR_TEXT,
        help="OCR text from the selected image region.",
    )
    parser.add_argument(
        "--image",
        default=None,
        help="Optional explicit image path.",
    )
    parser.add_argument(
        "--image-index",
        type=int,
        default=None,
        help="Pick an image from HARDCODED_IMAGE_PATHS by index.",
    )
    parser.add_argument(
        "--list-images",
        action="store_true",
        help="List HARDCODED_IMAGE_PATHS and exit.",
    )
    parser.add_argument(
        "--image-only",
        action="store_true",
        help="Clear OCR text so the backend relies on the attached image directly.",
    )
    parser.add_argument(
        "--pure-image",
        action="store_true",
        help="Use the selected image directly with no OCR prepass.",
    )
    parser.add_argument(
        "--auto-transcribe-image",
        action="store_true",
        help="Run OCR on the selected image first, then tutor from that transcript.",
    )
    parser.add_argument(
        "--transcribe-only",
        action="store_true",
        help="Transcribe the selected image and exit without tutoring.",
    )
    parser.add_argument(
        "--ocr-backend",
        choices=["ollama", "deepseek"],
        default="ollama",
        help="OCR backend used only by --auto-transcribe-image, --transcribe-only, /transcribe.",
    )
    parser.add_argument(
        "--ocr-model",
        default=None,
        help="Ollama model for OCR. Defaults to --model when omitted.",
    )
    parser.add_argument(
        "--ocr-max-dim",
        type=int,
        default=1280,
        help="Downscale OCR images to this max width/height. Use 0 to disable.",
    )
    parser.add_argument(
        "--ocr-jpeg-quality",
        type=int,
        default=90,
        help="JPEG quality for temporary OCR image preprocessing.",
    )
    parser.add_argument(
        "--ocr-prompt",
        default=None,
        help="Override the OCR prompt, e.g. 'Free OCR.' or 'Parse the figure.'.",
    )
    parser.add_argument(
        "--image-max-dim",
        type=int,
        default=1280,
        help="Downscale images sent directly to vision models. Use 0 to disable.",
    )
    parser.add_argument(
        "--image-jpeg-quality",
        type=int,
        default=90,
        help="JPEG quality for temporary direct-vision image preprocessing.",
    )

    parser.add_argument(
        "--backend",
        choices=["print", "mock", "llama", "ollama"],
        default="print",
        help="Answer backend: print prompt, mock demo, llama GGUF, or Ollama.",
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
        default=OLLAMA_MODEL,
        help="Ollama model name. Use a vision-capable model for image bytes.",
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
        "--deepseek-repo",
        default=DEEPSEEK_OCR_REPO,
        help="DeepSeek-OCR Hugging Face repo.",
    )
    parser.add_argument(
        "--deepseek-device",
        choices=["auto", "cuda", "mps", "cpu"],
        default="auto",
        help="Device for DeepSeek-OCR.",
    )
    parser.add_argument(
        "--deepseek-output-dir",
        default="ocr_outputs",
        help="Output directory required by DeepSeek-OCR infer().",
    )
    parser.add_argument(
        "--deepseek-base-size",
        type=int,
        default=1024,
        help="DeepSeek-OCR base_size.",
    )
    parser.add_argument(
        "--deepseek-image-size",
        type=int,
        default=640,
        help="DeepSeek-OCR image_size.",
    )
    parser.add_argument(
        "--deepseek-no-crop",
        dest="deepseek_crop_mode",
        action="store_false",
        help="Disable DeepSeek-OCR crop mode.",
    )
    parser.set_defaults(deepseek_crop_mode=True)

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


def prepare_args(args: argparse.Namespace) -> argparse.Namespace:
    args.image = resolve_image_path(args.image, args.image_index)

    if args.pure_image:
        args.image_only = True

    if args.image_only:
        args.ocr = ""

    return args


def run_transcribe_only(args: argparse.Namespace) -> int:
    if not args.image:
        raise RuntimeError("No image selected. Use --image-index 0 or --image path.")

    transcript = transcribe_image(
        backend=args.ocr_backend,
        image_path=args.image,
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
    print(transcript)
    return 0


def run_one_shot(args: argparse.Namespace) -> int:
    if (
        args.auto_transcribe_image
        and args.image
        and not args.ocr
        and not args.study_guide
    ):
        args.ocr = transcribe_image(
            backend=args.ocr_backend,
            image_path=args.image,
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

    if args.study_guide:
        prompt = build_study_guide_prompt(
            HARDCODED_SESSION_LOG,
            HARDCODED_TEACHER_CONTEXT,
            HARDCODED_INSTRUCTION_STYLE,
        )
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
        image_path = None
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
        system_prompt = TUTOR_SYSTEM_PROMPT
        image_path = args.image

    if args.backend == "print":
        print(prompt)
        return 0

    answer = answer_from_runtime(
        args=args,
        prompt=prompt,
        tutor_input=tutor_input,
        system_prompt=system_prompt,
        image_path=image_path,
    )
    if not args.stream:
        print(answer)
    return 0


def main() -> int:
    args = prepare_args(parse_args())

    if args.list_images:
        print(list_hardcoded_images())
        return 0

    if args.transcribe_only:
        return run_transcribe_only(args)

    if args.chat:
        return run_chat_loop(args)

    return run_one_shot(args)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        raise SystemExit(1)

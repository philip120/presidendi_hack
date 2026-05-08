from __future__ import annotations

from pathlib import Path

from config import DEEPSEEK_OCR_REPO
from image_preprocess import prepare_image_for_ocr
from llm_backends import call_ollama, call_ollama_chat
from prompts import IMAGE_TRANSCRIPTION_PROMPT


_deepseek_model = None
_deepseek_tokenizer = None


def transcribe_with_ollama(
    *,
    image_path: str,
    model: str,
    host: str,
    prompt: str | None,
) -> str:
    if "deepseek-ocr" in model.lower():
        return call_ollama_chat(
            content=prompt or "Free OCR.",
            model=model,
            image_path=image_path,
            host=host,
        ).strip()

    return call_ollama(
        prompt or IMAGE_TRANSCRIPTION_PROMPT,
        model=model,
        image_path=image_path,
        host=host,
        stream=False,
    ).strip()


def load_deepseek_ocr(
    *,
    repo_id: str = DEEPSEEK_OCR_REPO,
    device: str = "auto",
):
    global _deepseek_model, _deepseek_tokenizer
    if _deepseek_model is not None and _deepseek_tokenizer is not None:
        return _deepseek_model, _deepseek_tokenizer

    try:
        import torch
        from transformers import AutoModel, AutoTokenizer
    except ImportError as exc:
        raise RuntimeError(
            "Missing DeepSeek-OCR dependencies. Install optional requirements with "
            "`pip install -r requirements-deepseek.txt`."
        ) from exc

    if device == "auto":
        if torch.cuda.is_available():
            device = "cuda"
        elif getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
            device = "mps"
        else:
            device = "cpu"

    tokenizer = AutoTokenizer.from_pretrained(repo_id, trust_remote_code=True)
    model = AutoModel.from_pretrained(
        repo_id,
        trust_remote_code=True,
        use_safetensors=True,
    ).eval()

    if device == "cuda":
        model = model.cuda().to(torch.bfloat16)
    elif device == "mps":
        model = model.to("mps")
    else:
        model = model.to("cpu")

    _deepseek_model = model
    _deepseek_tokenizer = tokenizer
    return model, tokenizer


def transcribe_with_deepseek(
    *,
    image_path: str,
    repo_id: str,
    device: str,
    output_dir: str,
    base_size: int,
    image_size: int,
    crop_mode: bool,
) -> str:
    model, tokenizer = load_deepseek_ocr(repo_id=repo_id, device=device)
    Path(output_dir).mkdir(parents=True, exist_ok=True)

    result = model.infer(
        tokenizer,
        prompt="<image>\nFree OCR.",
        image_file=image_path,
        output_path=output_dir,
        base_size=base_size,
        image_size=image_size,
        crop_mode=crop_mode,
        save_results=False,
        test_compress=False,
    )
    return str(result).strip()


def transcribe_image(
    *,
    backend: str,
    image_path: str,
    ollama_model: str,
    ollama_host: str,
    deepseek_repo: str,
    deepseek_device: str,
    deepseek_output_dir: str,
    deepseek_base_size: int,
    deepseek_image_size: int,
    deepseek_crop_mode: bool,
    ocr_max_dim: int,
    ocr_jpeg_quality: int,
    ocr_prompt: str | None,
) -> str:
    prepared_path, should_delete = prepare_image_for_ocr(
        image_path,
        max_dim=ocr_max_dim,
        quality=ocr_jpeg_quality,
    )
    try:
        if backend == "ollama":
            return transcribe_with_ollama(
                image_path=prepared_path,
                model=ollama_model,
                host=ollama_host,
                prompt=ocr_prompt,
            )
        if backend == "deepseek":
            return transcribe_with_deepseek(
                image_path=prepared_path,
                repo_id=deepseek_repo,
                device=deepseek_device,
                output_dir=deepseek_output_dir,
                base_size=deepseek_base_size,
                image_size=deepseek_image_size,
                crop_mode=deepseek_crop_mode,
            )
        raise ValueError(f"Unsupported OCR backend: {backend}")
    finally:
        if should_delete:
            Path(prepared_path).unlink(missing_ok=True)

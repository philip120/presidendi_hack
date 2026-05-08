from __future__ import annotations

import base64
import json
import mimetypes
import os
import shutil
import sys
import urllib.error
import urllib.request
from pathlib import Path

from config import MODEL_FILE, MODEL_REPO
from image_preprocess import prepare_image_for_model
from math_guard import build_controller_guard_response
from models import TutorInput


_llama_model = None


def image_to_base64(path: str) -> str:
    image_path = Path(path).expanduser()
    if not image_path.exists():
        raise FileNotFoundError(f"Image not found: {image_path}")
    return base64.b64encode(image_path.read_bytes()).decode("ascii")


def call_ollama(
    prompt: str,
    *,
    model: str,
    image_path: str | None,
    host: str,
    stream: bool,
    image_max_dim: int = 0,
    image_jpeg_quality: int = 90,
) -> str:
    payload: dict[str, object] = {
        "model": model,
        "prompt": prompt,
        "stream": stream,
    }

    prepared_image_path: str | None = None
    should_delete_image = False
    if image_path:
        prepared_image_path, should_delete_image = prepare_image_for_model(
            image_path,
            max_dim=image_max_dim,
            quality=image_jpeg_quality,
        )
        payload["images"] = [image_to_base64(prepared_image_path)]

    try:
        request = urllib.request.Request(
            f"{host.rstrip('/')}/api/generate",
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )

        with urllib.request.urlopen(request, timeout=180) as response:
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
        if shutil.which("ollama") is None:
            raise RuntimeError(
                "Could not reach Ollama, and the `ollama` CLI is not on PATH. "
                "Install Ollama first, then run `ollama serve` and pull the model "
                f"with `ollama pull {model}`."
            ) from exc

        raise RuntimeError(
            "Could not reach Ollama. Start it with `ollama serve`, then try again."
        ) from exc
    finally:
        if should_delete_image and prepared_image_path:
            Path(prepared_image_path).unlink(missing_ok=True)


def call_ollama_chat(
    *,
    content: str,
    model: str,
    image_path: str | None,
    host: str,
) -> str:
    message: dict[str, object] = {
        "role": "user",
        "content": content,
    }
    if image_path:
        message["images"] = [image_to_base64(image_path)]

    payload = {
        "model": model,
        "messages": [message],
        "stream": False,
    }

    request = urllib.request.Request(
        f"{host.rstrip('/')}/api/chat",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            body = json.loads(response.read().decode("utf-8"))
            message_body = body.get("message", {})
            if isinstance(message_body, dict):
                return str(message_body.get("content", "")).strip()
            return ""
    except urllib.error.URLError as exc:
        if shutil.which("ollama") is None:
            raise RuntimeError(
                "Could not reach Ollama, and the `ollama` CLI is not on PATH."
            ) from exc
        raise RuntimeError(
            "Could not reach Ollama. Start it with `ollama serve`, then try again."
        ) from exc


def load_env_file(path: Path | None = None) -> None:
    env_path = path or Path(__file__).with_name(".env")
    if not env_path.exists():
        return

    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        if line.startswith("export "):
            line = line.removeprefix("export ").strip()

        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip("'\"")
        if key and key not in os.environ:
            os.environ[key] = value


def call_gemini(
    prompt: str,
    *,
    system_prompt: str,
    model: str,
    image_path: str | None,
    image_max_dim: int,
    image_jpeg_quality: int,
    max_tokens: int,
    temperature: float,
) -> str:
    load_env_file()
    api_key = os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY")
    if not api_key:
        raise RuntimeError(
            "Missing GEMINI_API_KEY. Create a .env file next to math_agent.py "
            "with GEMINI_API_KEY=your_key."
        )

    try:
        from google import genai
        from google.genai import types
    except ImportError as exc:
        raise RuntimeError(
            "Missing dependency: google-genai. Install it with "
            "`python -m pip install google-genai`."
        ) from exc

    contents: list[object] = []
    prepared_image_path: str | None = None
    should_delete_image = False
    if image_path:
        prepared_image_path, should_delete_image = prepare_image_for_model(
            image_path,
            max_dim=image_max_dim,
            quality=image_jpeg_quality,
        )
        mime_type = mimetypes.guess_type(prepared_image_path)[0] or "image/jpeg"
        image_bytes = Path(prepared_image_path).read_bytes()
        contents.append(types.Part.from_bytes(data=image_bytes, mime_type=mime_type))

    contents.append(prompt)

    try:
        client = genai.Client(api_key=api_key)
        response = client.models.generate_content(
            model=model,
            contents=contents,
            config=types.GenerateContentConfig(
                system_instruction=system_prompt,
                max_output_tokens=max_tokens,
                temperature=temperature,
            ),
        )
        return str(response.text or "").strip()
    finally:
        if should_delete_image and prepared_image_path:
            Path(prepared_image_path).unlink(missing_ok=True)


def load_llama_model(
    *,
    repo_id: str = MODEL_REPO,
    filename: str = MODEL_FILE,
    n_ctx: int = 8192,
    n_gpu_layers: int = 0,
):
    global _llama_model
    if _llama_model is not None:
        return _llama_model

    try:
        from llama_cpp import Llama
    except ImportError as exc:
        raise RuntimeError(
            "Missing dependency: llama-cpp-python. Install it with "
            "`pip install -r requirements.txt`."
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


def mock_tutor_answer(data: TutorInput) -> str:
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

    return (
        f"The key idea in `{ocr}` is to keep both sides balanced while isolating `x`. "
        "Focus on the operation attached to the variable and undo just that operation. "
        "What is the smallest next step you can take?"
    )


def answer_once(
    *,
    backend: str,
    prompt: str,
    tutor_input: TutorInput,
    system_prompt: str,
    image_path: str | None,
    disable_controller_guard: bool,
    ollama_model: str,
    ollama_host: str,
    gemini_model: str,
    stream: bool,
    image_max_dim: int,
    image_jpeg_quality: int,
    llama_repo: str,
    llama_file: str,
    n_ctx: int,
    n_gpu_layers: int,
    max_tokens: int,
    temperature: float,
) -> str:
    if not disable_controller_guard:
        controller_response = build_controller_guard_response(tutor_input)
        if controller_response is not None:
            return controller_response

    if backend == "mock":
        return mock_tutor_answer(tutor_input)

    if backend == "llama":
        if image_path:
            print(
                "Warning: the llama GGUF backend is text-only here. "
                "Use OCR text, or use --backend ollama with a vision-capable model for image bytes.",
                file=sys.stderr,
            )
        return call_llama_cpp(
            prompt,
            system_prompt=system_prompt,
            repo_id=llama_repo,
            filename=llama_file,
            n_ctx=n_ctx,
            n_gpu_layers=n_gpu_layers,
            max_tokens=max_tokens,
            temperature=temperature,
        )

    if backend == "ollama":
        return call_ollama(
            prompt,
            model=ollama_model,
            image_path=image_path,
            host=ollama_host,
            stream=stream,
            image_max_dim=image_max_dim,
            image_jpeg_quality=image_jpeg_quality,
        )

    if backend == "gemini":
        return call_gemini(
            prompt,
            system_prompt=system_prompt,
            model=gemini_model,
            image_path=image_path,
            image_max_dim=image_max_dim,
            image_jpeg_quality=image_jpeg_quality,
            max_tokens=max_tokens,
            temperature=temperature,
        )

    raise ValueError(f"Unsupported answer backend: {backend}")

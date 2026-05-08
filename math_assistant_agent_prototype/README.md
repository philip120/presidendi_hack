# Math Assistant Agent Prototype

Small Python prototype for the agentic tutoring prompt.

It does not depend on Flutter. It lets you hardcode a teacher context, student
question, optional OCR text, and an optional image path, then either:

- print the final prompt,
- simulate answers with a mock backend,
- send images directly to a local Ollama vision model,
- optionally transcribe images with Ollama vision or DeepSeek-OCR,
- run the GGUF model with `llama-cpp-python`, or
- send it to a local Ollama model.

## Run

From this folder:

```bash
python3 math_agent.py
```

That prints the assembled prompt.

## Simulate Q&A

Run a full question-answer loop without downloading a model:

```bash
python3 math_agent.py --chat --backend mock
```

Example session:

```text
Student> Why do they divide by -2 here?
Assistant> In `-2x = 6`, the number next to `x` is multiplying the variable...

Student> So what is the next step?
Assistant> Look at `-2x = 6` and find what operation is still attached to `x`...
```

Useful chat commands:

```text
/images
/image 0
/image ./equation.jpg
/clear-image
/study-guide
/quit
```

Run the same Q&A loop with the GGUF model:

```bash
python3 math_agent.py --chat --backend llama
```

Run it with Ollama:

```bash
ollama pull gemma4:26b
python3 math_agent.py --chat --backend ollama
```

Override the hardcoded question/OCR from the command line for text-only tests:

```bash
python3 math_agent.py \
  --question "Why do they divide by -2 here?" \
  --ocr "-2x = 6"
```

Pass an image path. This is useful with a vision-capable local model:

```bash
python3 math_agent.py \
  --backend ollama \
  --model gemma4:26b \
  --question "What is the next step?" \
  --image ./equation.jpg \
  --pure-image
```

## Hardcoded Images

Open `config.py` and edit:

```python
HARDCODED_IMAGE_PATHS = [
    "./images/equation_photo.jpg",
    "/absolute/path/to/worksheet_crop.png",
]
HARDCODED_IMAGE_INDEX = 0
```

List configured images:

```bash
python3 math_agent.py --list-images
```

Use a hardcoded image by index. `--pure-image` clears any OCR text and sends
the image directly to Gemma 26B:

```bash
python3 math_agent.py \
  --backend ollama \
  --model gemma4:26b \
  --image-index 0 \
  --pure-image \
  --question "How should I solve this?"
```

You can tune the temporary downscaled image sent to the vision model:

```bash
python3 math_agent.py \
  --backend ollama \
  --model gemma4:26b \
  --image-index 1 \
  --pure-image \
  --image-max-dim 1280 \
  --question "How should I solve this?"
```

This pipeline is:

```text
image -> Gemma 26B vision tutor
```

OCR/transcription code is still present for experiments, but it is no longer
part of the main image path. It only runs if you explicitly use
`--auto-transcribe-image`, `--transcribe-only`, or `/transcribe`.

Interactive image chat:

```bash
python3 math_agent.py \
  --chat \
  --backend ollama \
  --model gemma4:26b \
  --image-index 0 \
  --pure-image
```

Inside chat, switch images with:

```text
/images
/image 0
/image ./another_image.jpg
```

## Run With GGUF / llama-cpp-python

Install:

```bash
pip install -r requirements.txt
```

Run the hardcoded model:

```bash
python3 math_agent.py --backend llama
```

By default this loads:

```text
unsloth/gemma-4-26B-A4B-it-GGUF
gemma-4-26B-A4B-it-UD-IQ4_XS.gguf
```

Override model settings:

```bash
python3 math_agent.py \
  --backend llama \
  --llama-repo unsloth/gemma-4-26B-A4B-it-GGUF \
  --llama-file gemma-4-26B-A4B-it-UD-IQ4_XS.gguf \
  --n-ctx 8192 \
  --n-gpu-layers 0
```

The GGUF backend here is text-only. Use `--ocr` for recognized equation text.
For actual image bytes, use an Ollama vision-capable model.

## Run With Ollama

Run against Ollama:

```bash
ollama serve
ollama pull gemma4:26b
python3 math_agent.py --backend ollama
```

With image:

```bash
python3 math_agent.py \
  --backend ollama \
  --model gemma4:26b \
  --image ./equation.jpg \
  --pure-image \
  --question "Explain this step"
```

If your chosen Ollama model does not support images, use explicit `--ocr`
instead.

## Edit

Open `config.py` and edit:

- `HARDCODED_TEACHER_CONTEXT`
- `HARDCODED_INSTRUCTION_STYLE`
- `HARDCODED_STUDENT_STATE`
- `HARDCODED_RECENT_EXCHANGES`
- `HARDCODED_QUESTION`
- `HARDCODED_OCR_TEXT`
- `HARDCODED_IMAGE_PATHS`
- `HARDCODED_IMAGE_INDEX`

## Study Guide

Build the session-end consolidation prompt:

```bash
python3 math_agent.py --study-guide
```

Run that prompt with Ollama:

```bash
python3 math_agent.py --study-guide --backend ollama
```

Run it with the GGUF backend:

```bash
python3 math_agent.py --study-guide --backend llama
```

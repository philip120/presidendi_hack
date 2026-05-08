# Math Assistant Agent Prototype

Small Python prototype for the agentic tutoring prompt.

It does not depend on Flutter. It lets you hardcode a teacher context, student
question, OCR text, and optional image path, then either:

- print the final prompt,
- simulate answers with a mock backend,
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
/ocr -2x = 6
/image ./equation.jpg
/study-guide
/quit
```

Run the same Q&A loop with the GGUF model:

```bash
python3 math_agent.py --chat --backend llama
```

Run it with Ollama:

```bash
python3 math_agent.py --chat --backend ollama --model gemma3:4b
```

Override the hardcoded question/OCR from the command line:

```bash
python3 math_agent.py \
  --question "Why do they divide by -2 here?" \
  --ocr "-2x = 6"
```

Pass an image path. This is useful with a vision-capable local model:

```bash
python3 math_agent.py \
  --question "What is the next step?" \
  --image ./equation.jpg
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
python3 math_agent.py --backend ollama --model gemma3:4b
```

With image:

```bash
python3 math_agent.py \
  --backend ollama \
  --model gemma3:4b \
  --image ./equation.jpg \
  --question "Explain this step"
```

If your chosen Ollama model does not support images, use `--ocr` instead.

## Edit

Open `math_agent.py` and edit:

- `HARDCODED_TEACHER_CONTEXT`
- `HARDCODED_INSTRUCTION_STYLE`
- `HARDCODED_STUDENT_STATE`
- `HARDCODED_RECENT_EXCHANGES`
- `HARDCODED_QUESTION`
- `HARDCODED_OCR_TEXT`
- `HARDCODED_IMAGE_PATH`

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

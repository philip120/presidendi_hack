# Math Assistant Agent Prototype

Local Python prototype for a math tutoring agent using the Gemini API.

```text
image or text problem -> Gemini -> tutoring response
```

The default model is `gemini-3-flash-preview`. OCR is off by default; images are
sent directly to Gemini.

## Install Locally

### macOS

```bash
cd /path/to/math_assistant_agent_prototype
python3 -m venv venv
source venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements-gemini.txt
```

### Windows PowerShell

```powershell
cd path\to\math_assistant_agent_prototype
py -3 -m venv venv
.\venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements-gemini.txt
```

## Add API Key

Create `.env` in this folder:

```text
GEMINI_API_KEY=your_api_key_here
```

`.env` is loaded automatically and is ignored by git.

## Run Image Chat

Use a hardcoded image from `config.py`:

```bash
python -B math_agent.py --chat --backend gemini --image-index 1 --pure-image
```

Useful chat commands:

```text
/images
/image 0
/image 1
/image ./path/to/photo.jpg
/clear-image
/quit
```

Ask questions like:

```text
what do you see?
how should I solve this?
is my next step correct?
```

## Run Text Prompt

Single question:

```bash
python -B math_agent.py --backend gemini --question "How should I solve -2x = 6?" --ocr "-2x = 6"
```

Interactive text chat:

```bash
python -B math_agent.py --chat --backend gemini --ocr "-2x = 6"
```

Change the text problem during chat:

```text
/ocr 3x + 4 = 19
/clear-ocr
```

## Other Commands

Print the prompt without calling Gemini:

```bash
python -B math_agent.py
```

List configured images:

```bash
python -B math_agent.py --list-images
```

Create a study guide from the session log:

```bash
python -B math_agent.py --study-guide --backend gemini
```

Use a different Gemini model:

```bash
python -B math_agent.py --backend gemini --gemini-model gemini-3-flash-preview --question "Explain fractions"
```

## Configure Demo Inputs

Edit `config.py`:

- `GEMINI_MODEL`
- `HARDCODED_TEACHER_CONTEXT`
- `HARDCODED_INSTRUCTION_STYLE`
- `HARDCODED_QUESTION`
- `HARDCODED_OCR_TEXT`
- `HARDCODED_IMAGE_PATHS`
- `HARDCODED_IMAGE_INDEX`

## Troubleshooting

Missing API key:

```text
Error: Missing GEMINI_API_KEY
```

Fix: create `.env` in this folder with `GEMINI_API_KEY=...`.

Missing dependency:

```text
Error: Missing dependency: google-genai
```

Fix:

```bash
python -m pip install -r requirements-gemini.txt
```

# G.I KO KO CHAT — Starter ZIP

A mobile-first, no-build-step web app prototype for Android phones. Includes:
- Midnight Gold UI and GI splash-style hero
- Chat interface and `+` attachment menu (image, PDF, documents, audio, video, any file)
- API provider manager for OpenAI-compatible Chat Completions endpoints
- Model selector, system prompt, theme and basic preferences
- Browser-local settings and attached-file list

## Run on Android / Termux

No npm or Node packages are required.

```sh
pkg update
pkg install python
cd GI-KO-KO-CHAT
python -m http.server 8080
```

Open `http://127.0.0.1:8080` in your phone browser.

Alternatively, extract the ZIP and open `index.html` directly in a browser. Some browsers restrict API requests from `file://`; use the local server for API tests.

## API provider setup

1. Open **API Keys** → **Add provider**.
2. Enter provider name, API base URL, API key, and model ID.
3. Choose that provider in the model selector and send a text message.

The generic connector calls `{base URL}/chat/completions` with Bearer authentication. This works only for providers that expose a compatible endpoint and allow browser CORS. Provider-specific formats may need separate adapters.

## Important limitations

- This is a starter prototype, not a production-secure app.
- API keys are stored in browser `localStorage` and are **not encrypted**. Do not use on shared devices or publish a deployed copy with your key in it. Production version should use an authenticated backend proxy and OS-backed encrypted storage.
- Attachments can be selected and their names shown, but file contents are not extracted or sent to the model yet. PDF parsing, document extraction, image/vision requests, audio/video processing, upload progress, and resumable uploads are next implementation steps.
- “Pro” is a feature roadmap screen only; there is no billing or subscription.
- Direct browser calls can fail due to CORS, provider policy, invalid model IDs, quota, or endpoint differences.
- Never commit API keys to GitHub.

## Files

- `index.html` — complete self-contained UI and prototype logic
- `README.md` — setup and limitations

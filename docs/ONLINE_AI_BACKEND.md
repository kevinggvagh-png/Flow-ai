# Flow online AI connection

Flow's Android app sends chat messages to a configurable HTTPS backend. The backend implementation lives in `backend/` and calls QwenCloud's OpenAI-compatible Chat Completions API.

## Backend

Read [backend/README.md](../backend/README.md) for deployment steps. The server:
- receives `POST /v1/flow/chat` with `{"message":"..."}`
- sends a teen-oriented system instruction and the user message to QwenCloud
- returns `{"reply":"..."}`

The default Singapore endpoint is `https://ap-southeast-1.maas.qwencloudapi.com/compatible-mode/v1`. The API key must be created in the same region as the endpoint. The default model is `qwen3.8-flash`; both model and endpoint can be configured using backend environment variables.

The backend must run on a trusted Node.js 20+ host with HTTPS. It is not automatically deployed by GitHub.

## Android build

Set the Gradle property `FLOW_API_URL` to the HTTPS origin of the deployed backend (no route suffix):

```sh
gradle --no-daemon assembleDebug -PFLOW_API_URL=https://YOUR-HOST
```

The app appends `/v1/flow/chat` automatically. If the property is blank, the app builds but explains that the online AI server is not configured.

## Secrets and safety

Keep `QWEN_API_KEY` only in the backend host's secret environment-variable settings. Never put it in Android code, Gradle files committed to Git, ChatGPT, or the APK.

A parent/guardian or eligible project maintainer must review provider terms, account eligibility, consent, billing, and privacy requirements. The starter safeguards in this repository are not a complete compliance program; review `backend/README.md` before deployment.

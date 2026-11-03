# Flow online AI connection

Flow's Android app sends chat messages to a configurable HTTPS backend. The backend implementation lives in `backend/` and calls the OpenAI Responses API.

## Backend

Read [backend/README.md](../backend/README.md) for deployment steps. The server:
- receives `POST /v1/flow/chat` with `{"message":"..."}`
- checks user input and generated output with OpenAI's moderation endpoint
- generates a teen-oriented response using the Responses API
- returns `{"reply":"..."}`

The backend must run on a trusted Node.js 20+ host with HTTPS. It is not automatically deployed by GitHub.

## Android build

Set the Gradle property `FLOW_API_URL` to the HTTPS origin of the deployed backend (no route suffix):

```sh
gradle --no-daemon assembleDebug -PFLOW_API_URL=https://YOUR-HOST
```

The app appends `/v1/flow/chat` automatically. If the property is blank, the app builds but explains that the online AI server is not configured.

## Secrets and safety

Keep `OPENAI_API_KEY` only in the backend host's secret environment-variable settings. Never put it in Android code, Gradle files committed to Git, ChatGPT, or the APK.

A parent/guardian or eligible project maintainer must review provider terms, account eligibility, consent, billing, and privacy requirements. The starter safeguards in this repository are not a complete compliance program; review `backend/README.md` before deployment.
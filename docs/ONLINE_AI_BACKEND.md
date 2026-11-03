# Flow online AI connection

Flow's Android app sends chat messages to a configurable HTTPS backend. The
recommended free prototype is [`worker/`](../worker/README.md), using Cloudflare
Workers for hosting and Workers AI for replies and moderation. One Cloudflare
account provides the backend and models through bindings. Daily free quotas
apply; follow the linked deployment steps on `feature/cloudflare-flow-ai`.

## Existing Node/OpenAI backend

Read [backend/README.md](../backend/README.md) for deployment steps. The server:
- receives `POST /v1/flow/chat` with `{"message":"..."}`
- checks user input and generated output with OpenAI's moderation endpoint
- generates a teen-oriented response using the Responses API
- returns `{"reply":"..."}`

The description above applies to the earlier Node/OpenAI implementation in
`backend/`, which remains an independent option. It is not required for the
Cloudflare deployment. Neither backend is deployed by the Android build.

## Android build

Set the Gradle property `FLOW_API_URL` to the HTTPS origin of the deployed backend (no route suffix):

```sh
gradle --no-daemon assembleDebug -PFLOW_API_URL=https://YOUR-HOST
```

The app appends `/v1/flow/chat` automatically. If the property is blank, the app builds but explains that the online AI server is not configured.

## Secrets and safety

Keep `OPENAI_API_KEY` only in the backend host's secret environment-variable settings. Never put it in Android code, Gradle files committed to Git, ChatGPT, or the APK.

A parent/guardian or eligible project maintainer must review provider terms, account eligibility, consent, billing, and privacy requirements. The starter safeguards in this repository are not a complete compliance program; review `backend/README.md` before deployment.

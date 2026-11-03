# Flow AI backend

This Node 20+ service connects the Android app to OpenAI without putting a provider key in the APK.

## Configure

1. A parent/guardian or other eligible project maintainer should review the provider terms and complete any required account, consent, billing, and privacy setup.
2. Deploy this folder to a trusted Node.js hosting service that provides HTTPS.
3. Set these server environment variables in the hosting dashboard:
   - `OPENAI_API_KEY`: the project API key (secret; never commit or share it)
   - `OPENAI_MODEL`: optional; defaults to `gpt-6-astra`
   - `PORT`: supplied by most hosts automatically
   - `FLOW_ALLOWED_ORIGIN`: the app's web origin if a web client is added; native Android does not enforce browser CORS
4. Confirm `GET /health` returns `ok: true` and `aiConfigured: true`. Do not expose this endpoint's response publicly in a dashboard or screenshot.
5. Build the Android app with the HTTPS origin only:
   `gradle --no-daemon assembleDebug -PFLOW_API_URL=https://YOUR-HOST`
   Do not add a trailing route; the app adds `/v1/flow/chat`.
6. Install and test with non-sensitive sample prompts. Check provider usage and set spending limits in the provider dashboard.

## Safeguards included

- Input and output moderation with `omni-moderation-latest`
- A teen-oriented system instruction
- Message size limit and basic per-IP rate limit
- No request-body logging and `store: false` on Responses API calls
- API key stays in the backend environment, not Android or Git

These are starter safeguards, not a complete child-safety or privacy compliance program. The project maintainer must add appropriate monitoring, abuse prevention, privacy notices, data-retention practices, and any legally required parental consent or age assurance before making the app available to others. Do not send known or suspected child sexual abuse material to the Moderation API; it is not a CSAM detection system.

## Important

This backend is not deployed by this repository. A live service requires a hosting account, provider approval, a secret API key configured on the host, and an HTTPS URL. Never paste the API key into ChatGPT, GitHub source files, Android Gradle properties committed to Git, or an APK.

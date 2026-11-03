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

## Automated builds and tests

Set the GitHub repository **Actions variable** `FLOW_API_URL` to your backend's
HTTPS origin. The Android workflow passes this public URL to Gradle automatically;
leave it unset to build the interface without an AI connection. This variable is
not a provider secret. Keep the provider key only on your backend host.

Run `npm test` in `backend/` with Node 20 or later. Tests use a simulated provider
and never need an API key or incur provider charges. GitHub Actions runs these
tests alongside the Android build.

Requests require `Content-Type: application/json` and a JSON object with a
non-empty `message` (maximum 4,000 UTF-16 code units, matching Android's length).
The complete body is limited to 16,000 UTF-8 bytes. Invalid JSON returns 400,
oversized input returns 413, and an unsupported content type returns 415.
Provider failures return a generic 502/503 error without provider details.

All provider calls share a 35-second deadline, within the Android client's
45-second read timeout. Messages are moderated before generation and replies
before delivery; an invalid moderation response fails the request.

The in-memory limiter allows 20 requests per minute per IP and clears expired
entries on subsequent requests. It resets on server restart and is not shared
between instances. By default it uses the socket address. Set
`FLOW_TRUST_PROXY=true` only when your host's trusted reverse proxy overwrites
`X-Forwarded-For` and the service cannot be reached around that proxy. Otherwise
clients could spoof that header. Behind a proxy with this option disabled,
clients share the proxy IP's limit. Add user authentication and shared rate
limiting before opening a paid backend to public users.

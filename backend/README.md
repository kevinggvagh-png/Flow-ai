# Flow AI backend

This Node 20+ service connects the Android app to QwenCloud without putting a provider key in the APK.

## Configure

1. A parent/guardian or other eligible project maintainer must review the provider terms and complete any required account, consent, billing, and privacy setup.
2. Deploy this folder to a trusted Node.js hosting service that provides HTTPS.
3. Create a QwenCloud API key in the Singapore region. The key must match the Singapore endpoint configured below.
4. Set these server environment variables in the hosting dashboard:
   - `QWEN_API_KEY`: secret QwenCloud API key; never commit or share it
   - `QWEN_MODEL`: optional; defaults to `qwen3.8-flash`
   - `QWEN_BASE_URL`: optional; defaults to the Singapore OpenAI-compatible endpoint
   - `PORT`: supplied by most hosts automatically
   - `FLOW_ALLOWED_ORIGIN`: browser origin if a web client is added; native Android does not enforce browser CORS
5. Confirm `GET /health` returns `ok: true` and `aiConfigured: true`.
6. Build Android with the deployed HTTPS origin:
   `gradle --no-daemon assembleDebug -PFLOW_API_URL=https://YOUR-HOST`
   Do not add the chat route; the app adds `/v1/flow/chat`.
7. Test with non-sensitive sample prompts and review provider usage and billing controls.

## Safeguards

- The provider key stays server-side and is never included in Android or Git.
- Teen-oriented system instructions, message-size limit, request timeout, and basic per-IP rate limit.
- No request-body logging.
- QwenCloud provider-side safety controls may apply, but this starter backend is not a complete child-safety or privacy compliance program. The project maintainer must review provider safeguards, add suitable abuse prevention and privacy practices, and complete any legally required parental consent or age assurance before wider distribution.

## Important

This backend is not deployed by this repository. A live service still requires an eligible adult/project administrator to arrange provider access and hosting, configure the secret API key on the host, and provide an HTTPS URL. Never paste the API key into ChatGPT, GitHub source files, Android Gradle properties committed to Git, or an APK.

Set the GitHub repository **Actions variable** `FLOW_API_URL` to the backend HTTPS origin. This is a public URL, not a secret. Keep the QwenCloud key only on the backend host.

Run `npm test` in this folder with Node 20 or later. Tests use a simulated provider and do not require a real key or incur API charges.

Requests require `Content-Type: application/json` and a JSON object with a non-empty `message` (maximum 4,000 characters). The request body is limited to 16,000 bytes. Invalid JSON returns 400, oversized input returns 413, and unsupported content type returns 415. Provider failures return generic errors without exposing provider details.

The in-memory limiter allows 20 requests per minute per IP. It resets on server restart and is not shared between instances. Set `FLOW_TRUST_PROXY=true` only when a trusted reverse proxy overwrites `X-Forwarded-For`; otherwise clients could spoof it. Add authentication and shared rate limiting before opening a paid backend to public users.

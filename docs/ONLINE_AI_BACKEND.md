# Flow online AI connection

The floating chat now calls a configurable HTTPS backend instead of downloading an on-device model.

## Backend contract

Configure the Gradle project property `FLOW_API_URL` to the origin of a trusted HTTPS server, for example `https://flow.example.com` (do not include the route).

The app sends:

```http
POST /v1/flow/chat
Content-Type: application/json

{"message":"Hello Flow"}
```

The server should return:

```json
{"reply":"Hello! How can I help?"}
```

The backend must authenticate/rate-limit requests as appropriate, apply age-appropriate safety controls, and keep all provider API keys on the server. Never put a provider key in the Android app, Git repository, or APK.

## Build configuration

For a local build, set the Gradle property `FLOW_API_URL` in your private user-level Gradle properties or pass `-PFLOW_API_URL=https://your-https-backend` to Gradle. Do not commit private endpoints or credentials.

If the property is blank, the app builds normally but clearly reports that the online AI server has not been configured. A live AI provider is not bundled or secretly proxied by this repository; the backend must be supplied and operated by an eligible maintainer with appropriate permissions and safeguards.

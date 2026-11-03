# Flow AI on Cloudflare Free

This backend hosts Flow's chat endpoint on Cloudflare Workers and generates
replies with Cloudflare Workers AI. It uses one Cloudflare account. No Render
service, OpenAI API key, Gemini API key, paid instance, or custom domain is
needed for this deployment path.

The Android contract stays the same:

- `POST /v1/flow/chat` with `{"message":"Hello"}` returns `{"reply":"..."}`.
- `GET /health` returns `{"ok":true,"aiConfigured":true}` when the bindings exist.
  This checks configuration, not model availability or remaining AI quota.

## Deploy from GitHub

Use a Cloudflare account on **Workers Free**. In Workers & Pages, create a Worker
from the `kevinggvagh-png/Flow-ai` repository with:

| Setting | Value |
| --- | --- |
| Branch | `feature/cloudflare-flow-ai` |
| Root directory | `worker` |
| Build command | Leave empty |
| Deploy command | `npx wrangler deploy` |

Cloudflare installs dependencies from `worker/package-lock.json`. The committed
`wrangler.jsonc` supplies the `AI` binding and both rate limiter bindings. Select
`worker` as the root: the Android app is built separately.

After deployment, copy the HTTPS `workers.dev` origin shown in Cloudflare and
open `/health`. A sample chat verifies actual model access; the health endpoint
alone does not. If Cloudflare requests acceptance of model terms, complete that
in your own account before testing. No API key needs to be pasted into chat or
embedded in the app.

## Or deploy from your computer

With Node.js 22 or newer installed, open this directory and run:

```sh
npm ci
npm test
npm run check
npx wrangler login
npm run deploy
```

`wrangler login` opens Cloudflare's own authorization page. The deploy command
prints the public Worker URL. `npm run check` only bundles the Worker; it does
not publish it or invoke AI.

## Connect Android

Set the GitHub repository **Actions variable** `FLOW_API_URL` to that HTTPS origin
(for example, `https://flow-ai-backend.YOUR-SUBDOMAIN.workers.dev`). Do not append
`/v1/flow/chat`; Android adds it. Run **Build Flow AI APK** on this branch and
install its artifact. Local builds accept `-PFLOW_API_URL=https://YOUR-HOST`.

There are no provider credentials in the Android app. The Worker accesses AI
through Cloudflare's `AI` binding, scoped to its Cloudflare account.

## Free allocation and limits

As checked on 3 November 2026:

- Workers Free includes 100,000 incoming Worker requests per day.
- Workers AI includes 10,000 neurons of model compute per day. This is a separate
  limit, not 10,000 chats; input length, response length and moderation consume it.
- On Workers Free, exceeding the AI allowance stops AI operations until the daily
  reset. This code does not upgrade the account or fall back to a paid provider.
  Keep the account on Workers Free to avoid paid overages.
- The default chat model is `@cf/meta/llama-3.1-8b-instruct-fp8`. Llama Guard 3
  checks input and output. A normal chat therefore uses three model calls.
- Generation is limited to 512 output tokens. All three model calls share a
  35-second response deadline. An in-flight call may finish after this deadline,
  but the Worker does not start the next inference stage afterward.
- Limiters allow 20 messages/minute per client and 60/minute for the service,
  **per Cloudflare location**. They are approximate, not strict global counters.

The free allowance is suitable for a personal prototype; availability and quotas
can change. Model quality differs from the earlier OpenAI backend. This endpoint
has no user login, so its URL and rate limiters are not authentication. Add user
authentication before distributing it broadly.

The Worker does not log message bodies or persist conversations. Cloudflare
processes messages to run AI; review its data usage and model terms for your use.

## Verification

`npm test` exercises the Worker handler with simulated AI and rate-limit
bindings. Tests verify the Android contract, input byte/character limits, input
and output moderation, quota errors, redacted provider failures and timeouts.
These tests do not invoke live models or use an AI allowance.

Official references:

- [Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/)
- [Workers AI pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/)
- [Workers AI binding](https://developers.cloudflare.com/workers-ai/configuration/bindings/)
- [Rate limiter behavior](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/)
- [Llama Guard 3](https://developers.cloudflare.com/workers-ai/models/llama-guard-3-8b/)

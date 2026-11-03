const MAX_BODY_BYTES = 16_000;
const MAX_MESSAGE_CHARS = 4_000;
const DEFAULT_MODEL = "@cf/meta/llama-3.1-8b-instruct-fp8";
const GUARD_MODEL = "@cf/meta/llama-guard-3-8b";
const SAFE_REPLY =
  "I can't help with that request, but I can help with a safer, age-appropriate alternative.";
const SYSTEM_INSTRUCTION =
  "You are Flow, a helpful assistant for a teen audience. Be friendly, clear, and age-appropriate. " +
  "Do not provide sexual content, self-harm instructions, dangerous challenges, weapon instructions, " +
  "or help bypass laws, age limits, or safety systems. Avoid collecting personal information. " +
  "For serious health, safety, or emotional concerns, encourage the user to speak with a trusted adult " +
  "or qualified professional. Keep answers concise unless detail is needed.";

class RequestError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function send(env, status, body, extraHeaders = {}) {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": env.FLOW_ALLOWED_ORIGIN || "*",
      "access-control-allow-methods": "POST, GET, OPTIONS",
      "access-control-allow-headers": "content-type",
      "x-content-type-options": "nosniff",
      ...extraHeaders
    }
  });
}

// Check actual streamed bytes; Content-Length alone cannot enforce this limit.
async function readJsonLimited(body, limit) {
  if (!body) throw new RequestError(400, "Please send valid JSON.");
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let size = 0;
  let text = "";
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > limit) {
        await reader.cancel();
        throw new RequestError(413, "Request too large.");
      }
      text += decoder.decode(value, { stream: true });
    }
    text += decoder.decode();
  } finally {
    reader.releaseLock();
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new RequestError(400, "Please send valid JSON.");
  }
}

function isConfigured(env) {
  return typeof env.AI?.run === "function" &&
    typeof env.FLOW_RATE_LIMITER?.limit === "function" &&
    typeof env.FLOW_GLOBAL_LIMITER?.limit === "function";
}

function isSafe(result) {
  const verdict = typeof result?.response === "string" ? result.response.trim().toLowerCase() : "";
  if (verdict === "safe") return true;
  if (/^unsafe\b/.test(verdict)) return false;
  throw new Error("Invalid moderation response");
}

export function createFlowWorker({ timeoutMs = 35_000 } = {}) {
  async function generate(message, env) {
    const model = env.FLOW_AI_MODEL || DEFAULT_MODEL;
    let expired = false;
    let timer;
    const deadline = new Promise((_, reject) => {
      timer = setTimeout(() => {
        expired = true;
        reject(new Error("Provider deadline exceeded"));
      }, timeoutMs);
    });
    // A single deadline covers moderation, generation and output moderation.
    // An in-flight binding call may finish after timeout, but no next call starts.
    async function run(modelId, input) {
      if (expired) throw new Error("Provider deadline exceeded");
      const result = await env.AI.run(modelId, input);
      if (expired) throw new Error("Provider deadline exceeded");
      return result;
    }
    try {
      return await Promise.race([
        (async () => {
          const conversation = [{ role: "user", content: message }];
          if (!isSafe(await run(GUARD_MODEL, {
            messages: conversation, max_tokens: 64, temperature: 0
          }))) {
            return { reply: SAFE_REPLY };
          }
          const generated = await run(model, {
            messages: [{ role: "system", content: SYSTEM_INSTRUCTION }, ...conversation],
            max_tokens: 512,
            stream: false
          });
          const reply = typeof generated?.response === "string" ? generated.response.trim() : "";
          if (!reply || reply.length > 12_000) throw new Error("Invalid provider reply");
          const outputCheck = await run(GUARD_MODEL, {
            messages: [...conversation, { role: "assistant", content: reply }],
            max_tokens: 64,
            temperature: 0
          });
          return { reply: isSafe(outputCheck) ? reply : SAFE_REPLY };
        })(),
        deadline
      ]);
    } finally {
      clearTimeout(timer);
    }
  }

  return {
    async fetch(request, env = {}) {
      const path = new URL(request.url).pathname;
      if (request.method === "OPTIONS") return send(env, 204);
      if (request.method === "GET" && path === "/health") {
        return send(env, 200, { ok: true, aiConfigured: isConfigured(env) });
      }
      if (request.method !== "POST" || path !== "/v1/flow/chat") {
        return send(env, 404, { error: "Not found" });
      }
      if (!isConfigured(env)) {
        return send(env, 503, { error: "Flow AI server is not configured yet." });
      }
      try {
        if (request.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !==
            "application/json") {
          throw new RequestError(415, "Content-Type must be application/json.");
        }
        // This header is supplied by Cloudflare's edge, not X-Forwarded-For.
        const ip = request.headers.get("CF-Connecting-IP") || "unidentified";
        const clientLimit = await env.FLOW_RATE_LIMITER.limit({ key: ip });
        const globalLimit = clientLimit.success &&
          await env.FLOW_GLOBAL_LIMITER.limit({ key: "flow-chat" });
        if (!clientLimit.success || !globalLimit.success) {
          return send(env, 429, { error: "Too many messages. Please wait a moment." },
            { "retry-after": "60" });
        }
        const body = await readJsonLimited(request.body, MAX_BODY_BYTES);
        if (!body || typeof body !== "object" || Array.isArray(body)) {
          throw new RequestError(400, "Please send a JSON object.");
        }
        const message = typeof body.message === "string" ? body.message.trim() : "";
        if (!message) throw new RequestError(400, "Please enter a message.");
        if (message.length > MAX_MESSAGE_CHARS) {
          throw new RequestError(413, `Messages must be ${MAX_MESSAGE_CHARS} characters or fewer.`);
        }
        let result;
        try {
          result = await generate(message, env);
        } catch (error) {
          if (error.status === 429 || [3036, 3040].includes(Number(error.code))) {
            return send(env, 503, {
              error: "Flow's free AI quota is busy or used up. Please try again later."
            }, { "retry-after": "60" });
          }
          // Never relay provider errors, prompts or internal model details.
          return send(env, 502, { error: "Flow couldn't reach its AI service. Please try again later." });
        }
        return send(env, 200, { reply: result.reply });
      } catch (error) {
        if (error instanceof RequestError) {
          return send(env, error.status, { error: error.message });
        }
        return send(env, 503, { error: "Flow is temporarily unavailable. Please try again later." });
      }
    }
  };
}

export default createFlowWorker();

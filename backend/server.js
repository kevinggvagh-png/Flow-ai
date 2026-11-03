import http from "node:http";
import { pathToFileURL } from "node:url";

class RequestError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export function createFlowServer({ env = process.env, fetchImpl = fetch, now = Date.now } = {}) {

  const API_KEY = env.OPENAI_API_KEY;
  const MODEL = env.OPENAI_MODEL || "gpt-6-astra";
  const ALLOWED_ORIGIN = env.FLOW_ALLOWED_ORIGIN || "*";
  const MAX_MESSAGE_CHARS = 4000;
  const WINDOW_MS = 60_000;
  const MAX_REQUESTS_PER_WINDOW = 20;
  const requests = new Map();

  const SAFE_REPLY =
    "I can't help with that request, but I can help with a safer, age-appropriate alternative.";

  function send(res, status, body) {
    res.writeHead(status, {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": ALLOWED_ORIGIN,
      "access-control-allow-methods": "POST, GET, OPTIONS",
      "access-control-allow-headers": "content-type",
      "x-content-type-options": "nosniff"
    });
    res.end(JSON.stringify(body));
  }

  function rateLimited(ip) {
    const timestamp = now();
    // Prune expired clients so the limiter does not grow forever.
    for (const [key, entry] of requests) {
      if (timestamp - entry.startedAt >= WINDOW_MS) requests.delete(key);
    }
    const current = requests.get(ip);
    if (!current || timestamp - current.startedAt >= WINDOW_MS) {
      requests.set(ip, { startedAt: timestamp, count: 1 });
      return false;
    }
    current.count += 1;
    return current.count > MAX_REQUESTS_PER_WINDOW;
  }

  async function readJson(req) {
    const chunks = [];
    let size = 0;
    for await (const chunk of req) {
      size += chunk.length;
      if (size > 16_000) throw new RequestError(413, "Request too large.");
      chunks.push(chunk);
    }
    let body;
    try {
      body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    } catch {
      throw new RequestError(400, "Please send valid JSON.");
    }
    if (!body || typeof body !== "object" || Array.isArray(body)) {
      throw new RequestError(400, "Please send a JSON object.");
    }
    return body;
  }

  async function openai(path, body, signal) {
    const response = await fetchImpl(`https://api.openai.com/v1/${path}`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${API_KEY}`,
        "content-type": "application/json"
      },
      body: JSON.stringify(body),
      signal
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(data?.error?.message || "AI provider request failed");
      error.status = response.status;
      throw error;
    }
    return data;
  }

  function responseText(data) {
    if (typeof data.output_text === "string") return data.output_text.trim();
    const parts = [];
    for (const item of data.output || []) {
      if (item.type !== "message") continue;
      for (const content of item.content || []) {
        if (content.type === "output_text" && content.text) parts.push(content.text);
      }
    }
    return parts.join("\n").trim();
  }

  const server = http.createServer(async (req, res) => {
    if (req.method === "OPTIONS") return send(res, 204, {});
    if (req.method === "GET" && req.url === "/health") {
      return send(res, 200, { ok: true, aiConfigured: Boolean(API_KEY) });
    }
    if (req.method !== "POST" || req.url !== "/v1/flow/chat") {
      return send(res, 404, { error: "Not found" });
    }
    if (!API_KEY) return send(res, 503, { error: "Flow AI server is not configured yet." });

    // Forwarded headers are client-controlled unless a trusted proxy overwrites them.
    const forwarded = env.FLOW_TRUST_PROXY === "true" ? req.headers["x-forwarded-for"] : null;
    const ip = (typeof forwarded === "string" ? forwarded.split(",")[0].trim() : null)
      || req.socket.remoteAddress || "unknown";
    if (rateLimited(ip)) return send(res, 429, { error: "Too many messages. Please wait a moment." });

    try {
      if (req.headers["content-type"]?.split(";")[0].trim().toLowerCase() !== "application/json") {
        throw new RequestError(415, "Content-Type must be application/json.");
      }
      const body = await readJson(req);
      // One budget for all three provider calls; the Android client waits 45 seconds.
      const signal = AbortSignal.timeout(35_000);
      const message = typeof body.message === "string" ? body.message.trim() : "";
      if (!message) return send(res, 400, { error: "Please enter a message." });
      if (message.length > MAX_MESSAGE_CHARS) {
        return send(res, 413, { error: `Messages must be ${MAX_MESSAGE_CHARS} characters or fewer.` });
      }

      // Moderate user input before generation. Never send known or suspected CSAM
      // to this endpoint; this moderation call is not a CSAM detection system.
      const inputCheck = await openai("moderations", {
        model: "omni-moderation-latest",
        input: message
      }, signal);
      if (typeof inputCheck.results?.[0]?.flagged !== "boolean") throw new Error("Invalid moderation response");
      if (inputCheck.results[0].flagged) {
        return send(res, 200, { reply: SAFE_REPLY });
      }

      const generated = await openai("responses", {
        model: MODEL,
        store: false,
        instructions:
          "You are Flow, a helpful assistant for a teen audience. Be friendly, clear, and age-appropriate. " +
          "Do not provide sexual content, self-harm instructions, dangerous challenges, weapon instructions, " +
          "or help bypass laws, age limits, or safety systems. Avoid collecting personal information. " +
          "For serious health, safety, or emotional concerns, encourage the user to speak with a trusted adult " +
          "or qualified professional. Keep answers concise unless detail is needed.",
        input: message
      }, signal);

      const reply = responseText(generated);
      if (!reply) return send(res, 502, { error: "The AI returned an empty response. Please try again." });

      const outputCheck = await openai("moderations", {
        model: "omni-moderation-latest",
        input: reply
      }, signal);
      if (typeof outputCheck.results?.[0]?.flagged !== "boolean") throw new Error("Invalid moderation response");
      if (outputCheck.results[0].flagged) {
        return send(res, 200, { reply: SAFE_REPLY });
      }
      return send(res, 200, { reply });
    } catch (error) {
      if (error instanceof RequestError) return send(res, error.status, { error: error.message });
      const status = error.status === 429 ? 503 : 502;
      return send(res, status, { error: "Flow couldn't reach its AI service. Please try again later." });
    }
  });

  server.requestTimeout = 15_000;
  server.headersTimeout = 10_000;
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.env.PORT || 8080);
  createFlowServer().listen(port, "0.0.0.0", () => {
    console.log(`Flow AI backend listening on port ${port}`);
  });
}

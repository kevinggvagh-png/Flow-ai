import http from "node:http";
import { pathToFileURL } from "node:url";

class RequestError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

export function createFlowServer({ env = process.env, fetchImpl = fetch, now = Date.now } = {}) {
  const API_KEY = env.QWEN_API_KEY;
  const MODEL = env.QWEN_MODEL || "qwen3.8-flash";
  const BASE_URL = (env.QWEN_BASE_URL || "https://ap-southeast-1.maas.qwencloudapi.com/compatible-mode/v1").replace(/\/$/, "");
  const ALLOWED_ORIGIN = env.FLOW_ALLOWED_ORIGIN || "*";
  const MAX_MESSAGE_CHARS = 4000;
  const WINDOW_MS = 60_000;
  const MAX_REQUESTS_PER_WINDOW = 20;
  const requests = new Map();

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
    try { body = JSON.parse(Buffer.concat(chunks).toString("utf8")); }
    catch { throw new RequestError(400, "Please send valid JSON."); }
    if (!body || typeof body !== "object" || Array.isArray(body)) {
      throw new RequestError(400, "Please send a JSON object.");
    }
    return body;
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

    const forwarded = env.FLOW_TRUST_PROXY === "true" ? req.headers["x-forwarded-for"] : null;
    const ip = (typeof forwarded === "string" ? forwarded.split(",")[0].trim() : null)
      || req.socket.remoteAddress || "unknown";
    if (rateLimited(ip)) return send(res, 429, { error: "Too many messages. Please wait a moment." });

    try {
      if (req.headers["content-type"]?.split(";")[0].trim().toLowerCase() !== "application/json") {
        throw new RequestError(415, "Content-Type must be application/json.");
      }
      const body = await readJson(req);
      const message = typeof body.message === "string" ? body.message.trim() : "";
      if (!message) return send(res, 400, { error: "Please enter a message." });
      if (message.length > MAX_MESSAGE_CHARS) {
        return send(res, 413, { error: `Messages must be ${MAX_MESSAGE_CHARS} characters or fewer.` });
      }

      const response = await fetchImpl(`${BASE_URL}/chat/completions`, {
        method: "POST",
        headers: {
          authorization: `Bearer ${API_KEY}`,
          "content-type": "application/json"
        },
        body: JSON.stringify({
          model: MODEL,
          messages: [
            {
              role: "system",
              content: "You are Flow, a helpful assistant for a teen audience. Be friendly, clear, and age-appropriate. Do not provide sexual content, self-harm instructions, dangerous challenges, weapon instructions, or help bypass laws, age limits, or safety systems. Avoid collecting personal information. For serious health, safety, or emotional concerns, encourage the user to speak with a trusted adult or qualified professional. Keep answers concise unless detail is needed."
            },
            { role: "user", content: message }
          ],
          stream: false,
          max_tokens: 800
        }),
        signal: AbortSignal.timeout(35_000)
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        const error = new Error(data?.error?.message || "QwenCloud request failed");
        error.status = response.status;
        throw error;
      }
      const reply = data?.choices?.[0]?.message?.content;
      if (typeof reply !== "string" || !reply.trim()) {
        return send(res, 502, { error: "The AI returned an empty response. Please try again." });
      }
      return send(res, 200, { reply: reply.trim() });
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

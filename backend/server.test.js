import { test } from "node:test";
import assert from "node:assert/strict";
import { createFlowServer } from "./server.js";

async function fixture(t, { env = {}, replies = [], clock } = {}) {
  const calls = [];
  const server = createFlowServer({
    env: { QWEN_API_KEY: "test-only", ...env },
    now: clock,
    fetchImpl: async (url, options) => {
      calls.push({ url, body: JSON.parse(options.body), headers: options.headers, signal: options.signal });
      const reply = replies.shift();
      if (reply instanceof Error) throw reply;
      assert.ok(reply, "Unexpected provider call");
      return Response.json(reply.body ?? reply, { status: reply.status ?? 200 });
    }
  });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const base = `http://127.0.0.1:${server.address().port}`;
  return {
    calls,
    get: path => fetch(base + path),
    post: (body, headers = {}) => fetch(base + "/v1/flow/chat", {
      method: "POST", headers: { "content-type": "application/json", ...headers }, body
    })
  };
}

test("health and unconfigured chat do not call the provider", async t => {
  const app = await fixture(t, { env: { QWEN_API_KEY: "" } });
  assert.deepEqual(await (await app.get("/health")).json(), { ok: true, aiConfigured: false });
  assert.equal((await app.post('{"message":"Hello"}')).status, 503);
  assert.equal(app.calls.length, 0);
});

test("invalid JSON and invalid message shapes return client errors", async t => {
  const app = await fixture(t);
  for (const body of ["{", "null", "[]", "1", "{}", '{"message":12}', '{"message":" "}']) {
    assert.equal((await app.post(body)).status, 400, body);
  }
  assert.equal((await app.post("{}", { "content-type": "text/plain" })).status, 415);
  assert.equal(app.calls.length, 0);
});

test("message length and UTF-8 body byte limits are enforced", async t => {
  const app = await fixture(t);
  assert.equal((await app.post(JSON.stringify({ message: "a".repeat(4001) }))).status, 413);
  assert.equal((await app.post(JSON.stringify({ message: "😀".repeat(5000) }))).status, 413);
  assert.equal(app.calls.length, 0);
});

test("chat calls QwenCloud with server-side key and returns reply", async t => {
  const app = await fixture(t, { replies: [{ choices: [{ message: { content: " Hello Flow! " } }] }] });
  assert.deepEqual(await (await app.post('{"message":" hi "}')).json(), { reply: "Hello Flow!" });
  assert.equal(app.calls.length, 1);
  assert.match(app.calls[0].url, /ap-southeast-1\.maas\.qwencloudapi\.com\/compatible-mode\/v1\/chat\/completions$/);
  assert.equal(app.calls[0].headers.authorization, "Bearer test-only");
  assert.equal(app.calls[0].body.model, "qwen3.8-flash");
  assert.equal(app.calls[0].body.messages[1].content, "hi");
  assert.equal(app.calls[0].body.stream, false);
  assert.equal(app.calls[0].body.max_tokens, 800);
});

test("custom Qwen model and endpoint are configurable", async t => {
  const app = await fixture(t, {
    env: { QWEN_MODEL: "qwen3.7-plus", QWEN_BASE_URL: "https://example.test/v1/" },
    replies: [{ choices: [{ message: { content: "ok" } }] }]
  });
  await app.post('{"message":"hi"}');
  assert.equal(app.calls[0].url, "https://example.test/v1/chat/completions");
  assert.equal(app.calls[0].body.model, "qwen3.7-plus");
});

test("provider errors and malformed responses fail without exposing secrets", async t => {
  for (const reply of [{ status: 401, body: { error: { message: "secret test-key" } } }, {}, new Error("timeout")]) {
    const app = await fixture(t, { replies: [reply] });
    const response = await app.post('{"message":"hi"}');
    assert.equal(response.status, 502);
    assert.doesNotMatch(await response.text(), /secret|test-key|timeout/);
  }
});

test("provider throttling returns temporary unavailability", async t => {
  const app = await fixture(t, { replies: [{ status: 429, body: {} }] });
  assert.equal((await app.post('{"message":"hi"}')).status, 503);
});

test("forwarded header spoofing does not bypass rate limits; window resets", async t => {
  let timestamp = 0;
  const app = await fixture(t, { clock: () => timestamp });
  for (let i = 0; i < 20; i++) {
    assert.equal((await app.post("{}", { "x-forwarded-for": `client-${i}` })).status, 400);
  }
  assert.equal((await app.post("{}", { "x-forwarded-for": "another" })).status, 429);
  timestamp = 60_001;
  assert.equal((await app.post("{}")).status, 400);
});

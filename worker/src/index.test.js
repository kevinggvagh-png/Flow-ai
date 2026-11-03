import assert from "node:assert/strict";
import { test } from "node:test";
import { createFlowWorker } from "./index.js";

const GUARD = "@cf/meta/llama-guard-3-8b";

function setup({ run, clientLimit, globalLimit, timeoutMs } = {}) {
  const calls = [];
  const rateKeys = [];
  const env = {
    AI: {
      async run(model, input) {
        calls.push({ model, input });
        if (run) return run(model, input, calls.length);
        return { response: model === GUARD ? "safe" : "Hello from Flow." };
      }
    },
    FLOW_RATE_LIMITER: {
      async limit({ key }) {
        rateKeys.push(key);
        return { success: clientLimit ?? true };
      }
    },
    FLOW_GLOBAL_LIMITER: {
      async limit() { return { success: globalLimit ?? true }; }
    }
  };
  const worker = createFlowWorker({ timeoutMs });
  function request(body = JSON.stringify({ message: "Hello" }), options = {}) {
    return worker.fetch(new Request("https://flow.example/v1/flow/chat", {
      method: "POST",
      headers: { "content-type": "application/json", "CF-Connecting-IP": "192.0.2.1" },
      body,
      ...options
    }), env);
  }
  return { env, worker, request, calls, rateKeys };
}

test("preserves the Android contract and moderates both input and output", async () => {
  const app = setup();
  const response = await app.request(JSON.stringify({ message: "  Hello  " }));
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { reply: "Hello from Flow." });
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(app.calls.length, 3);
  assert.equal(app.calls[0].model, GUARD);
  assert.deepEqual(app.calls[0].input.messages, [{ role: "user", content: "Hello" }]);
  assert.equal(app.calls[1].model, "@cf/meta/llama-3.1-8b-instruct-fp8");
  assert.equal(app.calls[1].input.max_tokens, 512);
  assert.match(app.calls[1].input.messages[0].content, /age-appropriate/);
  assert.equal(app.calls[2].model, GUARD);
  assert.deepEqual(app.calls[2].input.messages.at(-1),
    { role: "assistant", content: "Hello from Flow." });
});

test("health and preflight do not invoke AI", async () => {
  const app = setup();
  const healthy = await app.worker.fetch(new Request("https://flow.example/health"), app.env);
  assert.deepEqual(await healthy.json(), { ok: true, aiConfigured: true });
  const incomplete = await app.worker.fetch(new Request("https://flow.example/health"), {});
  assert.deepEqual(await incomplete.json(), { ok: true, aiConfigured: false });
  const preflight = await app.request(undefined, { method: "OPTIONS", body: undefined });
  assert.equal(preflight.status, 204);
  assert.equal(await preflight.text(), "");
  const missing = await app.worker.fetch(new Request("https://flow.example/other"), app.env);
  assert.equal(missing.status, 404);
  assert.equal(app.calls.length, 0);
});

test("requires the AI binding and both rate limiters", async t => {
  for (const binding of ["AI", "FLOW_RATE_LIMITER", "FLOW_GLOBAL_LIMITER"]) {
    await t.test(binding, async () => {
      const app = setup();
      delete app.env[binding];
      assert.equal((await app.request()).status, 503);
      assert.equal(app.calls.length, 0);
    });
  }
});

test("rejects malformed input before any AI invocation", async t => {
  const cases = [
    ["broken JSON", "{", 400], ["null", "null", 400], ["array", "[]", 400],
    ["primitive", "42", 400], ["missing message", "{}", 400],
    ["blank message", '{"message":"  "}', 400],
    ["wrong message type", '{"message":12}', 400],
    ["long message", JSON.stringify({ message: "a".repeat(4001) }), 413],
    ["oversized bytes", JSON.stringify({ message: "Hello", extra: "a".repeat(16000) }), 413]
  ];
  for (const [name, body, status] of cases) {
    await t.test(name, async () => {
      const app = setup();
      assert.equal((await app.request(body)).status, status);
      assert.equal(app.calls.length, 0);
    });
  }
  const app = setup();
  assert.equal((await app.request("hello", {
    headers: { "content-type": "text/plain" }
  })).status, 415);
  assert.equal(app.calls.length, 0);
});

test("counts actual streamed bytes and cancels an oversized body", async () => {
  const app = setup();
  let cancelled = false;
  let chunks = 0;
  const body = new ReadableStream({
    pull(controller) { chunks += 1; controller.enqueue(new Uint8Array(8001)); },
    cancel() { cancelled = true; }
  });
  const response = await app.request(body, {
    duplex: "half",
    headers: { "content-type": "application/json", "content-length": "1" }
  });
  assert.equal(response.status, 413);
  assert.equal(cancelled, true);
  assert.ok(chunks <= 3);
  assert.equal(app.calls.length, 0);
});

test("accepts multilingual input at the client character limit", async () => {
  const app = setup();
  const message = "અ".repeat(4000);
  assert.equal((await app.request(JSON.stringify({ message }))).status, 200);
  assert.equal(app.calls[0].input.messages[0].content, message);
});

test("client and global limits block inference and use the edge IP", async t => {
  for (const limits of [{ clientLimit: false }, { globalLimit: false }]) {
    await t.test(JSON.stringify(limits), async () => {
      const app = setup(limits);
      const response = await app.request();
      assert.equal(response.status, 429);
      assert.equal(response.headers.get("retry-after"), "60");
      assert.equal(app.calls.length, 0);
    });
  }
  const app = setup();
  await app.request(undefined, {
    headers: {
      "content-type": "application/json", "CF-Connecting-IP": "192.0.2.22",
      "x-forwarded-for": "spoofed-client"
    }
  });
  assert.deepEqual(app.rateKeys, ["192.0.2.22"]);
});

test("blocked input stops generation and returns a safe response", async () => {
  const app = setup({ run: async () => ({ response: "unsafe\nS1" }) });
  const response = await app.request();
  assert.equal(response.status, 200);
  assert.match((await response.json()).reply, /safer/);
  assert.equal(app.calls.length, 1);
});

test("blocked output never reaches the Android client", async () => {
  const app = setup({ run: async (_, __, call) => ({
    response: call === 1 ? "safe" : call === 2 ? "blocked-generated-text" : "unsafe\nS11"
  }) });
  const response = await app.request();
  assert.equal(response.status, 200);
  const body = await response.text();
  assert.doesNotMatch(body, /blocked-generated-text/);
  assert.match(JSON.parse(body).reply, /safer/);
});

test("malformed moderation and generation fail closed", async t => {
  for (const invalid of [{}, { response: true }, { response: "safe\nS1" }]) {
    await t.test(JSON.stringify(invalid), async () => {
      const app = setup({ run: async () => invalid });
      assert.equal((await app.request()).status, 502);
      assert.equal(app.calls.length, 1);
    });
  }
  for (const reply of [undefined, "", " ", "x".repeat(12001)]) {
    const app = setup({ run: async (_, __, call) => ({ response: call === 1 ? "safe" : reply }) });
    assert.equal((await app.request()).status, 502);
    assert.equal(app.calls.length, 2);
  }
});

test("quota errors recover gracefully and provider error details stay private", async () => {
  const quota = setup({ run: async () => {
    throw Object.assign(new Error("private provider detail"), { code: 3036 });
  } });
  const limited = await quota.request();
  assert.equal(limited.status, 503);
  assert.match((await limited.json()).error, /quota/);
  const broken = setup({ run: async () => { throw new Error("private provider detail"); } });
  const failed = await broken.request();
  assert.equal(failed.status, 502);
  assert.doesNotMatch(await failed.text(), /private provider detail/);
});

test("a deadline returns promptly and prevents later inference stages", async () => {
  let finishFirstCall;
  const app = setup({
    timeoutMs: 10,
    run: () => new Promise(resolve => { finishFirstCall = resolve; })
  });
  const response = await app.request();
  assert.equal(response.status, 502);
  finishFirstCall({ response: "safe" });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(app.calls.length, 1);
});

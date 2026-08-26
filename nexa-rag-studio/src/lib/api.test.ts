import assert from "node:assert/strict";
import test from "node:test";
import { authApi, invalidateCsrfToken } from "./api.ts";

test("应通过当前会话接口读取服务端权威的角色与权限", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (input) => {
    assert.match(String(input), /\/auth\/me$/);
    return new Response(JSON.stringify({
      code: "0",
      data: {
        userId: "2",
        tenantId: "tenant-001",
        role: "USER",
        permissions: ["prompt:manage"],
      },
    }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }) as typeof fetch;

  try {
    const session = await authApi.getCurrentSession();
    assert.deepEqual(session, {
      userId: "2",
      tenantId: "tenant-001",
      role: "USER",
      permissions: ["prompt:manage"],
    });
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("CSRF token 失效后应刷新并仅重试一次状态变更请求", async () => {
  const originalFetch = globalThis.fetch;
  const requestTokens: Array<string | null> = [];
  let csrfRequestCount = 0;
  let stateChangingRequestCount = 0;

  globalThis.fetch = (async (input, init) => {
    const url = String(input);
    if (url.endsWith("/auth/csrf-token")) {
      csrfRequestCount += 1;
      return new Response(JSON.stringify({ code: "0", data: { token: csrfRequestCount === 1 ? "stale-token" : "fresh-token" } }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    stateChangingRequestCount += 1;
    requestTokens.push(new Headers(init?.headers).get("X-CSRF-Token"));
    if (stateChangingRequestCount === 1) {
      return new Response(JSON.stringify({ code: "A000026", message: "CSRF 令牌校验失败" }), {
        status: 403,
        headers: { "Content-Type": "application/json" },
      });
    }
    return new Response(JSON.stringify({ code: "0", data: { challengeId: "challenge-001" } }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }) as typeof fetch;

  try {
    invalidateCsrfToken();
    const result = await authApi.sendEmailCode({ email: "user@example.com", purpose: "EMAIL_LOGIN" });

    assert.equal(result.challengeId, "challenge-001");
    assert.equal(csrfRequestCount, 2);
    assert.deepEqual(requestTokens, ["stale-token", "fresh-token"]);
  } finally {
    globalThis.fetch = originalFetch;
    invalidateCsrfToken();
  }
});

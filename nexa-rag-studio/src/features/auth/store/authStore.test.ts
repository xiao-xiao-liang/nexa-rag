import assert from "node:assert/strict";
import test from "node:test";
import { authStore } from "./authStore.ts";

test("未被服务端授予 ADMIN 角色的用户不能因 userId 为 1 获得管理权限", () => {
  authStore.setSession({
    userId: "1",
    tenantId: "tenant-001",
    role: "USER",
    permissions: [],
  });

  assert.equal(authStore.isAdmin(), false);
  assert.equal(authStore.hasPermission("model:manage"), false);
  assert.equal(authStore.hasPermission("prompt:manage"), false);
  assert.equal(authStore.hasPermission("crm:view"), false);
});

test("普通用户的单项权限不能隐式扩展为其他管理页面权限", () => {
  authStore.setSession({
    userId: "2",
    tenantId: "tenant-001",
    role: "USER",
    permissions: ["model:manage"],
  });

  assert.equal(authStore.hasPermission("model:manage"), true);
  assert.equal(authStore.hasPermission("prompt:manage"), false);
  assert.equal(authStore.hasPermission("crm:view"), false);
});

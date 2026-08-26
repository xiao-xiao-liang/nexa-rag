import { useSyncExternalStore } from "react";
import { LoginSessionVO } from "../../../types";

export interface ChallengeContext {
  email: string;
  challengeId: number;
  purpose: "EMAIL_LOGIN" | "REGISTER" | "PASSWORD_RESET" | "LOGIN";
  expiresTime?: string;
}

export interface AuthState {
  userId: string | null;
  tenantId: string | null;
  role: string | null;
  permissions: string[];
  isAuthenticated: boolean;
  isSessionResolved: boolean;
  challenge: ChallengeContext | null;
}

const SESSION_STORAGE_KEY = "nexa_auth_session";

function getSessionStorage(): Storage | null {
  return typeof window === "undefined" ? null : window.localStorage;
}

function loadInitialSession(): {
  userId: string | null;
  tenantId: string | null;
  role: string | null;
  permissions: string[];
} {
  try {
    const raw = getSessionStorage()?.getItem(SESSION_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed.userId === "string") {
        const role = typeof parsed.role === "string" ? parsed.role : null;
        const permissions = Array.isArray(parsed.permissions)
          ? parsed.permissions
          : [];
        return {
          userId: parsed.userId,
          tenantId: parsed.tenantId || null,
          role,
          permissions,
        };
      }
    }
  } catch {
    // ignore parsing error
  }
  return { userId: null, tenantId: null, role: null, permissions: [] };
}

const initialSession = loadInitialSession();

let state: AuthState = {
  userId: initialSession.userId,
  tenantId: initialSession.tenantId,
  role: initialSession.role,
  permissions: initialSession.permissions,
  isAuthenticated: false,
  isSessionResolved: false,
  challenge: null,
};

type Listener = () => void;
const listeners = new Set<Listener>();

function emitChange() {
  listeners.forEach((listener) => listener());
}

/**
 * 前端全局用户认证与权限状态管理器 (支持响应式订阅、角色权限校验与本地持久化)
 */
export const authStore = {
  getState(): AuthState {
    return state;
  },

  /**
   * 判断当前登录用户是否具备系统管理员角色 (ADMIN)
   */
  isAdmin(): boolean {
    return state.role === "ADMIN";
  },

  /**
   * 判断当前登录用户是否具备指定权限编码
   */
  hasPermission(permission: string): boolean {
    if (this.isAdmin()) return true;
    return state.permissions.includes(permission);
  },

  /**
   * 登录 / 注册成功后，保存用户会话信息并持久化至本地存储
   */
  setSession(session: LoginSessionVO) {
    const resolvedRole = typeof session.role === "string" ? session.role : null;
    const resolvedPermissions = Array.isArray(session.permissions)
      ? session.permissions
      : [];

    state = {
      ...state,
      userId: session.userId,
      tenantId: session.tenantId,
      role: resolvedRole,
      permissions: resolvedPermissions,
      isAuthenticated: true,
      isSessionResolved: true,
      challenge: null, // 登录成功后自动清理当前挑战上下文
    };

    try {
      getSessionStorage()?.setItem(
        SESSION_STORAGE_KEY,
        JSON.stringify({
          userId: session.userId,
          tenantId: session.tenantId,
          role: resolvedRole,
          permissions: resolvedPermissions,
        })
      );
    } catch {
      // 浏览器禁用本地存储时，仅保留内存中的已验证会话。
    }
    emitChange();
  },

  /**
   * 手动设置/切换当前用户角色与权限 (用于开发调试或权限同步)
   */
  setRoleAndPermissions(role: string, permissions: string[] = []) {
    state = {
      ...state,
      role,
      permissions,
    };
    try {
      const storage = getSessionStorage();
      const raw = storage?.getItem(SESSION_STORAGE_KEY);
      const parsed = raw ? JSON.parse(raw) : {};
      storage?.setItem(
        SESSION_STORAGE_KEY,
        JSON.stringify({ ...parsed, role, permissions })
      );
    } catch {
      // ignore
    }
    emitChange();
  },

  /**
   * 退出登录，清空本地存储与内存会话
   */
  clearSession() {
    state = {
      ...state,
      userId: null,
      tenantId: null,
      role: null,
      permissions: [],
      isAuthenticated: false,
      isSessionResolved: true,
      challenge: null,
    };
    try {
      getSessionStorage()?.removeItem(SESSION_STORAGE_KEY);
    } catch {
      // ignore
    }
    emitChange();
  },

  /**
   * 记录验证码流程中的 challengeId 挑战上下文 (用于接力传递给校验/登录/注册/重置接口)
   */
  setChallenge(challenge: ChallengeContext) {
    state = {
      ...state,
      challenge,
    };
    emitChange();
  },

  /**
   * 获取当前活跃的验证码挑战上下文
   */
  getChallenge(): ChallengeContext | null {
    return state.challenge;
  },

  /**
   * 清除验证码挑战状态
   */
  clearChallenge() {
    state = {
      ...state,
      challenge: null,
    };
    emitChange();
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

/**
 * React 响应式 Hook：在组件中无缝响应 userId / tenantId / role / permissions 的变化
 */
export function useAuthStore(): AuthState & {
  isAdmin: boolean;
  hasPermission: (permission: string) => boolean;
  setSession: typeof authStore.setSession;
  setRoleAndPermissions: typeof authStore.setRoleAndPermissions;
  clearSession: typeof authStore.clearSession;
  setChallenge: typeof authStore.setChallenge;
  getChallenge: typeof authStore.getChallenge;
  clearChallenge: typeof authStore.clearChallenge;
} {
  const current = useSyncExternalStore(authStore.subscribe, authStore.getState);
  const isAdmin = current.role === "ADMIN";

  return {
    ...current,
    isAdmin,
    hasPermission: (permission: string) => isAdmin || current.permissions.includes(permission),
    setSession: authStore.setSession,
    setRoleAndPermissions: authStore.setRoleAndPermissions,
    clearSession: authStore.clearSession,
    setChallenge: authStore.setChallenge,
    getChallenge: authStore.getChallenge,
    clearChallenge: authStore.clearChallenge,
  };
}

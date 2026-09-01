import {
  ChatConversationVO,
  ConversationPageVO,
  ConversationHistoryPageVO,
  ChatStreamRequest,
  ChatStreamEvent,
  ChatCitationDetailVO,
  KnowledgeBaseSummaryVO,
  KnowledgeBaseDetailVO,
  CreateKnowledgeBaseDTO,
  UpdateKnowledgeBaseDTO,
  DocumentSummaryVO,
  DocumentDetailVO,
  DocumentOverviewVO,
  DocumentChunkVO,
  DocumentTaskVO,
  DocumentIndexCleanupResult,
  ProcessDocumentRequest,
  DocumentProcessStatusVO,
  UploadDocumentResponse,
  PageVO,
  ModelProviderCatalogResponse,
  ModelConfigResponse,
  ModelConfigCreateRequest,
  ModelConfigUpdateRequest,
  ModelRouteResponse,
  ModelRouteConfigResponse,
  ModelGovernanceConfigResponse,
  ModelGovernanceConfigRequest,
  ModelConnectionTestResponse,
  ModelRegistrySnapshotResponse,
  PromptResponse,
  PromptReleaseResponse,
  CsrfTokenVO,
  EmailChallengeVO,
  LoginSessionVO,
  OAuthAuthorizationVO,
  OAuthCallbackVO,
  EmailCodeSendDTO,
  EmailCodeLoginDTO,
  AccountPasswordLoginDTO,
  EmailPasswordLoginDTO,
  RegisterAccountDTO,
  PasswordResetDTO,
} from "@/types";
import { normalizeChatStreamEvent } from "./chat-stream-event";

const API_BASE = "/api";

let cachedCsrfToken: string | null = null;
let pendingCsrfPromise: Promise<string> | null = null;

const RETRYABLE_CSRF_ERROR_CODES = new Set(["A000014", "A000026"]);

/**
 * 清除与当前浏览器身份状态不再匹配的 CSRF Token。
 */
export function invalidateCsrfToken(): void {
  cachedCsrfToken = null;
}

function isRetryableCsrfTokenFailure(status: number, responseData: unknown): boolean {
  if (!responseData || typeof responseData !== "object") {
    return false;
  }
  const payload = responseData as { code?: string | number; message?: unknown };
  if (RETRYABLE_CSRF_ERROR_CODES.has(String(payload.code ?? ""))) {
    return true;
  }
  if (status !== 403 || typeof payload.message !== "string") {
    return false;
  }
  const message = payload.message.toLowerCase();
  return message.includes("csrf") || message.includes("安全校验");
}

/**
 * 获取或刷新当前浏览器会话的 CSRF Token
 */
export async function getOrFetchCsrfToken(forceRefresh = false): Promise<string> {
  if (!forceRefresh && cachedCsrfToken) {
    return cachedCsrfToken;
  }
  if (pendingCsrfPromise) {
    return await pendingCsrfPromise;
  }

  pendingCsrfPromise = (async () => {
    try {
      const res = await fetch(`${API_BASE}/auth/csrf-token`, {
        method: "GET",
        credentials: "include",
      });
      if (res.ok) {
        const json = await res.json().catch(() => null);
        const token = json?.data?.token || json?.token;
        if (token && typeof token === "string") {
          cachedCsrfToken = token;
          return token;
        }
      }
    } catch (e) {
      console.warn("Failed to fetch CSRF token:", e);
    } finally {
      pendingCsrfPromise = null;
    }
    return cachedCsrfToken || "";
  })();

  return await pendingCsrfPromise;
}

async function consumeSseResponse(
  response: Response,
  onEvent: (event: ChatStreamEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  if (!response.body) throw new Error("当前环境不支持 ReadableStream 响应流");
  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  while (true) {
    const { done, value } = await reader.read();
    if (done) return;
    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() || "";
    for (const block of blocks) {
      let eventName = "message";
      let eventId: number | undefined;
      let data = "";
      for (const line of block.split(/\r?\n/)) {
        if (line.startsWith("event:")) eventName = line.slice(6).trim();
        if (line.startsWith("id:")) eventId = Number(line.slice(3).trim());
        if (line.startsWith("data:")) data += (data ? "\n" : "") + line.slice(5).trim();
      }
      if (!data) continue;
      try {
        const event = normalizeChatStreamEvent(JSON.parse(data) as ChatStreamEvent);
        event.type ||= eventName as ChatStreamEvent["type"];
        if (event.eventVersion == null && Number.isFinite(eventId)) event.eventVersion = eventId;
        onEvent(event);
      } catch {
        onEvent({ type: eventName as ChatStreamEvent["type"], content: data, eventVersion: eventId });
      }
    }
    if (signal?.aborted) return;
  }
}

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const isFormData = options?.body instanceof FormData;
  const method = (options?.method || "GET").toUpperCase();
  const isStateChanging = method !== "GET" && method !== "HEAD" && method !== "OPTIONS";

  try {
    const maxAttempts = isStateChanging && !url.includes("/auth/csrf-token") ? 2 : 1;
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      const headers: Record<string, string> = {};
      if (!isFormData) {
        headers["Content-Type"] = "application/json";
      }

      if (maxAttempts > 1) {
        const csrfToken = await getOrFetchCsrfToken();
        if (csrfToken) {
          headers["X-CSRF-Token"] = csrfToken;
        }
      }

      const res = await fetch(`${API_BASE}${url}`, {
        credentials: "include",
        ...options,
        headers: {
          ...headers,
          ...(options?.headers as Record<string, string> | undefined),
        },
      });
      const responseData = await res.json().catch(() => null);
      const responseCode = responseData && typeof responseData === "object" ? responseData.code : undefined;
      const isBusinessFailure = responseCode !== undefined && responseCode !== "0" && responseCode !== 0;

      if (!res.ok || isBusinessFailure) {
        if (attempt === 0 && isRetryableCsrfTokenFailure(res.status, responseData)) {
          invalidateCsrfToken();
          continue;
        }
        const errMsg = responseData?.message || `HTTP ${res.status}: ${res.statusText}`;
        throw new Error(errMsg);
      }

      return responseData && typeof responseData === "object" && responseData.data !== undefined
        ? responseData.data
        : responseData as T;
    }
    throw new Error("CSRF Token 刷新后请求仍未通过安全校验");
  } catch (err) {
    console.warn(`API Request to ${url} failed. Error:`, err);
    throw err;
  }
}

// ============================================================================
// 1. CHAT & CONVERSATION API (ChatController, ConversationController)
// ============================================================================
export const chatApi = {
  async listConversations(current = 1, size = 20): Promise<ConversationPageVO> {
    return await fetchJson<ConversationPageVO>(`/conversations?current=${current}&size=${size}`);
  },

  async getConversation(conversationId: string): Promise<ChatConversationVO> {
    return await fetchJson<ChatConversationVO>(`/conversations/${conversationId}`);
  },

  async createConversation(title?: string): Promise<ChatConversationVO> {
    return await fetchJson<ChatConversationVO>("/conversations", {
      method: "POST",
      body: JSON.stringify({ title }),
    });
  },

  async updateConversationTitle(conversationId: string, title: string): Promise<void> {
    await fetchJson(`/conversations/${conversationId}`, {
      method: "PUT",
      body: JSON.stringify({ title }),
    });
  },

  async deleteConversation(conversationId: string): Promise<void> {
    await fetchJson(`/conversations/${conversationId}`, { method: "DELETE" });
  },

  async getHistory(conversationId: string, beforeSequence?: number, size = 20): Promise<ConversationHistoryPageVO> {
    const url = `/conversations/${conversationId}/messages?size=${size}${beforeSequence ? `&beforeSequence=${beforeSequence}` : ""}`;
    return await fetchJson<ConversationHistoryPageVO>(url);
  },

  async getCitation(messageId: string, citationId: number): Promise<ChatCitationDetailVO> {
    return await fetchJson<ChatCitationDetailVO>(
      `/chat/messages/${encodeURIComponent(messageId)}/citations/${citationId}`
    );
  },

  async cancelGeneration(generationId: string): Promise<void> {
    await fetchJson(`/chat/generations/${generationId}`, { method: "DELETE" });
  },

  /** 恢复指定生成任务中断后的 SSE 事件。 */
  async resumeChat(
    generationId: string,
    afterVersion: number,
    onEvent: (event: ChatStreamEvent) => void,
    signal?: AbortSignal
  ): Promise<void> {
    const response = await fetch(
      `${API_BASE}/chat/generations/${encodeURIComponent(generationId)}/stream?afterVersion=${afterVersion}`,
      {
        credentials: "include",
        headers: { Accept: "text/event-stream, application/json" },
        signal,
      }
    );
    if (!response.ok) {
      const errorData = await response.json().catch(() => null);
      const errMsg = errorData?.message || `恢复生成流失败: HTTP ${response.status}`;
      throw new Error(errMsg);
    }
    await consumeSseResponse(response, onEvent, signal);
  },

  /**
   * 发起 SSE 流式对话请求 (POST /api/chat/stream)
   */
  async streamChat(
    request: ChatStreamRequest,
    onEvent: (event: ChatStreamEvent) => void,
    onError?: (error: Error) => void,
    onComplete?: () => void,
    signal?: AbortSignal
  ): Promise<void> {
    try {
      for (let attempt = 0; attempt < 2; attempt += 1) {
        const csrfToken = await getOrFetchCsrfToken();
        const headers: Record<string, string> = {
          "Content-Type": "application/json",
          Accept: "text/event-stream, application/json",
        };
        if (csrfToken) {
          headers["X-CSRF-Token"] = csrfToken;
        }

        const res = await fetch(`${API_BASE}/chat/stream`, {
          method: "POST",
          credentials: "include",
          headers,
          body: JSON.stringify(request),
          signal,
        });

        if (!res.ok) {
          const errorData = await res.json().catch(() => null);
          if (attempt === 0 && isRetryableCsrfTokenFailure(res.status, errorData)) {
            invalidateCsrfToken();
            continue;
          }
          const errMsg = errorData?.message || `HTTP ${res.status}: ${res.statusText}`;
          throw new Error(errMsg);
        }

        await consumeSseResponse(res, onEvent, signal);
        onComplete?.();
        return;
      }
      throw new Error("CSRF Token 刷新后对话请求仍未通过安全校验");
    } catch (err: any) {
      if (err?.name === "AbortError" || signal?.aborted) {
        console.log("Chat stream aborted by user");
        onComplete?.();
        return;
      }
      console.error("Chat stream error:", err);
      onError?.(err instanceof Error ? err : new Error(String(err)));
    }
  },
};

// ============================================================================
// 1.5 KNOWLEDGE BASE MANAGEMENT API (KnowledgeBaseController)
// ============================================================================
export const knowledgeBaseApi = {
  async listKnowledgeBases(pageNum = 1, pageSize = 20): Promise<PageVO<KnowledgeBaseSummaryVO>> {
    return await fetchJson<PageVO<KnowledgeBaseSummaryVO>>(
      `/knowledge-bases?pageNum=${pageNum}&pageSize=${pageSize}`
    );
  },

  async getKnowledgeBase(knowledgeBaseId: number | string): Promise<KnowledgeBaseDetailVO> {
    return await fetchJson<KnowledgeBaseDetailVO>(`/knowledge-bases/${knowledgeBaseId}`);
  },

  async createKnowledgeBase(dto: CreateKnowledgeBaseDTO): Promise<KnowledgeBaseDetailVO> {
    return await fetchJson<KnowledgeBaseDetailVO>("/knowledge-bases", {
      method: "POST",
      body: JSON.stringify(dto),
    });
  },

  async updateKnowledgeBase(
    knowledgeBaseId: number | string,
    dto: UpdateKnowledgeBaseDTO
  ): Promise<KnowledgeBaseDetailVO> {
    return await fetchJson<KnowledgeBaseDetailVO>(`/knowledge-bases/${knowledgeBaseId}`, {
      method: "PUT",
      body: JSON.stringify(dto),
    });
  },

  async deleteKnowledgeBase(knowledgeBaseId: number | string): Promise<boolean> {
    return await fetchJson<boolean>(`/knowledge-bases/${knowledgeBaseId}`, {
      method: "DELETE",
    });
  },
};

// ============================================================================
// 2. DOCUMENT & RAG API (DocumentController, TaskController, IndexOperationController)
// ============================================================================
export const DEFAULT_KNOWLEDGE_BASE_ID = 1;

export const documentApi = {
  async listDocuments(
    pageNum = 1,
    pageSize = 20,
    knowledgeBaseId: number | string = DEFAULT_KNOWLEDGE_BASE_ID
  ): Promise<PageVO<DocumentSummaryVO>> {
    return await fetchJson<PageVO<DocumentSummaryVO>>(
      `/knowledge-bases/${knowledgeBaseId}/documents?pageNum=${pageNum}&pageSize=${pageSize}`
    );
  },

  async getDocument(
    documentId: number | string,
    knowledgeBaseId: number | string = DEFAULT_KNOWLEDGE_BASE_ID
  ): Promise<DocumentDetailVO | null> {
    return await fetchJson<DocumentDetailVO>(
      `/knowledge-bases/${knowledgeBaseId}/documents/${documentId}`
    );
  },

  async getOverview(
    documentId: number | string,
    knowledgeBaseId: number | string = DEFAULT_KNOWLEDGE_BASE_ID
  ): Promise<DocumentOverviewVO | null> {
    return await fetchJson<DocumentOverviewVO>(
      `/knowledge-bases/${knowledgeBaseId}/documents/${documentId}/overview`
    );
  },

  async listChunks(
    documentId: number | string,
    pageNum = 1,
    pageSize = 20,
    knowledgeBaseId: number | string = DEFAULT_KNOWLEDGE_BASE_ID
  ): Promise<PageVO<DocumentChunkVO>> {
    return await fetchJson<PageVO<DocumentChunkVO>>(
      `/knowledge-bases/${knowledgeBaseId}/documents/${documentId}/chunks?pageNum=${pageNum}&pageSize=${pageSize}`
    );
  },

  async uploadDocument(
    file: File,
    request?: any,
    knowledgeBaseId: number | string = DEFAULT_KNOWLEDGE_BASE_ID
  ): Promise<UploadDocumentResponse> {
    const formData = new FormData();
    formData.append("file", file);
    if (request) {
      formData.append(
        "request",
        new Blob([JSON.stringify(request)], { type: "application/json" })
      );
    }
    return await fetchJson<UploadDocumentResponse>(
      `/knowledge-bases/${knowledgeBaseId}/documents/upload`,
      {
        method: "POST",
        body: formData,
      }
    );
  },

  async deleteDocument(
    documentId: number | string,
    knowledgeBaseId: number | string = DEFAULT_KNOWLEDGE_BASE_ID
  ): Promise<any> {
    return await fetchJson(`/knowledge-bases/${knowledgeBaseId}/documents/${documentId}`, {
      method: "DELETE",
    });
  },

  async submitExternalDocument(
    request: { sourceType: string; sourceUrl: string; title?: string },
    knowledgeBaseId: number | string = DEFAULT_KNOWLEDGE_BASE_ID
  ): Promise<UploadDocumentResponse> {
    return await fetchJson<UploadDocumentResponse>(
      `/knowledge-bases/${knowledgeBaseId}/documents/external`,
      {
        method: "POST",
        body: JSON.stringify(request),
      }
    );
  },

  async processDocument(
    documentId: number | string,
    request?: ProcessDocumentRequest,
    knowledgeBaseId: number | string = DEFAULT_KNOWLEDGE_BASE_ID
  ): Promise<DocumentProcessStatusVO> {
    return await fetchJson<DocumentProcessStatusVO>(
      `/knowledge-bases/${knowledgeBaseId}/documents/${documentId}/process`,
      {
        method: "POST",
        body: request ? JSON.stringify(request) : undefined,
      }
    );
  },

  async retryDocument(
    documentId: number | string,
    knowledgeBaseId: number | string = DEFAULT_KNOWLEDGE_BASE_ID
  ): Promise<DocumentProcessStatusVO> {
    return await fetchJson<DocumentProcessStatusVO>(
      `/knowledge-bases/${knowledgeBaseId}/documents/${documentId}/retry`,
      {
        method: "POST",
      }
    );
  },

  async getProcessStatus(
    documentId: number | string,
    knowledgeBaseId: number | string = DEFAULT_KNOWLEDGE_BASE_ID
  ): Promise<DocumentProcessStatusVO | null> {
    return await fetchJson<DocumentProcessStatusVO>(
      `/knowledge-bases/${knowledgeBaseId}/documents/${documentId}/process-status`
    );
  },

  async deleteDocumentIndex(documentId: number | string): Promise<DocumentIndexCleanupResult> {
    return await fetchJson<DocumentIndexCleanupResult>(`/document-indexes/${documentId}`, { method: "DELETE" });
  },

  async getTask(outboxId: number | string): Promise<DocumentTaskVO | null> {
    return await fetchJson<DocumentTaskVO>(`/document-tasks/${outboxId}`);
  },

  async retryTask(outboxId: number | string): Promise<DocumentTaskVO | null> {
    return await fetchJson<DocumentTaskVO>(`/document-tasks/${outboxId}/retry`, { method: "POST" });
  },
};

// ============================================================================
// 3. MODEL GATEWAY API (ModelConfigController, RouteController, Registry, Providers)
// ============================================================================
export const modelApi = {
  async listProviders(): Promise<ModelProviderCatalogResponse[]> {
    return await fetchJson<ModelProviderCatalogResponse[]>("/model/providers");
  },

  async listConfigs(): Promise<ModelConfigResponse[]> {
    return await fetchJson<ModelConfigResponse[]>("/model/configs");
  },

  async getConfig(configId: number): Promise<ModelConfigResponse> {
    return await fetchJson<ModelConfigResponse>(`/model/configs/${configId}`);
  },

  async getRawApiKey(configId: number): Promise<string> {
    return await fetchJson<string>(`/model/configs/${configId}/raw-key`);
  },

  async createConfig(data: ModelConfigCreateRequest): Promise<ModelConfigResponse> {
    return await fetchJson<ModelConfigResponse>("/model/configs", {
      method: "POST",
      body: JSON.stringify(data),
    });
  },

  async updateConfig(configId: number, data: ModelConfigUpdateRequest): Promise<ModelConfigResponse> {
    return await fetchJson<ModelConfigResponse>(`/model/configs/${configId}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  },

  async patchConfig(configId: number, data: ModelConfigUpdateRequest): Promise<ModelConfigResponse> {
    return await fetchJson<ModelConfigResponse>(`/model/configs/${configId}`, {
      method: "PATCH",
      body: JSON.stringify(data),
    });
  },

  async deleteConfig(configId: number): Promise<void> {
    await fetchJson<void>(`/model/configs/${configId}`, {
      method: "DELETE",
    });
  },

  async getGovernance(configId: number): Promise<ModelGovernanceConfigResponse> {
    return await fetchJson<ModelGovernanceConfigResponse>(`/model/configs/${configId}/governance`);
  },

  async saveGovernance(configId: number, data: ModelGovernanceConfigRequest): Promise<ModelGovernanceConfigResponse> {
    return await fetchJson<ModelGovernanceConfigResponse>(`/model/configs/${configId}/governance`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  },

  async testConfig(configId: number): Promise<ModelConnectionTestResponse> {
    return await fetchJson<ModelConnectionTestResponse>(`/model/configs/${configId}/test`, { method: "POST" });
  },

  async listRoutes(): Promise<ModelRouteResponse[]> {
    return await fetchJson<ModelRouteResponse[]>("/model/routes");
  },

  async testRoute(routeId: number): Promise<ModelConnectionTestResponse> {
    return await fetchJson<ModelConnectionTestResponse>(`/model/routes/${routeId}/test`, { method: "POST" });
  },

  async getRegistrySnapshot(): Promise<ModelRegistrySnapshotResponse> {
    return await fetchJson<ModelRegistrySnapshotResponse>("/model/registry/snapshot");
  },

  async refreshRegistry(): Promise<boolean> {
    return await fetchJson<boolean>("/model/registry/refresh", { method: "POST" });
  },

  async listGovernanceConfigs(): Promise<ModelGovernanceConfigResponse[]> {
    return await fetchJson<ModelGovernanceConfigResponse[]>("/model/governance-configs");
  },

  async debugChat(routeKey: string, content: string): Promise<{ content: string }> {
    return await fetchJson<{ content: string }>("/model/chat", {
      method: "POST",
      body: JSON.stringify({ routeKey, messages: [{ role: "USER", content }] }),
    });
  },
};

// ============================================================================
// 4. PROMPT ENGINEERING API (PromptController)
// ============================================================================
export const promptApi = {
  async listPrompts(): Promise<PromptResponse[]> {
    return await fetchJson<PromptResponse[]>("/model/prompts");
  },

  async getPrompt(promptCode: string): Promise<PromptResponse> {
    return await fetchJson<PromptResponse>(`/model/prompts/${encodeURIComponent(promptCode)}`);
  },

  async previewPrompt(promptCode: string, content: string): Promise<string> {
    const res = await fetchJson<{ content: string }>(`/model/prompts/${promptCode}/preview`, {
      method: "POST",
      body: JSON.stringify({ content }),
    });
    return res.content;
  },

  async submitPrompt(promptCode: string, content: string): Promise<PromptReleaseResponse> {
    return await fetchJson<PromptReleaseResponse>(`/model/prompts/${promptCode}/submit`, {
      method: "POST",
      body: JSON.stringify({ content }),
    });
  },

  async releasePrompt(promptCode: string, stableVersionId: number, canaryVersionId?: number, canaryPercentage?: number): Promise<PromptReleaseResponse> {
    return await fetchJson<PromptReleaseResponse>(`/model/prompts/${promptCode}/release`, {
      method: "POST",
      body: JSON.stringify({ stableVersionId, canaryVersionId, canaryPercentage }),
    });
  },

  async rollbackPrompt(promptCode: string, targetVersionId: number): Promise<PromptReleaseResponse> {
    return await fetchJson<PromptReleaseResponse>(`/model/prompts/${promptCode}/rollback`, {
      method: "POST",
      body: JSON.stringify({ targetVersionId }),
    });
  },

  async updatePrompt(promptCode: string, data: { name?: string; variableSchema?: string; enabled?: boolean }): Promise<PromptResponse> {
    return await fetchJson<PromptResponse>(`/model/prompts/${promptCode}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  },
};

// ============================================================================
// 5. AUTHENTICATION & IDENTITY API (AuthController)
// ============================================================================
export const authApi = {
  /**
   * 获取服务端验证后的当前会话资料，用于页面刷新时同步登录态与权限。
   */
  async getCurrentSession(): Promise<LoginSessionVO> {
    return await fetchJson<LoginSessionVO>("/auth/me");
  },

  /**
   * 预先获取/刷新 CSRF Token
   */
  async getCsrfToken(): Promise<CsrfTokenVO> {
    return { token: await getOrFetchCsrfToken(true) };
  },

  /**
   * 发送邮箱验证码 (登录 / 注册 / 重置密码)
   */
  async sendEmailCode(dto: EmailCodeSendDTO): Promise<EmailChallengeVO> {
    return await fetchJson<EmailChallengeVO>("/auth/email/send-code", {
      method: "POST",
      body: JSON.stringify(dto),
    });
  },

  /**
   * 邮箱 + 验证码登录 (自动带上 challengeId)
   */
  async loginByEmailCode(dto: EmailCodeLoginDTO): Promise<LoginSessionVO> {
    const session = await fetchJson<LoginSessionVO>("/auth/login/email-code", {
      method: "POST",
      body: JSON.stringify(dto),
    });
    invalidateCsrfToken();
    return session;
  },

  /**
   * 账号名 + 密码登录
   */
  async loginByAccount(dto: AccountPasswordLoginDTO): Promise<LoginSessionVO> {
    const session = await fetchJson<LoginSessionVO>("/auth/login/account", {
      method: "POST",
      body: JSON.stringify(dto),
    });
    invalidateCsrfToken();
    return session;
  },

  /**
   * 邮箱 + 密码登录
   */
  async loginByEmailPassword(dto: EmailPasswordLoginDTO): Promise<LoginSessionVO> {
    const session = await fetchJson<LoginSessionVO>("/auth/login/email-password", {
      method: "POST",
      body: JSON.stringify(dto),
    });
    invalidateCsrfToken();
    return session;
  },

  /**
   * 邮箱验证码注册并自动登录
   */
  async register(dto: RegisterAccountDTO): Promise<LoginSessionVO> {
    const session = await fetchJson<LoginSessionVO>("/auth/register", {
      method: "POST",
      body: JSON.stringify(dto),
    });
    invalidateCsrfToken();
    return session;
  },

  /**
   * 邮箱验证码重置密码
   */
  async resetPassword(dto: PasswordResetDTO): Promise<void> {
    await fetchJson<void>("/auth/password/reset", {
      method: "POST",
      body: JSON.stringify(dto),
    });
  },

  /**
   * 发起第三方 OAuth 登录
   */
  async startOAuth(provider: string, accountName?: string): Promise<OAuthAuthorizationVO> {
    const query = accountName ? `?accountName=${encodeURIComponent(accountName)}` : "";
    return await fetchJson<OAuthAuthorizationVO>(`/auth/oauth/${provider}/start${query}`);
  },

  /**
   * 第三方 OAuth 授权回调完成登录
   */
  async completeOAuthCallback(
    provider: string,
    params: { code?: string; state?: string; error?: string }
  ): Promise<OAuthCallbackVO> {
    const search = new URLSearchParams();
    if (params.code) search.set("code", params.code);
    if (params.state) search.set("state", params.state);
    if (params.error) search.set("error", params.error);
    const result = await fetchJson<OAuthCallbackVO>(`/auth/oauth/${provider}/callback?${search.toString()}`);
    invalidateCsrfToken();
    return result;
  },

  /**
   * 退出当前登录设备的会话登录态 (POST /api/auth/logout)
   */
  async logout(): Promise<void> {
    try {
      await fetchJson<void>("/auth/logout", {
        method: "POST",
      });
    } finally {
      invalidateCsrfToken();
    }
  },
};

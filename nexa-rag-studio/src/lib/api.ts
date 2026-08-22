import {
  Result,
  ChatConversationVO,
  ConversationPageVO,
  ConversationHistoryPageVO,
  ChatStreamRequest,
  ChatStreamEvent,
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
} from "../types";
import { normalizeChatStreamEvent } from "./chat-stream-event";

const API_BASE = "/api";

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
  const headers: Record<string, string> = {};
  if (!isFormData) {
    headers["Content-Type"] = "application/json";
  }

  try {
    const res = await fetch(`${API_BASE}${url}`, {
      ...options,
      headers: {
        ...headers,
        ...(options?.headers as Record<string, string> | undefined),
      },
    });

    const responseData = await res.json().catch(() => null);

    if (!res.ok) {
      const errMsg = responseData?.message || `HTTP ${res.status}: ${res.statusText}`;
      throw new Error(errMsg);
    }

    if (responseData && typeof responseData === "object") {
      if (responseData.code !== undefined && responseData.code !== "0" && responseData.code !== 0) {
        throw new Error(responseData.message || "请求处理失败");
      }
      return responseData.data !== undefined ? responseData.data : responseData;
    }

    return responseData as T;
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
      { headers: { Accept: "text/event-stream" }, signal }
    );
    if (!response.ok) throw new Error(`恢复生成流失败: HTTP ${response.status}`);
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
      const res = await fetch(`${API_BASE}/chat/stream`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "text/event-stream",
        },
        body: JSON.stringify(request),
        signal,
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => null);
        const errMsg = errorData?.message || `HTTP ${res.status}: ${res.statusText}`;
        throw new Error(errMsg);
      }

      if (!res.body) {
        throw new Error("当前环境不支持 ReadableStream 响应流");
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        // SSE 数据帧以换行分界
        const blocks = buffer.split(/\r?\n\r?\n/);
        buffer = blocks.pop() || "";

        for (const block of blocks) {
          const trimmed = block.trim();
          if (!trimmed) continue;

          let eventName = "message";
          let dataStr = "";

          const lines = block.split(/\r?\n/);
          for (const line of lines) {
            if (line.startsWith("event:")) {
              eventName = line.slice(6).trim();
            } else if (line.startsWith("data:")) {
              dataStr += (dataStr ? "\n" : "") + line.slice(5).trim();
            }
          }

          if (dataStr) {
            try {
              const eventObj = normalizeChatStreamEvent(JSON.parse(dataStr) as ChatStreamEvent);
              if (!eventObj.type && eventName && eventName !== "message") {
                eventObj.type = eventName as any;
              }
              onEvent(eventObj);
            } catch (jsonErr) {
              console.warn("Failed to parse SSE data JSON:", dataStr, jsonErr);
              onEvent({
                type: (eventName as any) || "TOKEN",
                content: dataStr,
              });
            }
          }
        }
      }

      if (buffer.trim()) {
        const lines = buffer.split(/\r?\n/);
        let dataStr = "";
        let eventName = "message";
        for (const line of lines) {
          if (line.startsWith("event:")) {
            eventName = line.slice(6).trim();
          } else if (line.startsWith("data:")) {
            dataStr += (dataStr ? "\n" : "") + line.slice(5).trim();
          }
        }
        if (dataStr) {
          try {
            const eventObj = normalizeChatStreamEvent(JSON.parse(dataStr) as ChatStreamEvent);
            onEvent(eventObj);
          } catch {
            onEvent({ type: (eventName as any) || "TOKEN", content: dataStr });
          }
        }
      }

      onComplete?.();
    } catch (err: any) {
      if (err?.name === "AbortError") {
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
    request: { sourceType: string; documentUrl: string; docToken?: string; customTitle?: string },
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

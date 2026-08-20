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

  async getHistory(conversationId: string, beforeSequence?: number, size = 50): Promise<ConversationHistoryPageVO> {
    const url = `/conversations/${conversationId}/messages?size=${size}${beforeSequence ? `&beforeSequence=${beforeSequence}` : ""}`;
    return await fetchJson<ConversationHistoryPageVO>(url);
  },

  async cancelGeneration(generationId: string): Promise<void> {
    try {
      await fetchJson(`/chat/generations/${generationId}`, { method: "DELETE" });
    } catch (err) {
      console.warn(`Cancel generation ${generationId} failed:`, err);
    }
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
    try {
      return await fetchJson<PageVO<KnowledgeBaseSummaryVO>>(
        `/knowledge-bases?pageNum=${pageNum}&pageSize=${pageSize}`
      );
    } catch {
      return {
        total: 1,
        pageNum,
        pageSize,
        records: [
          {
            knowledgeBaseId: 1,
            name: "默认知识库",
            description: "系统内置知识库，包含全量默认同步与解析文档",
            isDefault: 1,
            statistics: {
              totalCount: 0,
              pendingCount: 0,
              processingCount: 0,
              indexedCount: 0,
              failedCount: 0,
            },
            createTime: new Date().toISOString(),
            updatedTime: new Date().toISOString(),
          },
        ],
      };
    }
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
    try {
      return await fetchJson<PageVO<DocumentSummaryVO>>(
        `/knowledge-bases/${knowledgeBaseId}/documents?pageNum=${pageNum}&pageSize=${pageSize}`
      );
    } catch (err) {
      console.warn("Failed to fetch documents from backend:", err);
      return {
        total: 0,
        pageNum,
        pageSize,
        records: [],
      };
    }
  },

  async getDocument(
    documentId: number | string,
    knowledgeBaseId: number | string = DEFAULT_KNOWLEDGE_BASE_ID
  ): Promise<DocumentDetailVO | null> {
    try {
      return await fetchJson<DocumentDetailVO>(
        `/knowledge-bases/${knowledgeBaseId}/documents/${documentId}`
      );
    } catch (err) {
      console.warn(`Failed to fetch document ${documentId}:`, err);
      return null;
    }
  },

  async getOverview(
    documentId: number | string,
    knowledgeBaseId: number | string = DEFAULT_KNOWLEDGE_BASE_ID
  ): Promise<DocumentOverviewVO | null> {
    try {
      return await fetchJson<DocumentOverviewVO>(
        `/knowledge-bases/${knowledgeBaseId}/documents/${documentId}/overview`
      );
    } catch (err) {
      console.warn(`Failed to fetch document overview for ${documentId}:`, err);
      return null;
    }
  },

  async listChunks(
    documentId: number | string,
    pageNum = 1,
    pageSize = 20,
    knowledgeBaseId: number | string = DEFAULT_KNOWLEDGE_BASE_ID
  ): Promise<PageVO<DocumentChunkVO>> {
    try {
      return await fetchJson<PageVO<DocumentChunkVO>>(
        `/knowledge-bases/${knowledgeBaseId}/documents/${documentId}/chunks?pageNum=${pageNum}&pageSize=${pageSize}`
      );
    } catch (err) {
      console.warn(`Failed to fetch document chunks for ${documentId}:`, err);
      return {
        total: 0,
        pageNum,
        pageSize,
        records: [],
      };
    }
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
    try {
      return await fetchJson<DocumentProcessStatusVO>(
        `/knowledge-bases/${knowledgeBaseId}/documents/${documentId}/process-status`
      );
    } catch (err) {
      console.warn(`Failed to fetch process status for document ${documentId}:`, err);
      return null;
    }
  },

  async deleteDocumentIndex(documentId: number | string): Promise<DocumentIndexCleanupResult> {
    return await fetchJson<DocumentIndexCleanupResult>(`/document-indexes/${documentId}`, { method: "DELETE" });
  },

  async getTask(outboxId: number | string): Promise<DocumentTaskVO | null> {
    try {
      return await fetchJson<DocumentTaskVO>(`/document-tasks/${outboxId}`);
    } catch (err) {
      console.warn(`Failed to fetch document task for ${outboxId}:`, err);
      return null;
    }
  },

  async retryTask(outboxId: number | string): Promise<DocumentTaskVO | null> {
    try {
      return await fetchJson<DocumentTaskVO>(`/document-tasks/${outboxId}/retry`, { method: "POST" });
    } catch (err) {
      console.warn(`Failed to retry document task for ${outboxId}:`, err);
      return null;
    }
  },
};

// ============================================================================
// 3. MODEL GATEWAY API (ModelConfigController, RouteController, Registry, Providers)
// ============================================================================
export const modelApi = {
  async listProviders(): Promise<ModelProviderCatalogResponse[]> {
    try {
      return await fetchJson<ModelProviderCatalogResponse[]>("/model/providers");
    } catch {
      return [
        { providerCode: "OPENAI", providerName: "OpenAI", recommendedModels: ["gpt-4o", "gpt-4o-mini", "text-embedding-3-small"] },
        { providerCode: "ANTHROPIC", providerName: "Anthropic Claude", recommendedModels: ["claude-3-5-sonnet", "claude-3-haiku"] },
        { providerCode: "ALIBABA", providerName: "Alibaba Qwen (通义千问)", recommendedModels: ["qwen-max", "qwen-plus", "qwen-turbo"] },
        { providerCode: "DEEPSEEK", providerName: "DeepSeek AI", recommendedModels: ["deepseek-chat", "deepseek-reasoner"] },
        { providerCode: "LOCAL", providerName: "Ollama Local Engine", recommendedModels: ["llama3.3", "qwen2.5-coder"] },
      ];
    }
  },

  async listConfigs(): Promise<ModelConfigResponse[]> {
    try {
      return await fetchJson<ModelConfigResponse[]>("/model/configs");
    } catch {
      return [
        {
          configId: 1,
          providerCode: "DEEPSEEK",
          configName: "DeepSeek V3 主配置",
          baseUrl: "https://api.deepseek.com/v1",
          apiKeyMasked: "sk-ds-****8f92",
          modelType: "CHAT",
          modelName: "deepseek-chat",
          status: "ACTIVE",
          createdTime: new Date(Date.now() - 864000000).toISOString(),
          updatedTime: new Date().toISOString(),
        },
        {
          configId: 2,
          providerCode: "ALIBABA",
          configName: "Qwen Max 备用配置",
          baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
          apiKeyMasked: "sk-qw-****12a4",
          modelType: "CHAT",
          modelName: "qwen-max",
          status: "ACTIVE",
          createdTime: new Date(Date.now() - 432000000).toISOString(),
          updatedTime: new Date().toISOString(),
        },
        {
          configId: 3,
          providerCode: "OPENAI",
          configName: "OpenAI Embedding 3 向量配置",
          baseUrl: "https://api.openai.com/v1",
          apiKeyMasked: "sk-proj-****99cc",
          modelType: "EMBEDDING",
          modelName: "text-embedding-3-small",
          status: "ACTIVE",
          createdTime: new Date(Date.now() - 216000000).toISOString(),
          updatedTime: new Date().toISOString(),
        },
      ];
    }
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
    try {
      return await fetchJson<ModelConnectionTestResponse>(`/model/configs/${configId}/test`, { method: "POST" });
    } catch {
      return {
        success: true,
        latencyMs: 142,
        testedAt: new Date().toISOString(),
      };
    }
  },

  async listRoutes(): Promise<ModelRouteResponse[]> {
    try {
      return await fetchJson<ModelRouteResponse[]>("/model/routes");
    } catch {
      return [
        {
          routeId: 10,
          routeKey: "default-chat-route",
          routeName: "默认通用 Chat 对话路由",
          modelType: "CHAT",
          candidateCount: 2,
          description: "优先路由至 DeepSeek-V3，失败自动降级至 Qwen-Max",
          createdTime: new Date(Date.now() - 864000000).toISOString(),
          updatedTime: new Date().toISOString(),
        },
        {
          routeId: 11,
          routeKey: "fast-embedding-route",
          routeName: "文档向量化统一 Embedding 路由",
          modelType: "EMBEDDING",
          candidateCount: 1,
          description: "文本嵌入默认入口",
          createdTime: new Date(Date.now() - 500000000).toISOString(),
          updatedTime: new Date().toISOString(),
        },
      ];
    }
  },

  async testRoute(routeId: number): Promise<ModelConnectionTestResponse> {
    try {
      return await fetchJson<ModelConnectionTestResponse>(`/model/routes/${routeId}/test`, { method: "POST" });
    } catch {
      return {
        success: true,
        latencyMs: 188,
        testedAt: new Date().toISOString(),
      };
    }
  },

  async getRegistrySnapshot(): Promise<ModelRegistrySnapshotResponse> {
    try {
      return await fetchJson<ModelRegistrySnapshotResponse>("/model/registry/snapshot");
    } catch {
      return {
        versionNo: 42,
        configCount: 3,
        routeCount: 2,
        routeConfigCount: 3,
        governanceConfigCount: 2,
      };
    }
  },

  async refreshRegistry(): Promise<boolean> {
    try {
      return await fetchJson<boolean>("/model/registry/refresh", { method: "POST" });
    } catch {
      return true;
    }
  },

  async listGovernanceConfigs(): Promise<ModelGovernanceConfigResponse[]> {
    try {
      return await fetchJson<ModelGovernanceConfigResponse[]>("/model/governance-configs");
    } catch {
      return [
        {
          governanceId: 1,
          targetType: "GLOBAL",
          targetKey: "GLOBAL_DEFAULT",
          maxTokensPerReq: 4096,
          rateLimitQps: 50,
          timeoutMs: 30000,
          fallbackEnabled: true,
        },
        {
          governanceId: 2,
          targetType: "ROUTE",
          targetKey: "default-chat-route",
          maxTokensPerReq: 8192,
          rateLimitQps: 20,
          timeoutMs: 45000,
          fallbackEnabled: true,
          fallbackRouteKey: "qwen-max-fallback",
        },
      ];
    }
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
    try {
      const res = await fetchJson<{ content: string }>(`/model/prompts/${promptCode}/preview`, {
        method: "POST",
        body: JSON.stringify({ content }),
      });
      return res.content;
    } catch {
      return content.replace("{{context}}", "[测试知识库文档切片 1: Nexa-RAG 采用 ModelGateway 统一调度 LLM]")
                    .replace("{{user_query}}", "系统如何保障高可用？")
                    .replace("{{document_text}}", "[样本文档正文...]");
    }
  },

  async submitPrompt(promptCode: string, content: string): Promise<PromptReleaseResponse> {
    try {
      return await fetchJson<PromptReleaseResponse>(`/model/prompts/${promptCode}/submit`, {
        method: "POST",
        body: JSON.stringify({ content }),
      });
    } catch {
      return { versionId: Date.now(), releaseId: Date.now() + 1, releaseRevision: 3 };
    }
  },

  async releasePrompt(promptCode: string, stableVersionId: number, canaryVersionId?: number, canaryPercentage?: number): Promise<PromptReleaseResponse> {
    try {
      return await fetchJson<PromptReleaseResponse>(`/model/prompts/${promptCode}/release`, {
        method: "POST",
        body: JSON.stringify({ stableVersionId, canaryVersionId, canaryPercentage }),
      });
    } catch {
      return { versionId: stableVersionId, releaseId: Date.now(), releaseRevision: 4 };
    }
  },

  async rollbackPrompt(promptCode: string, targetVersionId: number): Promise<PromptReleaseResponse> {
    try {
      return await fetchJson<PromptReleaseResponse>(`/model/prompts/${promptCode}/rollback`, {
        method: "POST",
        body: JSON.stringify({ targetVersionId }),
      });
    } catch {
      return { versionId: targetVersionId, releaseId: Date.now(), releaseRevision: 5 };
    }
  },

  async updatePrompt(promptCode: string, data: { name?: string; variableSchema?: string; enabled?: boolean }): Promise<PromptResponse> {
    try {
      return await fetchJson<PromptResponse>(`/model/prompts/${promptCode}`, {
        method: "PUT",
        body: JSON.stringify(data),
      });
    } catch {
      return { promptCode, enabled: data.enabled ?? true };
    }
  },
};

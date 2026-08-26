// Standard Backend Result Wrapper
export interface Result<T> {
  code: number;
  message: string;
  data: T;
  success?: boolean;
}

// -------------------------------------------------------------
// 1. Chat & Conversation Types (ChatController, ConversationController)
// -------------------------------------------------------------
export interface ChatConversationVO {
  conversationId: string;
  title: string;
  status?: string;
  lastMessageTime?: string;
  createdTime?: string;
  updatedTime?: string;
}

export interface ConversationPageVO {
  records: ChatConversationVO[];
  total: number;
  current: number;
  size: number;
  pages: number;
}

export interface ChatMessageVO {
  messageId: string;
  sequence: number;
  role: 'user' | 'assistant' | 'system' | 'USER' | 'ASSISTANT' | 'SYSTEM' | string;
  status: string;
  content: string;
  thinkingContent?: string;
  referencesJson?: string;
  generationId?: string;
  toolOperationsJson?: string;
  operations?: ChatToolOperation[];
  citations?: ChatCitationSummaryVO[];
  connectionState?: 'STREAMING' | 'RECONNECTING' | 'BACKGROUND_RUNNING';
  createdTime?: string;
  updatedTime?: string;
}

export interface ConversationHistoryPageVO {
  records: ChatMessageVO[];
  hasMore: boolean;
  nextBeforeSequence?: number;
}

export type ChatToolOperationStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface ChatToolOperation {
  opId: string;
  processId: string;
  sequence: number;
  name: string;
  status: ChatToolOperationStatus;
}

export interface ChatCitationSummaryVO {
  citationId: number;
}

export interface ChatCitationDetailVO {
  citationId: number;
  title: string;
  chunkOrder?: number;
  content: string;
  documentPath: string;
  sourceUrl?: string;
}

export type ChatStreamEventType = 'META' | 'SNAPSHOT' | 'CITATIONS' | 'ANSWER_DELTA' | 'TOKEN' | 'COMPLETE' | 'ERROR' | 'CANCELLED' | 'TEXT';

export interface ChatStreamEvent {
  type: ChatStreamEventType;
  content?: string;
  conversationId?: string;
  traceId?: string;
  generationId?: string;
  messageId?: string;
  errorCode?: string | number;
  errorMessage?: string;
  eventVersion?: number | string;
  operations?: ChatToolOperation[];
  citations?: ChatCitationSummaryVO[];
}

export interface ChatStreamRequest {
  conversationId?: string | null;
  content: string;
  knowledgeBaseIds?: (number | string)[];
}

export type FileType =
  | 'PDF'
  | 'WORD'
  | 'EXCEL'
  | 'PPT'
  | 'MARKDOWN'
  | 'TEXT'
  | 'UNKNOWN';

export type DocumentStatus =
  | 'UPLOADED'
  | 'QUEUED'
  | 'PARSING'
  | 'PARSED'
  | 'CHUNKING'
  | 'CHUNKED'
  | 'INDEXING'
  | 'INDEXED'
  | 'FAILED';

export type ChunkStatus =
  | 'PENDING_INDEX'
  | 'INDEXED'
  | 'SKIP_INDEX'
  | 'FAILED';

// -------------------------------------------------------------
// 2. Knowledge Base & Document Types (KnowledgeBaseController, DocumentController)
// -------------------------------------------------------------
export interface KnowledgeBaseStatisticsVO {
  totalCount: number;
  pendingCount: number;
  processingCount: number;
  indexedCount: number;
  failedCount: number;
}

export interface KnowledgeBaseSummaryVO {
  knowledgeBaseId: number | string;
  name: string;
  description?: string;
  isDefault: number;
  statistics?: KnowledgeBaseStatisticsVO;
  createTime?: string;
  updatedTime?: string;
}

export interface KnowledgeBaseDetailVO {
  knowledgeBaseId: number | string;
  name: string;
  description?: string;
  isDefault: number;
  createTime?: string;
  updatedTime?: string;
}

export interface CreateKnowledgeBaseDTO {
  name: string;
  description?: string;
}

export interface UpdateKnowledgeBaseDTO {
  name: string;
  description?: string;
}
export interface DocumentSummaryVO {
  documentId: string | number;
  title: string;
  originalFileName: string;
  fileType: FileType;
  status: DocumentStatus;
  createBy?: string;
  updatedTime?: string;
  // Optional / backward-compatible aliases
  fileName?: string;
  fileSize?: number;
  contentType?: string;
  chunkCount?: number;
  createdTime?: string;
}

export interface DocumentDetailVO {
  documentId: string | number;
  title: string;
  description?: string;
  originalFileName: string;
  fileType: FileType;
  fileSize?: number;
  originalFileUrl?: string;
  parsedFileUrl?: string;
  status: DocumentStatus;
  processConfigJson?: string;
}

export interface DocumentChunkStatisticsVO {
  total: number;
  indexed: number;
  failed: number;
  skipped: number;
  pending: number;
}

export interface DocumentOverviewVO {
  documentId: string | number;
  title: string;
  description?: string;
  originalFileName: string;
  fileType: FileType;
  fileSize?: number;
  status: DocumentStatus;
  sourceType?: string;
  sourceUrl?: string;
  processConfigJson?: string;
  createTime?: string;
  updateTime?: string;
  chunkStatistics?: DocumentChunkStatisticsVO;
  // Backward-compatible aliases
  fileName?: string;
  totalChunks?: number;
  indexedChunks?: number;
  failedChunks?: number;
  processConfigSnapshotJson?: string;
  createdTime?: string;
}

export interface DocumentChunkVO {
  chunkId: string | number;
  documentId: string | number;
  chunkOrder?: number;
  text?: string;
  status?: ChunkStatus | string;
  // Backward-compatible aliases
  content?: string;
  chunkIndex?: number;
  tokenCount?: number;
  vectorIndexed?: boolean;
}

export interface SplitConfigRequest {
  chunkSize?: number;
  chunkOverlap?: number;
}

export interface ParseConfigRequest {
  parserType?: string;
  options?: Record<string, any>;
}

export interface IndexConfigRequest {
  embedModel?: string;
}

export interface ProcessDocumentRequest {
  splitConfig?: SplitConfigRequest;
  parseConfig?: ParseConfigRequest;
  indexConfig?: IndexConfigRequest;
}

export interface UploadDocumentResponse {
  documentId: string | number;
  processId: string;
  status: DocumentStatus;
}

export interface DocumentProcessStatusVO {
  documentId: string | number;
  processId?: string;
  status: DocumentStatus;
  messageStatus?: string;
  consumedTimes?: number;
  failureStage?: string;
  failureReason?: string;
}

export interface DocumentTaskVO {
  outboxId: number;
  documentId: number;
  parentOutboxId?: number;
  operationId?: string;
  taskType: string;
  publishStatus?: string;
  taskStatus?: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | string;
  status?: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | string;
  publishRetryCount?: number;
  consumeRetryCount?: number;
  retryCount?: number;
  maxRetries?: number;
  failureReason?: string;
  errorMessage?: string;
  completedTime?: string;
  createdTime?: string;
  updatedTime?: string;
}

export interface DocumentIndexCleanupResult {
  documentId: number;
  vectorCleanedCount: number;
  keywordCleanedCount: number;
  navigationCleanedCount: number;
}

export interface PageVO<T> {
  total: number;
  records: T[];
  pageNum?: number;
  pageSize?: number;
  current?: number;
  size?: number;
  pages?: number;
}

// -------------------------------------------------------------
// 3. Model Gateway & Route Types (ModelConfigController, ModelRouteController, Governance, Registry)
// -------------------------------------------------------------
export interface ModelProviderCatalogResponse {
  provider?: string;
  providerCode?: string;
  displayName?: string;
  providerName?: string;
  logoUrl?: string;
  defaultBaseUrl?: string;
  supportedTypes?: string[];
  defaultEndpointPath?: string;
  defaultGovernanceDescription?: string;
  recommendedModels?: Record<string, string[]> | string[];
}

export interface ModelConfigResponse {
  configId: number;
  configKey?: string;
  configName?: string;
  provider?: string;
  providerCode?: string;
  baseUrl: string;
  endpointPath?: string;
  apiKeyMask?: string;
  apiKeyMasked?: string;
  modelType: string;
  modelName: string;
  enabled?: boolean;
  status?: 'ACTIVE' | 'INACTIVE' | string;
  timeoutMs?: number;
  maxRetries?: number;
  version?: number;
  extraConfig?: string;
  remark?: string;
  createTime?: string;
  createdTime?: string;
  updateTime?: string;
  updatedTime?: string;
}

export interface ModelConfigCreateRequest {
  configKey: string;
  modelType: string;
  provider: string;
  baseUrl: string;
  endpointPath?: string;
  apiKey?: string;
  modelName: string;
  timeoutMs?: number;
  maxRetries?: number;
  extraConfig?: string;
  remark?: string;
}

export interface ModelConfigUpdateRequest {
  configKey?: string;
  modelType?: string;
  provider?: string;
  baseUrl?: string;
  endpointPath?: string;
  apiKey?: string;
  modelName?: string;
  enabled?: boolean;
  timeoutMs?: number;
  maxRetries?: number;
  extraConfig?: string;
  remark?: string;
}

export interface ModelGovernanceConfigRequest {
  bindingMode?: 'GLOBAL' | 'ROUTE' | 'CONFIG';
  routeKey?: string;
  enabled?: boolean;
  retryEnabled?: boolean;
  maxAttempts?: number;
  retryWaitMs?: number;
  circuitEnabled?: boolean;
  failureRateThreshold?: number;
  slowCallRateThreshold?: number;
  slowCallDurationMs?: number;
  minimumNumberOfCalls?: number;
  slidingWindowSize?: number;
  waitDurationInOpenStateMs?: number;
  rateLimitEnabled?: boolean;
  limitForPeriod?: number;
  limitRefreshPeriodMs?: number;
  timeoutDurationMs?: number;
  bulkheadEnabled?: boolean;
  timeLimiterEnabled?: boolean;
  timeLimiterTimeoutMs?: number;
  streamFirstChunkTimeoutMs?: number;
  streamMaxDurationMs?: number;
  maxConcurrentCalls?: number;
  maxWaitDurationMs?: number;
}

export interface ModelRouteResponse {
  routeId: number;
  routeKey: string;
  routeName: string;
  modelType: string;
  candidateCount?: number;
  description?: string;
  createdTime: string;
  updatedTime: string;
}

export interface ModelRouteConfigResponse {
  routeConfigId: number;
  routeId: number;
  configId: number;
  configName?: string;
  weight: number;
  priority: number;
  status: 'ACTIVE' | 'DISABLED';
}

export interface ModelGovernanceConfigResponse {
  governanceId: number;
  bindingMode?: 'GLOBAL' | 'ROUTE' | 'CONFIG' | string;
  configId?: number;
  routeKey?: string;
  enabled?: boolean;
  retryEnabled?: boolean;
  maxAttempts?: number;
  retryWaitMs?: number;
  circuitEnabled?: boolean;
  failureRateThreshold?: number;
  slowCallRateThreshold?: number;
  slowCallDurationMs?: number;
  minimumNumberOfCalls?: number;
  slidingWindowSize?: number;
  waitDurationInOpenStateMs?: number;
  rateLimitEnabled?: boolean;
  limitForPeriod?: number;
  limitRefreshPeriodMs?: number;
  timeoutDurationMs?: number;
  bulkheadEnabled?: boolean;
  timeLimiterEnabled?: boolean;
  timeLimiterTimeoutMs?: number;
  streamFirstChunkTimeoutMs?: number;
  streamMaxDurationMs?: number;
  maxConcurrentCalls?: number;
  maxWaitDurationMs?: number;
  // Aliases for compatibility
  targetType?: 'GLOBAL' | 'ROUTE' | 'CONFIG' | string;
  targetKey?: string;
  maxTokensPerReq?: number;
  rateLimitQps?: number;
  timeoutMs?: number;
  fallbackEnabled?: boolean;
  fallbackRouteKey?: string;
}

export interface ModelConnectionTestResponse {
  success: boolean;
  latencyMs: number;
  errorMessage?: string;
  testedAt: string;
}

export interface ModelRegistrySnapshotResponse {
  versionNo: number;
  configCount: number;
  routeCount: number;
  routeConfigCount: number;
  governanceConfigCount: number;
}

export interface ModelChatRequest {
  routeKey: string;
  messages: Array<{ role: string; content: string }>;
  options?: Record<string, any>;
}

// -------------------------------------------------------------
// 4. Prompt Engineering Types (PromptController)
// -------------------------------------------------------------
export interface PromptResponse {
  promptId?: number;
  promptCode: string;
  name?: string;
  promptName?: string;
  description?: string;
  variableSchema?: string;
  variableContractJson?: string;
  enabled: boolean;
  currentReleaseId?: number;
  currentReleaseRevision?: number;
  activeStableVersionId?: number;
  activeCanaryVersionId?: number;
  canaryPercentage?: number;
  latestContent?: string;
  versions?: PromptVersionVO[];
  releases?: PromptReleaseVO[];
}

export interface PromptVersionVO {
  versionId: number;
  versionNo?: number;
  promptCode?: string;
  content: string;
  createdBy?: string;
  createdAt?: string;
  createdTime?: string;
  remark?: string;
}

export interface PromptReleaseVO {
  releaseId: number;
  promptCode?: string;
  stableVersionId: number;
  canaryVersionId?: number;
  canaryRule?: string;
  canaryPercentage?: number;
  releaseRevision: number;
  releasedBy?: string;
  operator?: string;
  releasedAt?: string;
  releasedTime?: string;
  rollbackFromReleaseId?: number;
  remark?: string;
}

export interface PromptReleaseResponse {
  versionId: number;
  releaseId: number;
  releaseRevision: number;
}

// -------------------------------------------------------------
// 5. Auth & Identity Types (AuthController, AccountSecurityController)
// -------------------------------------------------------------
export interface CsrfTokenVO {
  token: string;
}

export interface EmailChallengeVO {
  challengeId: number;
  expiresTime: string;
}

export interface LoginSessionVO {
  userId: string;
  tenantId: string;
  role?: 'ADMIN' | 'USER' | string;
  permissions?: string[];
}

export interface OAuthAuthorizationVO {
  authorizationUrl: string;
}

export interface OAuthCallbackVO {
  success: boolean;
  action: 'LOGIN' | 'REGISTER' | 'BIND' | string;
  userId?: string;
  tenantId?: string;
  provider?: string;
  error?: string;
}

export interface EmailCodeSendDTO {
  email: string;
  purpose: 'REGISTER' | 'EMAIL_LOGIN' | 'PASSWORD_RESET' | 'CHANGE_EMAIL_OLD' | 'CHANGE_EMAIL_NEW' | 'LOGIN';
}

export interface EmailCodeLoginDTO {
  email: string;
  challengeId: number;
  verificationCode: string;
}

export interface AccountPasswordLoginDTO {
  accountName: string;
  password: string;
}

export interface EmailPasswordLoginDTO {
  email: string;
  password: string;
}

export interface RegisterAccountDTO {
  accountName: string;
  email: string;
  challengeId: number;
  verificationCode: string;
}

export interface PasswordResetDTO {
  email: string;
  challengeId: number;
  verificationCode: string;
  newPassword: string;
}

import React, { useState, useEffect } from "react";
import {
  X,
  RefreshCw,
  Trash2,
  Activity,
  Layers,
  Clock,
  RotateCcw,
  Copy,
  Check,
  FileCode,
  AlertTriangle,
  Play,
  Settings2,
  Search,
} from "lucide-react";
import { documentApi, DEFAULT_KNOWLEDGE_BASE_ID } from "../../../lib/api";
import {
  DocumentOverviewVO,
  DocumentChunkVO,
  DocumentTaskVO,
  DocumentProcessStatusVO,
  ProcessDocumentRequest,
} from "../../../types";
import { FeishuPill, FeishuEmptyState } from "../../../components/ui/feishu-table";

interface DocumentDrawerProps {
  documentId: string | number | null;
  knowledgeBaseId?: number | string;
  onClose: () => void;
  onReloadList: () => void;
}

export const DocumentDrawer: React.FC<DocumentDrawerProps> = ({
                                                                documentId,
                                                                knowledgeBaseId = DEFAULT_KNOWLEDGE_BASE_ID,
                                                                onClose,
                                                                onReloadList,
                                                              }) => {
  const [activeTab, setActiveTab] = useState<"overview" | "chunks" | "process" | "task">("overview");
  const [overview, setOverview] = useState<DocumentOverviewVO | null>(null);
  const [processStatus, setProcessStatus] = useState<DocumentProcessStatusVO | null>(null);
  const [chunks, setChunks] = useState<DocumentChunkVO[]>([]);
  const [task, setTask] = useState<DocumentTaskVO | null>(null);
  const [searchOutboxId, setSearchOutboxId] = useState<string>("");
  const [isTaskLoading, setIsTaskLoading] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [copiedChunkId, setCopiedChunkId] = useState<string | number | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // 处理流水线参数
  const [chunkSize, setChunkSize] = useState<number>(500);
  const [chunkOverlap, setChunkOverlap] = useState<number>(50);

  const isProcessingStatus = (s?: string) =>
    ["UPLOADED", "QUEUED", "PARSING", "PARSED", "CHUNKING", "CHUNKED", "INDEXING"].includes(s || "");

  useEffect(() => {
    if (documentId) {
      loadAllDetails(documentId);
    }
  }, [documentId, knowledgeBaseId]);

  const loadAllDetails = async (id: string | number) => {
    setIsLoading(true);
    try {
      const [ov, chPage, ps] = await Promise.all([
        documentApi.getOverview(id, knowledgeBaseId),
        documentApi.listChunks(id, 1, 50, knowledgeBaseId),
        documentApi.getProcessStatus(id, knowledgeBaseId),
      ]);
      setOverview(ov);
      setChunks(chPage.records || []);
      setProcessStatus(ps);
    } catch (err) {
      console.error("Failed to load document drawer data", err);
    } finally {
      setIsLoading(false);
    }
  };

  // 抽屉打开且文档处于处理中时，每 2 秒静默更新概览、切片与处理状态，完成时通知列表刷新
  useEffect(() => {
    const curStatus = processStatus?.status || overview?.status;
    if (!documentId || !isProcessingStatus(curStatus)) return;

    const timer = setInterval(async () => {
      try {
        const [ov, chPage, ps] = await Promise.all([
          documentApi.getOverview(documentId, knowledgeBaseId),
          documentApi.listChunks(documentId, 1, 50, knowledgeBaseId),
          documentApi.getProcessStatus(documentId, knowledgeBaseId),
        ]);
        setOverview(ov);
        setChunks(chPage.records || []);
        setProcessStatus(ps);
        if (ps?.status === "INDEXED" || ps?.status === "FAILED") {
          onReloadList();
        }
      } catch (err) {
        console.warn("Silent polling drawer data error:", err);
      }
    }, 2000);

    return () => clearInterval(timer);
  }, [documentId, knowledgeBaseId, processStatus?.status, overview?.status]);

  if (!documentId) return null;

  const showNotification = (msg: string) => {
    setSuccessMessage(msg);
    setTimeout(() => setSuccessMessage(null), 3500);
  };

  const handleCopyChunk = (chunkId: string | number, content: string) => {
    navigator.clipboard.writeText(content);
    setCopiedChunkId(chunkId);
    setTimeout(() => setCopiedChunkId(null), 2000);
  };

  const handleRetryDoc = async () => {
    if (!documentId) return;
    setActionLoading(true);
    try {
      const res = await documentApi.retryDocument(documentId, knowledgeBaseId);
      showNotification(`文档已提交重新处理！批次号: ${res.processId || "已生成"}`);
      await loadAllDetails(documentId);
      onReloadList();
    } catch (err: any) {
      alert(`重试失败: ${err?.message || "网络或服务端异常"}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleProcessDoc = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!documentId) return;
    setActionLoading(true);
    try {
      const req: ProcessDocumentRequest = {
        splitConfig: {
          chunkSize,
          chunkOverlap,
        },
      };
      const res = await documentApi.processDocument(documentId, req, knowledgeBaseId);
      showNotification(`流水线处理任务已提交！批次号: ${res.processId || "处理中"}`);
      setActiveTab("overview");
      await loadAllDetails(documentId);
      onReloadList();
    } catch (err: any) {
      alert(`提交流水线处理失败: ${err?.message || "请检查后端配置"}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleCleanupIndex = async () => {
    if (!documentId) return;
    if (!confirm("确定要清理当前文档在向量库与搜索引擎中的全部索引吗？")) return;
    setActionLoading(true);
    try {
      const res = await documentApi.deleteDocumentIndex(documentId);
      showNotification(`索引清理完成！向量清理: ${res.vectorCleanedCount} 条，关键词清理: ${res.keywordCleanedCount} 条`);
      await loadAllDetails(documentId);
      onReloadList();
    } catch (err: any) {
      alert(`清理索引失败: ${err?.message || "服务端异常"}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteDoc = async () => {
    if (!documentId) return;
    if (!confirm("确定要永久删除该文档及相关切片吗？此操作不可逆！")) return;
    setActionLoading(true);
    try {
      await documentApi.deleteDocument(documentId, knowledgeBaseId);
      onReloadList();
      onClose();
    } catch (err: any) {
      alert(`删除失败: ${err?.message || "服务端异常"}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleSearchTask = async (outboxIdToSearch?: number) => {
    const id = outboxIdToSearch ?? parseInt(searchOutboxId, 10);
    if (!id || isNaN(id)) {
      alert("请输入有效的 Outbox 任务 ID (数字)");
      return;
    }
    setIsTaskLoading(true);
    try {
      const res = await documentApi.getTask(id);
      setTask(res);
      if (!res) {
        alert(`未找到 Outbox 任务 #${id}`);
      }
    } catch (err: any) {
      alert(`查询 Outbox 任务失败: ${err?.message || "服务异常"}`);
    } finally {
      setIsTaskLoading(false);
    }
  };

  const handleRetryTask = async (outboxId: number) => {
    setActionLoading(true);
    try {
      const newTk = await documentApi.retryTask(outboxId);
      setTask(newTk);
      showNotification(`Outbox 任务 #${outboxId} 补偿重试命令已提交！`);
    } catch (err: any) {
      alert(`重试任务失败: ${err?.message || "服务异常"}`);
    } finally {
      setActionLoading(false);
    }
  };

  const currentStatus = processStatus?.status || overview?.status;

  return (
    <div className="fixed inset-0 z-50 overflow-hidden flex justify-end">
      {/* 遮罩背景 */}
      <div
        className="fixed inset-0 bg-[#1F2329]/30 backdrop-blur-[1px] transition-opacity animate-in fade-in"
        onClick={onClose}
      />

      {/* 580px 飞书标准右侧滑出抽屉 */}
      <div className="relative w-full max-w-[580px] bg-white h-full shadow-[-8px_0_32px_rgba(31,35,41,0.12)] flex flex-col z-10 animate-in slide-in-from-right duration-200">
        {/* 1. 抽屉顶部头部 */}
        <div className="p-5 border-b border-[#DEE0E3] bg-white flex flex-col gap-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 min-w-0">
              <span className="text-[11px] font-mono text-[#8F959E] bg-[#F2F3F5] px-1.5 py-0.5 rounded">
                #{documentId}
              </span>
              <h2 className="text-[16px] font-semibold text-[#1F2329] truncate" title={overview?.title || overview?.originalFileName || overview?.fileName}>
                {overview?.title || overview?.originalFileName || overview?.fileName || `文档 #${documentId}`}
              </h2>
            </div>
            <button
              onClick={onClose}
              className="w-7 h-7 rounded-[6px] hover:bg-[#EFF0F1] flex items-center justify-center text-[#646A75] hover:text-[#1F2329] transition-colors cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* 状态与元信息药丸栏 */}
          <div className="flex items-center gap-3 text-xs text-[#646A75]">
            <span className="flex items-center gap-1.5">
              <span>状态:</span>
              {currentStatus === "INDEXED" && <FeishuPill variant="green">已完成向量索引</FeishuPill>}
              {currentStatus === "INDEXING" && <FeishuPill variant="blue">索引写入中</FeishuPill>}
              {currentStatus === "PARSING" && <FeishuPill variant="blue">解析中</FeishuPill>}
              {currentStatus === "CHUNKING" && <FeishuPill variant="purple">文本切分中</FeishuPill>}
              {currentStatus === "CHUNKED" && <FeishuPill variant="purple">已切片待向量化</FeishuPill>}
              {currentStatus === "QUEUED" && <FeishuPill variant="orange">排队等待中</FeishuPill>}
              {currentStatus === "UPLOADED" && <FeishuPill variant="gray">已就绪待处理</FeishuPill>}
              {currentStatus === "FAILED" && <FeishuPill variant="red">处理异常</FeishuPill>}
              {!currentStatus && <FeishuPill variant="gray">未知</FeishuPill>}
            </span>

            <span>·</span>
            <span>总切片: <strong className="text-[#1F2329] font-mono">{overview?.chunkStatistics?.total ?? overview?.totalChunks ?? chunks.length}</strong></span>
            <span>·</span>
            <span>知识库: <strong className="text-[#3370FF] font-mono">#{knowledgeBaseId}</strong></span>
          </div>
        </div>

        {/* 提示条 */}
        {successMessage && (
          <div className="px-5 py-2.5 bg-[#E6F7ED] border-b border-[#98E4B5] text-xs text-[#00B42A] flex items-center gap-2 font-medium">
            <Check className="w-4 h-4 text-[#00B42A]" />
            <span>{successMessage}</span>
          </div>
        )}

        {/* 2. 飞书 1:1 内部 Tab 栏 */}
        <div className="flex items-center gap-6 px-5 border-b border-[#DEE0E3] bg-white select-none">
          <button
            type="button"
            onClick={() => setActiveTab("overview")}
            className={`py-3 text-[13px] font-medium border-b-2 transition-all cursor-pointer flex items-center gap-1.5 ${
              activeTab === "overview"
                ? "border-[#3370FF] text-[#1F2329]"
                : "border-transparent text-[#646A75] hover:text-[#1F2329]"
            }`}
          >
            <Activity className="w-3.5 h-3.5 text-[#8D55ED]" />
            <span>诊断概览 (Overview)</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab("chunks")}
            className={`py-3 text-[13px] font-medium border-b-2 transition-all cursor-pointer flex items-center gap-1.5 ${
              activeTab === "chunks"
                ? "border-[#3370FF] text-[#1F2329]"
                : "border-transparent text-[#646A75] hover:text-[#1F2329]"
            }`}
          >
            <Layers className="w-3.5 h-3.5 text-[#00B42A]" />
            <span>切片明细 ({chunks.length})</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab("process")}
            className={`py-3 text-[13px] font-medium border-b-2 transition-all cursor-pointer flex items-center gap-1.5 ${
              activeTab === "process"
                ? "border-[#3370FF] text-[#1F2329]"
                : "border-transparent text-[#646A75] hover:text-[#1F2329]"
            }`}
          >
            <Settings2 className="w-3.5 h-3.5 text-[#3370FF]" />
            <span>流水线配置 (Process)</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab("task")}
            className={`py-3 text-[13px] font-medium border-b-2 transition-all cursor-pointer flex items-center gap-1.5 ${
              activeTab === "task"
                ? "border-[#3370FF] text-[#1F2329]"
                : "border-transparent text-[#646A75] hover:text-[#1F2329]"
            }`}
          >
            <Clock className="w-3.5 h-3.5 text-[#FF7D00]" />
            <span>Outbox 事务流水线</span>
          </button>
        </div>

        {/* 3. 抽屉滚动内容区域 */}
        <div className="flex-1 overflow-y-auto p-5 space-y-4 custom-scrollbar bg-[#F8F9FA]/40">
          {isLoading ? (
            <div className="py-16 text-center text-xs text-[#8F959E]">
              正在加载文档深度诊断快照...
            </div>
          ) : (
            <>
              {/* Tab 1: 诊断概览 */}
              {activeTab === "overview" && (
                <div className="space-y-4">
                  {/* 状态看板与实时流水线指标 */}
                  {processStatus?.failureReason && (
                    <div className="p-3.5 bg-[#FFF2F0] border border-[#FFCCC7] rounded-[8px] space-y-1.5">
                      <div className="flex items-center gap-1.5 text-xs text-[#F53F3F] font-semibold">
                        <AlertTriangle className="w-4 h-4" />
                        <span>流水线处理异常 (阶段: {processStatus.failureStage || "未知"})</span>
                      </div>
                      <p className="text-[12px] text-[#F53F3F] whitespace-pre-wrap break-all">
                        {processStatus.failureReason}
                      </p>
                      <button
                        type="button"
                        onClick={handleRetryDoc}
                        disabled={actionLoading}
                        className="mt-2 inline-flex h-7 items-center gap-1 rounded bg-[#F53F3F] text-white text-[12px] px-3 font-medium hover:bg-[#D9363E] transition-colors"
                      >
                        <RefreshCw className="w-3 h-3" />
                        一键重试处理
                      </button>
                    </div>
                  )}

                  {/* 四色指标卡 */}
                  <div className="grid grid-cols-2 gap-3">
                    <div className="p-3.5 bg-white border border-[#DEE0E3] rounded-[8px] shadow-2xs">
                      <span className="text-[12px] text-[#646A75]">总切片数 (Chunks)</span>
                      <p className="text-[20px] font-bold text-[#1F2329] mt-1 font-mono">
                        {overview?.chunkStatistics?.total ?? overview?.totalChunks ?? chunks.length}
                      </p>
                    </div>

                    <div className="p-3.5 bg-[#E6F7ED] border border-[#B3E8C6] rounded-[8px]">
                      <span className="text-[12px] text-[#00B42A]">向量索引已就绪</span>
                      <p className="text-[20px] font-bold text-[#00B42A] mt-1 font-mono">
                        {overview?.chunkStatistics?.indexed ?? overview?.indexedChunks ?? 0}
                      </p>
                    </div>

                    <div className="p-3.5 bg-[#FFECEC] border border-[#FFB4B4] rounded-[8px]">
                      <span className="text-[12px] text-[#F53F3F]">异常/失败切片</span>
                      <p className="text-[20px] font-bold text-[#F53F3F] mt-1 font-mono">
                        {overview?.chunkStatistics?.failed ?? overview?.failedChunks ?? 0}
                      </p>
                    </div>

                    <div className="p-3.5 bg-[#E8F3FF] border border-[#B3D4FF] rounded-[8px]">
                      <span className="text-[12px] text-[#3370FF]">处理批次号 (Process ID)</span>
                      <p className="text-[12px] font-semibold text-[#3370FF] mt-1.5 font-mono truncate" title={processStatus?.processId || "无"}>
                        {processStatus?.processId || "—"}
                      </p>
                    </div>
                  </div>

                  {/* 核心属性元数据表 */}
                  <div className="bg-white border border-[#DEE0E3] rounded-[8px] p-4 space-y-2.5 text-[13px]">
                    <h4 className="text-[13px] font-semibold text-[#1F2329] pb-2 border-b border-[#DEE0E3]">
                      文档元数据快照
                    </h4>
                    <div className="grid grid-cols-3 gap-2">
                      <span className="text-[#646A75]">文档唯一编号:</span>
                      <span className="col-span-2 font-mono text-[#1F2329]">#{documentId}</span>
                    </div>
                    <div className="grid grid-cols-3 gap-2">
                      <span className="text-[#646A75]">原始文件名称:</span>
                      <span className="col-span-2 text-[#1F2329] break-all">{overview?.title || overview?.originalFileName || overview?.fileName}</span>
                    </div>
                    <div className="grid grid-cols-3 gap-2">
                      <span className="text-[#646A75]">文件格式与大小:</span>
                      <span className="col-span-2 font-mono text-[#1F2329]">
                        {overview?.fileType || "—"} · {overview?.fileSize ? `${Math.round(overview.fileSize / 1024)} KB` : "—"}
                      </span>
                    </div>
                    <div className="grid grid-cols-3 gap-2">
                      <span className="text-[#646A75]">切片配置快照:</span>
                      <pre className="col-span-2 p-2 bg-[#F8F9FA] rounded border border-[#DEE0E3] text-[11px] font-mono text-[#3370FF] overflow-x-auto">
                        {overview?.processConfigJson || overview?.processConfigSnapshotJson || JSON.stringify({ chunkSize: 500, overlap: 50 }, null, 2)}
                      </pre>
                    </div>
                  </div>
                </div>
              )}

              {/* Tab 2: 切片明细 */}
              {activeTab === "chunks" && (
                <div className="space-y-3">
                  {chunks.length === 0 ? (
                    <div className="bg-white rounded-[8px] border border-[#DEE0E3]">
                      <FeishuEmptyState
                        title="暂无切片记录"
                        description="请在流水线配置中提交切片与向量化任务"
                        className="py-10"
                      />
                    </div>
                  ) : (
                    chunks.map((c) => (
                      <div
                        key={c.chunkId}
                        className="bg-white border border-[#DEE0E3] rounded-[8px] p-3.5 space-y-2 hover:border-[#3370FF]/50 transition-colors shadow-2xs"
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <span className="text-[12px] font-semibold font-mono text-[#3370FF] bg-[#E8F3FF] px-2 py-0.5 rounded">
                              Chunk #{c.chunkOrder ?? c.chunkIndex ?? 1}
                            </span>
                            <span className="text-[11px] text-[#8F959E] font-mono">
                              Tokens: {c.tokenCount ?? Math.round(((c.text || c.content || "").length) * 0.7)}
                            </span>
                          </div>

                          <div className="flex items-center gap-2">
                            {c.status === "INDEXED" || c.vectorIndexed ? (
                              <FeishuPill variant="green">已建立索引</FeishuPill>
                            ) : (
                              <FeishuPill variant="red">未索引</FeishuPill>
                            )}
                            <button
                              onClick={() => handleCopyChunk(c.chunkId, c.text || c.content || "")}
                              className="text-[#646A75] hover:text-[#1F2329] p-1 rounded hover:bg-[#EFF0F1] transition-colors cursor-pointer"
                              title="复制文本"
                            >
                              {copiedChunkId === c.chunkId ? (
                                <Check className="w-3.5 h-3.5 text-[#00B42A]" />
                              ) : (
                                <Copy className="w-3.5 h-3.5" />
                              )}
                            </button>
                          </div>
                        </div>

                        <p className="text-[12px] text-[#1F2329] leading-[20px] whitespace-pre-wrap">
                          {c.text || c.content}
                        </p>
                      </div>
                    ))
                  )}
                </div>
              )}

              {/* Tab 3: 流水线参数与触发处理 (POST /process) */}
              {activeTab === "process" && (
                <form onSubmit={handleProcessDoc} className="space-y-4">
                  <div className="bg-white border border-[#DEE0E3] rounded-[8px] p-4 space-y-3.5 shadow-2xs">
                    <div className="flex items-center justify-between pb-3 border-b border-[#DEE0E3]">
                      <div className="flex items-center gap-2">
                        <Settings2 className="w-4 h-4 text-[#3370FF]" />
                        <h4 className="text-[13px] font-semibold text-[#1F2329]">
                          手动配置切片流水线 (Process Pipeline)
                        </h4>
                      </div>
                      <FeishuPill variant="blue">POST /process</FeishuPill>
                    </div>

                    <div className="space-y-3 text-xs">
                      <div>
                        <label className="block text-[13px] font-medium text-[#1F2329] mb-1">
                          切片大小 (Chunk Size)
                        </label>
                        <input
                          type="number"
                          min={100}
                          max={3000}
                          step={50}
                          value={chunkSize}
                          onChange={(e) => setChunkSize(parseInt(e.target.value, 10))}
                          className="w-full h-8 px-2.5 rounded-[6px] border border-[#DEE0E3] text-xs font-mono"
                        />
                        <p className="text-[11px] text-[#8F959E] mt-1">推荐 300 ~ 800 字/块，过大可能导致检索精准度下降</p>
                      </div>

                      <div>
                        <label className="block text-[13px] font-medium text-[#1F2329] mb-1">
                          相邻块重叠字数 (Chunk Overlap)
                        </label>
                        <input
                          type="number"
                          min={0}
                          max={500}
                          step={10}
                          value={chunkOverlap}
                          onChange={(e) => setChunkOverlap(parseInt(e.target.value, 10))}
                          className="w-full h-8 px-2.5 rounded-[6px] border border-[#DEE0E3] text-xs font-mono"
                        />
                        <p className="text-[11px] text-[#8F959E] mt-1">建议为切片大小的 10% ~ 20%，保持上下文连贯性</p>
                      </div>
                    </div>

                    <button
                      type="submit"
                      disabled={actionLoading}
                      className="w-full h-8 rounded-[6px] bg-[#3370FF] hover:bg-[#2860E1] text-white text-[13px] font-medium inline-flex items-center justify-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
                    >
                      <Play className="w-3.5 h-3.5 fill-current" />
                      <span>立即提交流水线重新切片与索引</span>
                    </button>
                  </div>
                </form>
              )}

              {/* Tab 4: Outbox 异步事务流水线 (GET /api/document-tasks/{id}) */}
              {activeTab === "task" && (
                <div className="space-y-4">
                  {/* Outbox 搜索栏 */}
                  <div className="bg-white border border-[#DEE0E3] rounded-[8px] p-3.5 shadow-2xs space-y-2">
                    <label className="text-[12px] font-semibold text-[#1F2329] block">
                      通过 Outbox 任务 ID 探查异步任务
                    </label>
                    <div className="flex items-center gap-2">
                      <input
                        type="number"
                        placeholder="输入 Outbox ID (如 1, 2...)"
                        value={searchOutboxId}
                        onChange={(e) => setSearchOutboxId(e.target.value)}
                        className="flex-1 h-8 px-2.5 rounded-[6px] border border-[#DEE0E3] text-xs font-mono focus:border-[#3370FF] focus:outline-none"
                      />
                      <button
                        type="button"
                        onClick={() => handleSearchTask()}
                        disabled={isTaskLoading}
                        className="h-8 px-3.5 rounded-[6px] bg-[#3370FF] hover:bg-[#2860E1] text-white text-xs font-medium inline-flex items-center gap-1.5 transition-colors disabled:opacity-50 cursor-pointer"
                      >
                        <Search className="w-3.5 h-3.5" />
                        <span>查询</span>
                      </button>
                    </div>
                  </div>

                  {task ? (
                    <div className="bg-white border border-[#DEE0E3] rounded-[8px] p-4 space-y-3.5 shadow-2xs">
                      <div className="flex items-center justify-between pb-3 border-b border-[#DEE0E3]">
                        <div>
                          <div className="flex items-center gap-2">
                            <FileCode className="w-4 h-4 text-[#3370FF]" />
                            <h4 className="text-[13px] font-semibold text-[#1F2329]">
                              Outbox 任务 #{task.outboxId}
                            </h4>
                          </div>
                          <p className="text-[11px] text-[#8F959E] font-mono mt-0.5">
                            类型: {task.taskType} · 文档 ID: #{task.documentId}
                          </p>
                        </div>
                        <FeishuPill variant={task.taskStatus === "FAILED" || task.status === "FAILED" ? "red" : "blue"}>
                          {task.taskStatus || task.status || "UNKNOWN"}
                        </FeishuPill>
                      </div>

                      <div className="grid grid-cols-2 gap-2 text-xs">
                        <div className="p-2.5 bg-[#F8F9FA] rounded border border-[#DEE0E3]">
                          <span className="text-[#646A75]">重试计数</span>
                          <p className="text-[14px] font-bold text-[#1F2329] font-mono mt-0.5">
                            {task.consumeRetryCount ?? task.retryCount ?? 0} / {task.maxRetries ?? 3}
                          </p>
                        </div>
                        <div className="p-2.5 bg-[#F8F9FA] rounded border border-[#DEE0E3]">
                          <span className="text-[#646A75]">发布状态</span>
                          <p className="text-[14px] font-bold text-[#00B42A] font-mono mt-0.5">
                            {task.publishStatus || "PUBLISHED"}
                          </p>
                        </div>
                      </div>

                      {(task.failureReason || task.errorMessage) && (
                        <div className="p-3 bg-[#FFECEC] border border-[#FFB4B4] rounded-[6px] space-y-1">
                          <div className="flex items-center gap-1.5 text-xs text-[#F53F3F] font-semibold">
                            <AlertTriangle className="w-3.5 h-3.5" />
                            <span>异常原因:</span>
                          </div>
                          <pre className="text-[11px] font-mono text-[#F53F3F] whitespace-pre-wrap break-all">
                            {task.failureReason || task.errorMessage}
                          </pre>
                        </div>
                      )}

                      <button
                        onClick={() => handleRetryTask(task.outboxId)}
                        disabled={actionLoading}
                        className="w-full h-8 rounded-[6px] border border-[#D0D3D6] bg-white hover:bg-[#EFF0F1] text-[13px] font-medium text-[#1F2329] inline-flex items-center justify-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
                      >
                        <RotateCcw className="w-3.5 h-3.5 text-[#3370FF]" />
                        <span>手动触发 Outbox 事务补偿重试 (POST /retry)</span>
                      </button>
                    </div>
                  ) : (
                    <div className="p-8 text-center text-xs text-[#8F959E] bg-white rounded-[8px] border border-[#DEE0E3]">
                      请输入任务 ID 查询 Outbox 任务状态
                    </div>
                  )}
                </div>
              )}
            </>
          )}
        </div>

        {/* 4. 抽屉底部操作工具栏 */}
        <div className="p-4 border-t border-[#DEE0E3] bg-white flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <button
              onClick={handleRetryDoc}
              disabled={actionLoading}
              className="h-8 px-3 rounded-[6px] border border-[#D0D3D6] bg-white hover:bg-[#EFF0F1] text-[13px] font-medium text-[#1F2329] inline-flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 text-[#FF7D00] ${actionLoading ? "animate-spin" : ""}`} />
              <span>重试解析</span>
            </button>

            <button
              onClick={handleCleanupIndex}
              disabled={actionLoading}
              className="h-8 px-3 rounded-[6px] border border-[#D0D3D6] bg-white hover:bg-[#EFF0F1] text-[13px] font-medium text-[#F53F3F] inline-flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>清理索引</span>
            </button>
          </div>

          <button
            onClick={handleDeleteDoc}
            disabled={actionLoading}
            className="h-8 px-4 rounded-[6px] bg-[#FFECEC] hover:bg-[#FFD6D6] text-[#F53F3F] text-[13px] font-medium inline-flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
          >
            <span>删除文档</span>
          </button>
        </div>
      </div>
    </div>
  );
};

import React, { useState, useEffect, useMemo } from "react";
import { useParams, Link } from "react-router-dom";
import {
  ArrowLeft,
  Search,
  FileText,
  FileCode,
  Globe,
  FileSpreadsheet,
  Layers,
  X,
  Copy,
  Check,
} from "lucide-react";
import { documentApi, DEFAULT_KNOWLEDGE_BASE_ID } from "../../lib/api";
import { DocumentOverviewVO, DocumentChunkVO, DocumentProcessStatusVO, ProcessDocumentRequest } from "../../types";
import { FeishuInput, FeishuEmptyState, FeishuPill } from "../../components/ui/feishu-table";
import { RefreshCw, Play, Trash2, AlertTriangle } from "lucide-react";

export const DocumentDetailPage: React.FC = () => {
  const { knowledgeBaseId: kbIdParam, documentId } = useParams<{ knowledgeBaseId?: string; documentId: string }>();
  const knowledgeBaseId = kbIdParam || DEFAULT_KNOWLEDGE_BASE_ID;

  const [overview, setOverview] = useState<DocumentOverviewVO | null>(null);
  const [processStatus, setProcessStatus] = useState<DocumentProcessStatusVO | null>(null);
  const [chunks, setChunks] = useState<DocumentChunkVO[]>([]);
  const [selectedChunk, setSelectedChunk] = useState<DocumentChunkVO | null>(null);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [copied, setCopied] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);

  const isProcessingStatus = (s?: string) =>
    ["UPLOADED", "QUEUED", "PARSING", "PARSED", "CHUNKING", "CHUNKED", "INDEXING"].includes(s || "");

  useEffect(() => {
    if (documentId) {
      loadData(documentId);
    }
  }, [documentId, knowledgeBaseId]);

  // 处于处理中时，每 2 秒静默拉取最新切片与状态，完成后自动停止
  useEffect(() => {
    const curStatus = processStatus?.status || overview?.status;
    if (!documentId || !isProcessingStatus(curStatus)) return;

    const timer = setInterval(async () => {
      try {
        const [ov, chunkPage, ps] = await Promise.all([
          documentApi.getOverview(documentId, knowledgeBaseId),
          documentApi.listChunks(documentId, 1, 100, knowledgeBaseId),
          documentApi.getProcessStatus(documentId, knowledgeBaseId),
        ]);
        setOverview(ov);
        setChunks(chunkPage.records || []);
        setProcessStatus(ps);
      } catch (err) {
        console.warn("Silent polling detail error:", err);
      }
    }, 2000);

    return () => clearInterval(timer);
  }, [documentId, knowledgeBaseId, processStatus?.status, overview?.status]);

  const showFeedback = (msg: string) => {
    setFeedback(msg);
    setTimeout(() => setFeedback(null), 3500);
  };

  const loadData = async (id: string | number) => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const [ov, chunkPage, ps] = await Promise.all([
        documentApi.getOverview(id, knowledgeBaseId),
        documentApi.listChunks(id, 1, 100, knowledgeBaseId),
        documentApi.getProcessStatus(id, knowledgeBaseId),
      ]);
      setOverview(ov);
      setChunks(chunkPage.records || []);
      setProcessStatus(ps);
    } catch (err: any) {
      console.error("Failed to load document chunk details", err);
      setErrorMessage(err?.message || "加载文档切片详情失败，请检查网络或后端服务");
    } finally {
      setIsLoading(false);
    }
  };

  const handleRetry = async () => {
    if (!documentId) return;
    setActionLoading(true);
    try {
      const res = await documentApi.retryDocument(documentId, knowledgeBaseId);
      showFeedback(`文档重试命令已提交！批次号: ${res.processId || "已触发"}`);
      await loadData(documentId);
    } catch (err: any) {
      alert(`重试失败: ${err?.message || "服务端异常"}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleReprocess = async () => {
    if (!documentId) return;
    const chunkSizeInput = prompt("请输入重新切片大小 (Chunk Size，推荐 300~800):", "500");
    if (chunkSizeInput === null) return;
    const cSize = parseInt(chunkSizeInput, 10) || 500;
    setActionLoading(true);
    try {
      const res = await documentApi.processDocument(
        documentId,
        { splitConfig: { chunkSize: cSize, chunkOverlap: Math.round(cSize * 0.1) } },
        knowledgeBaseId
      );
      showFeedback(`重新切片与处理已提交！批次号: ${res.processId || "处理中"}`);
      await loadData(documentId);
    } catch (err: any) {
      alert(`提交流水线失败: ${err?.message || "服务端异常"}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleCleanupIndex = async () => {
    if (!documentId) return;
    if (!confirm("确定要清理当前文档全部向量与关键词索引吗？")) return;
    setActionLoading(true);
    try {
      const res = await documentApi.deleteDocumentIndex(documentId);
      showFeedback(`索引清理完成！向量清理: ${res.vectorCleanedCount} 条，关键词清理: ${res.keywordCleanedCount} 条`);
      await loadData(documentId);
    } catch (err: any) {
      alert(`清理索引失败: ${err?.message || "服务端异常"}`);
    } finally {
      setActionLoading(false);
    }
  };

  // 渲染飞书风格彩色格式图标
  const renderFormatIcon = (fileName?: string) => {
    const lower = (fileName || "").toLowerCase();
    if (lower.endsWith(".pdf")) {
      return (
        <div className="w-8 h-8 rounded-[8px] bg-[#FFF2F0] text-[#F53F3F] flex items-center justify-center shrink-0">
          <FileText className="w-4 h-4" />
        </div>
      );
    }
    if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
      return (
        <div className="w-8 h-8 rounded-[8px] bg-[#E8F3FF] text-[#3370FF] flex items-center justify-center shrink-0">
          <FileCode className="w-4 h-4" />
        </div>
      );
    }
    if (lower.includes("飞书") || lower.includes("语雀")) {
      return (
        <div className="w-8 h-8 rounded-[8px] bg-[#F2E9FE] text-[#8D55ED] flex items-center justify-center shrink-0">
          <Globe className="w-4 h-4" />
        </div>
      );
    }
    return (
      <div className="w-8 h-8 rounded-[8px] bg-[#F2F3F5] text-[#646A75] flex items-center justify-center shrink-0">
        <FileSpreadsheet className="w-4 h-4" />
      </div>
    );
  };

  // 过滤后的分块列表
  const filteredChunks = useMemo(() => {
    if (!searchKeyword.trim()) return chunks;
    const query = searchKeyword.toLowerCase();
    return chunks.filter(
      (c) =>
        (c.text || c.content || "").toLowerCase().includes(query) ||
        String(c.chunkOrder ?? c.chunkIndex ?? "").includes(query) ||
        String(c.chunkId).includes(query)
    );
  }, [chunks, searchKeyword]);

  // 统计总字符数与 Token 数
  const stats = useMemo(() => {
    const totalChars = chunks.reduce((acc, c) => acc + ((c.text || c.content)?.length || 0), 0);
    const totalTokens = chunks.reduce(
      (acc, c) => acc + (c.tokenCount ?? Math.round(((c.text || c.content)?.length || 0) * 0.7)),
      0
    );
    return { totalChars, totalTokens };
  }, [chunks]);

  const handleCopyContent = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="flex gap-4 items-start w-full select-none">
      {/* 左侧主区域 (包含导航栏、指标卡、搜索条、固定 3 列分块，随右侧抽屉展开整体平滑缩窄) */}
      <div className="flex-1 min-w-0 flex flex-col gap-4 transition-all duration-250 ease-[cubic-bezier(0.16,1,0.3,1)]">
        {/* 1. 顶部飞书标准导航栏 */}
        <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-4 shadow-2xs flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <Link
              to={`/knowledge-base/${knowledgeBaseId}`}
              className="h-8 px-2.5 rounded-[6px] border border-[#D0D3D6] bg-white hover:bg-[#EFF0F1] text-[13px] font-medium text-[#1F2329] inline-flex items-center gap-1.5 transition-colors cursor-pointer"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              <span>返回文档列表</span>
            </Link>

            <div className="h-4 w-[1px] bg-[#DEE0E3]" />

            <div className="flex items-center gap-2.5">
              {renderFormatIcon(overview?.originalFileName || overview?.fileName)}
              <div>
                <div className="flex items-center gap-2">
                  <h1 className="text-[16px] font-semibold text-[#1F2329] tracking-tight">
                    {overview?.title || overview?.originalFileName || overview?.fileName || `文档 #${documentId}`}
                  </h1>
                  <span className="text-[11px] font-mono text-[#8F959E] bg-[#F2F3F5] px-1.5 py-0.5 rounded">
                    #{documentId}
                  </span>
                  {processStatus?.status && (
                    <FeishuPill
                      variant={
                        processStatus.status === "INDEXED"
                          ? "green"
                          : processStatus.status === "FAILED"
                          ? "red"
                          : "blue"
                      }
                    >
                      {processStatus.status}
                    </FeishuPill>
                  )}
                </div>
                <p className="text-[12px] text-[#646A75] mt-0.5">
                  文档分块与文本切片工作区 · 结构化内容审阅
                </p>
              </div>
            </div>
          </div>

          {/* 顶部操作按钮组 */}
          <div className="flex items-center gap-2">
            <button
              onClick={() => documentId && loadData(documentId)}
              disabled={isLoading || actionLoading}
              className="h-8 px-3 rounded-[6px] border border-[#D0D3D6] bg-white hover:bg-[#EFF0F1] text-[13px] font-medium text-[#1F2329] inline-flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? "animate-spin text-[#3370FF]" : "text-[#646A75]"}`} />
              <span>刷新</span>
            </button>

            <button
              onClick={handleReprocess}
              disabled={actionLoading}
              className="h-8 px-3 rounded-[6px] bg-[#3370FF] hover:bg-[#2860E1] text-[13px] font-medium text-white inline-flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
            >
              <Play className="w-3.5 h-3.5 fill-current" />
              <span>重新切片处理</span>
            </button>

            {(processStatus?.status === "FAILED" || overview?.status === "FAILED") && (
              <button
                onClick={handleRetry}
                disabled={actionLoading}
                className="h-8 px-3 rounded-[6px] bg-[#FFF2F0] border border-[#FFCCC7] hover:bg-[#FFE8E6] text-[13px] font-medium text-[#F53F3F] inline-flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span>一键重试</span>
              </button>
            )}

            <button
              onClick={handleCleanupIndex}
              disabled={actionLoading}
              className="h-8 px-3 rounded-[6px] border border-[#D0D3D6] bg-white hover:bg-[#EFF0F1] text-[13px] font-medium text-[#F53F3F] inline-flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>清理索引</span>
            </button>
          </div>
        </div>

        {/* 提示条与异常提示 */}
        {errorMessage && (
          <div className="p-3.5 bg-[#FFF2F0] border border-[#FFCCC7] rounded-[8px] space-y-1">
            <div className="flex items-center gap-1.5 text-xs text-[#F53F3F] font-semibold">
              <AlertTriangle className="w-4 h-4" />
              <span>加载异常</span>
            </div>
            <p className="text-[12px] text-[#F53F3F]">{errorMessage}</p>
          </div>
        )}

        {feedback && (
          <div className="p-3 bg-[#E6F7ED] border border-[#98E4B5] rounded-[8px] text-xs text-[#00B42A] flex items-center gap-2 font-medium">
            <Check className="w-4 h-4 text-[#00B42A]" />
            <span>{feedback}</span>
          </div>
        )}

        {processStatus?.failureReason && (
          <div className="p-3.5 bg-[#FFF2F0] border border-[#FFCCC7] rounded-[8px] space-y-1.5">
            <div className="flex items-center gap-1.5 text-xs text-[#F53F3F] font-semibold">
              <AlertTriangle className="w-4 h-4" />
              <span>流水线处理异常 (阶段: {processStatus.failureStage || "未知"})</span>
            </div>
            <p className="text-[12px] text-[#F53F3F] whitespace-pre-wrap break-all">
              {processStatus.failureReason}
            </p>
          </div>
        )}

        {/* 2. 统计指标看板条 */}
        <div className="grid grid-cols-4 gap-4">
          <div className="p-3.5 bg-white border border-[#DEE0E3] rounded-[12px] shadow-2xs">
            <span className="text-[12px] text-[#646A75]">切片总数 (Chunks)</span>
            <p className="text-[20px] font-bold text-[#1F2329] mt-1 font-mono">
              {chunks.length} <span className="text-[12px] text-[#8F959E] font-normal">块</span>
            </p>
          </div>

          <div className="p-3.5 bg-white border border-[#DEE0E3] rounded-[12px] shadow-2xs">
            <span className="text-[12px] text-[#646A75]">文档总字符数</span>
            <p className="text-[20px] font-bold text-[#1F2329] mt-1 font-mono">
              {stats.totalChars.toLocaleString()} <span className="text-[12px] text-[#8F959E] font-normal">字</span>
            </p>
          </div>

          <div className="p-3.5 bg-white border border-[#DEE0E3] rounded-[12px] shadow-2xs">
            <span className="text-[12px] text-[#646A75]">Token 预估规模</span>
            <p className="text-[20px] font-bold text-[#3370FF] mt-1 font-mono">
              {stats.totalTokens.toLocaleString()} <span className="text-[12px] text-[#8F959E] font-normal">tokens</span>
            </p>
          </div>

          <div className="p-3.5 bg-white border border-[#DEE0E3] rounded-[12px] shadow-2xs">
            <span className="text-[12px] text-[#646A75]">平均分块长度</span>
            <p className="text-[20px] font-bold text-[#00B42A] mt-1 font-mono">
              {chunks.length > 0 ? Math.round(stats.totalChars / chunks.length) : 0} <span className="text-[12px] text-[#8F959E] font-normal">字/块</span>
            </p>
          </div>
        </div>

        {/* 3. 搜索与工具条 */}
        <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-3 shadow-2xs flex items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <Layers className="w-4 h-4 text-[#3370FF]" />
            <span className="text-[13px] font-medium text-[#1F2329]">
              分块内容流 (共 {filteredChunks.length} 项) · 点击卡片查看完整详情
            </span>
          </div>

          {/* 搜索框 (1:1 飞书 CRM 原生输入框，聚焦时 1px solid #3370FF 高亮边框) */}
          <FeishuInput
            value={searchKeyword}
            onChange={(val) => setSearchKeyword(val)}
            onClear={() => setSearchKeyword("")}
            placeholder="搜索切片正文、序号..."
            prefix={<Search className="w-3.5 h-3.5 text-[#8F959E]" />}
            containerClassName="w-60 h-[30px] rounded-[6px]"
          />
        </div>

        {/* 4. 分块卡片流 (高亮边框 1:1 对齐飞书 CRM 搜索框 1px solid #3370FF 风格，无模糊光晕) */}
        <div>
          {isLoading ? (
            <div className="py-20 text-center text-xs text-[#8F959E] bg-white rounded-[12px] border border-[#DEE0E3]">
              正在加载文档分块内容流...
            </div>
          ) : filteredChunks.length === 0 ? (
            <div className="bg-white rounded-[12px] border border-[#DEE0E3] shadow-2xs">
              <FeishuEmptyState
                title={searchKeyword ? "未匹配到相关切片" : "暂无相关记录"}
                description={
                  searchKeyword
                    ? `未找到包含 “${searchKeyword}” 的切片正文`
                    : "当前暂无切片数据"
                }
              />
            </div>
          ) : (
            <div className="grid grid-cols-3 gap-4 transition-all duration-250">
              {filteredChunks.map((chunk) => {
                const textContent = chunk.text || chunk.content || "";
                const charCount = textContent.length;
                const tokenCount = chunk.tokenCount ?? Math.round(charCount * 0.7);
                const orderNum = chunk.chunkOrder ?? chunk.chunkIndex ?? 1;
                const formattedIndex = String(orderNum).padStart(3, "0");
                const isSelected = selectedChunk?.chunkId === chunk.chunkId;

                return (
                  <div
                    key={chunk.chunkId}
                    onClick={() => setSelectedChunk(chunk)}
                    style={{
                      borderColor: isSelected ? "#3370FF" : undefined,
                    }}
                    className={`bg-white rounded-[12px] border shadow-2xs p-4 flex flex-col transition-colors duration-150 cursor-pointer group ${
                      isSelected
                        ? "!border-[#3370FF]"
                        : "border-[#DEE0E3] hover:border-[#3370FF]"
                    }`}
                  >
                    {/* 标头：Chunk #001 · 148 Tokens · 76 字符 */}
                    <div
                      className={`text-[13px] font-mono font-medium transition-colors truncate ${
                        isSelected
                          ? "text-[#3370FF]"
                          : "text-[#646A75] group-hover:text-[#3370FF]"
                      }`}
                    >
                      <span
                        className={`font-semibold ${
                          isSelected
                            ? "text-[#3370FF]"
                            : "text-[#1F2329] group-hover:text-[#3370FF]"
                        }`}
                      >
                        Chunk #{formattedIndex}
                      </span>
                      <span> · {tokenCount} Tokens · {charCount} 字符</span>
                    </div>

                    {/* 正文 */}
                    <div className="mt-3 text-[13px] text-[#1F2329] leading-[22px] whitespace-pre-wrap break-words font-sans line-clamp-6">
                      {textContent}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* 右侧全高度沉浸式分块详情抽屉面板 */}
      {selectedChunk && (
        <div className="w-[440px] lg:w-[480px] shrink-0 bg-white rounded-[12px] border border-[#DEE0E3] shadow-2xs p-5 flex flex-col gap-4 sticky top-4 h-[calc(100vh-96px)] animate-in slide-in-from-right-4 duration-250">
          {/* 抽屉头部：精炼标题、Tokens与字符、关闭按钮 */}
          <div className="flex items-center justify-between pb-3 border-b border-[#DEE0E3] shrink-0">
            <div className="flex items-center gap-2 min-w-0">
              <span className="text-[16px] font-bold font-mono text-[#1F2329]">
                Chunk #{String(selectedChunk.chunkOrder ?? selectedChunk.chunkIndex ?? 1).padStart(3, "0")}
              </span>
              <span className="text-[12px] font-mono text-[#646A75] bg-[#F2F3F5] px-2 py-0.5 rounded-[4px] truncate">
                {selectedChunk.tokenCount ?? Math.round(((selectedChunk.text || selectedChunk.content || "").length) * 0.7)} Tokens · {(selectedChunk.text || selectedChunk.content || "").length} 字符
              </span>
            </div>

            <button
              onClick={() => setSelectedChunk(null)}
              className="w-7 h-7 rounded-[6px] hover:bg-[#EFF0F1] flex items-center justify-center text-[#646A75] hover:text-[#1F2329] transition-colors cursor-pointer"
              title="关闭"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* 切片正文视窗 (占据全部剩余竖向空间，大文本舒适纵向滚动) */}
          <div className="bg-white rounded-[8px] border border-[#DEE0E3] flex-1 min-h-0 flex flex-col shadow-2xs overflow-hidden">
            <div className="flex items-center justify-between px-4 py-2.5 bg-white border-b border-[#DEE0E3] shrink-0">
              <span className="text-[12px] font-medium text-[#646A75]">切片完整正文</span>
              <button
                onClick={() => handleCopyContent(selectedChunk.text || selectedChunk.content || "")}
                className="h-6 px-2 rounded-[4px] border border-[#D0D3D6] bg-white hover:bg-[#EFF0F1] text-[11px] font-medium text-[#646A75] hover:text-[#1F2329] inline-flex items-center gap-1 transition-colors cursor-pointer"
              >
                {copied ? (
                  <>
                    <Check className="w-3 h-3 text-[#00B42A]" />
                    <span className="text-[#00B42A]">已复制</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3 h-3" />
                    <span>复制正文</span>
                  </>
                )}
              </button>
            </div>

            <div className="p-4 flex-1 overflow-y-auto custom-scrollbar text-[14px] text-[#1F2329] leading-[24px] whitespace-pre-wrap break-words font-sans selection:bg-[#E8F4FF]">
              {selectedChunk.text || selectedChunk.content}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

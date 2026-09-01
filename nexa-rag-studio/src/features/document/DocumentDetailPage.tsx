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
  RefreshCw,
  Play,
  Trash2,
  AlertTriangle,
  History,
  Activity,
  Plus,
  RotateCcw,
  CheckCircle2,
} from "lucide-react";
import { documentApi, DEFAULT_KNOWLEDGE_BASE_ID } from "@/lib/api.ts";
import {
  DocumentOverviewVO,
  DocumentChunkVO,
  DocumentProcessStatusVO,
  DocumentVersionVO,
  DocumentVersionOperationLogVO,
  DocumentVersionStatus,
  DocumentVersionOperationType,
} from "@/types";
import {
  FeishuInput,
  FeishuEmptyState,
  FeishuPill,
  FeishuDataTable,
  FeishuColumn,
  FeishuTag,
  FeishuActionLink,
  FEISHU_FONT_FAMILY,
} from "@/components/ui/feishu-table";
import { FeishuMarkdown } from "@/components/chat";
import { DocumentVersionUploadModal } from "./components/DocumentVersionUploadModal";
import { feishuDialog } from "../../components/ui/FeishuDialog";
import { feishuToast } from "../../components/ui/FeishuToast";
import { FeishuDocIcon } from "../../components/ui/FeishuDocIcon";

export const DocumentDetailPage: React.FC = () => {
  const { knowledgeBaseId: kbIdParam, documentId } = useParams<{ knowledgeBaseId?: string; documentId: string }>();
  const knowledgeBaseId = kbIdParam || DEFAULT_KNOWLEDGE_BASE_ID;

  // 活跃 Tab: "versions" (默认) | "auditLogs" | "chunks"
  const [activeTab, setActiveTab] = useState<"versions" | "auditLogs" | "chunks">("versions");

  // 基础文档状态
  const [overview, setOverview] = useState<DocumentOverviewVO | null>(null);
  const [processStatus, setProcessStatus] = useState<DocumentProcessStatusVO | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [copied, setCopied] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);

  // Tab 1: 版本历史状态
  const [versions, setVersions] = useState<DocumentVersionVO[]>([]);
  const [versionsTotal, setVersionsTotal] = useState(0);
  const [versionsPage, setVersionsPage] = useState(1);
  const [versionsPageSize, setVersionsPageSize] = useState(20);
  const [isVersionsLoading, setIsVersionsLoading] = useState(false);
  const [isUploadVersionModalOpen, setIsUploadVersionModalOpen] = useState(false);

  // Tab 2: 版本审计状态
  const [auditLogs, setAuditLogs] = useState<DocumentVersionOperationLogVO[]>([]);
  const [auditLogsTotal, setAuditLogsTotal] = useState(0);
  const [auditLogsPage, setAuditLogsPage] = useState(1);
  const [auditLogsPageSize, setAuditLogsPageSize] = useState(20);
  const [isAuditLogsLoading, setIsAuditLogsLoading] = useState(false);

  // Tab 3: 分块状态与抽屉
  const [chunks, setChunks] = useState<DocumentChunkVO[]>([]);
  const [selectedChunk, setSelectedChunk] = useState<DocumentChunkVO | null>(null);
  const [cachedChunk, setCachedChunk] = useState<DocumentChunkVO | null>(null);
  const [searchKeyword, setSearchKeyword] = useState("");

  useEffect(() => {
    if (selectedChunk) {
      setCachedChunk(selectedChunk);
    }
  }, [selectedChunk]);

  const isDrawerOpen = Boolean(selectedChunk);
  const displayChunk = selectedChunk || cachedChunk;

  const isProcessingStatus = (s?: string) =>
    ["UPLOADED", "QUEUED", "PARSING", "PARSED", "CHUNKING", "CHUNKED", "INDEXING"].includes(s || "");

  const isVersionProcessing = (s?: DocumentVersionStatus) =>
    s && ["UPLOADED", "QUEUED", "PARSING", "PARSED", "CHUNKING", "CHUNKED", "INDEXING", "DELETING"].includes(s);

  useEffect(() => {
    if (documentId) {
      loadData(documentId);
      loadVersions(documentId, versionsPage, versionsPageSize);
      loadAuditLogs(documentId, auditLogsPage, auditLogsPageSize);
    }
  }, [documentId, knowledgeBaseId]);

  // 当文档处于处理中时，每 2 秒静默轮询文档概览与处理状态
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

  // 当存在构建中版本时，每 2 秒静默轮询版本列表
  useEffect(() => {
    const hasProcessingVersion = versions.some((v) => isVersionProcessing(v.status));
    if (!documentId || !hasProcessingVersion) return;

    const timer = setInterval(async () => {
      try {
        const res = await documentApi.listDocumentVersions(documentId, versionsPage, versionsPageSize, knowledgeBaseId);
        setVersions(res.records || []);
        setVersionsTotal(res.total || res.records?.length || 0);
      } catch (err) {
        console.warn("Silent polling versions error:", err);
      }
    }, 2000);

    return () => clearInterval(timer);
  }, [documentId, knowledgeBaseId, versions, versionsPage, versionsPageSize]);

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

  const loadVersions = async (id: string | number, page = 1, size = 20) => {
    setIsVersionsLoading(true);
    try {
      const res = await documentApi.listDocumentVersions(id, page, size, knowledgeBaseId);
      setVersions(res.records || []);
      setVersionsTotal(res.total || res.records?.length || 0);
    } catch (err) {
      console.warn("加载版本历史列表失败:", err);
    } finally {
      setIsVersionsLoading(false);
    }
  };

  const loadAuditLogs = async (id: string | number, page = 1, size = 20) => {
    setIsAuditLogsLoading(true);
    try {
      const res = await documentApi.listDocumentVersionOperationLogs(id, page, size, knowledgeBaseId);
      setAuditLogs(res.records || []);
      setAuditLogsTotal(res.total || res.records?.length || 0);
    } catch (err) {
      console.warn("加载版本审计日志失败:", err);
    } finally {
      setIsAuditLogsLoading(false);
    }
  };

  const handleRetry = async () => {
    if (!documentId) return;
    setActionLoading(true);
    try {
      const res = await documentApi.retryDocument(documentId, knowledgeBaseId);
      feishuToast.success(`文档重试命令已提交！批次号: ${res.processId || "已触发"}`);
      await loadData(documentId);
    } catch (err: any) {
      feishuToast.error(`重试失败: ${err?.message || "服务端异常"}`);
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
      feishuToast.success(`重新切片与处理已提交！批次号: ${res.processId || "处理中"}`);
      await loadData(documentId);
    } catch (err: any) {
      feishuToast.error(`提交流水线失败: ${err?.message || "服务端异常"}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleCleanupIndex = () => {
    if (!documentId) return;
    feishuDialog.danger({
      title: "确认清理索引？",
      content: "确定要清理当前文档全部向量与关键词索引吗？清理后该文档在重新构建索引前将无法被检索命中。",
      okText: "清理",
      cancelText: "取消",
      onOk: async () => {
        setActionLoading(true);
        try {
          const res = await documentApi.deleteDocumentIndex(documentId);
          feishuToast.success(`索引清理完成！向量清理: ${res.vectorCleanedCount} 条，关键词清理: ${res.keywordCleanedCount} 条`);
          await loadData(documentId);
        } catch (err: any) {
          feishuToast.error(`清理索引失败: ${err?.message || "服务端异常"}`);
        } finally {
          setActionLoading(false);
        }
      },
    });
  };

  // 版本操作：激活生效版本 (回滚/切换/重新激活)
  const handleActivateVersion = (versionId: number | string, revNo: number, isActive: boolean) => {
    if (!documentId) return;
    const actionLabel = isActive ? "重新激活" : "设为生效";
    feishuDialog.info({
      title: "提示",
      content: `确定要将版本 Revision #${revNo} ${actionLabel}为当前生效的检索版本吗？`,
      showCancel: true,
      okText: "确定",
      cancelText: "取消",
      onOk: async () => {
        setActionLoading(true);
        try {
          await documentApi.activateDocumentVersion(documentId, versionId, knowledgeBaseId);
          feishuToast.success(`已成功将版本 Revision #${revNo} ${actionLabel}为当前生效版本！`);
          await Promise.all([
            loadData(documentId),
            loadVersions(documentId, versionsPage, versionsPageSize),
            loadAuditLogs(documentId, auditLogsPage, auditLogsPageSize),
          ]);
        } catch (err: any) {
          feishuToast.error(`${actionLabel}版本失败: ${err?.message || "服务端异常"}`);
        } finally {
          setActionLoading(false);
        }
      },
    });
  };

  // 版本操作：重试失败版本
  const handleRetryVersion = async (versionId: number | string, revNo: number) => {
    if (!documentId) return;
    setActionLoading(true);
    try {
      await documentApi.retryDocumentVersion(documentId, versionId, knowledgeBaseId);
      feishuToast.success(`版本 Revision #${revNo} 重新处理命令已提交！`);
      await Promise.all([
        loadVersions(documentId, versionsPage, versionsPageSize),
        loadAuditLogs(documentId, auditLogsPage, auditLogsPageSize),
      ]);
    } catch (err: any) {
      feishuToast.error(`重试版本失败: ${err?.message || "服务端异常"}`);
    } finally {
      setActionLoading(false);
    }
  };

  // 版本操作：永久删除非生效历史版本
  const handleDeleteVersion = (versionId: number | string, revNo: number) => {
    if (!documentId) return;
    feishuDialog.danger({
      title: `是否删除：Revision #${revNo}？`,
      content: `确定要永久删除历史版本 Revision #${revNo} 吗？此操作将清理该版本的存储与索引快照且不可逆！`,
      okText: "删除",
      cancelText: "取消",
      onOk: async () => {
        setActionLoading(true);
        try {
          await documentApi.deleteDocumentVersion(documentId, versionId, knowledgeBaseId);
          feishuToast.success(`历史版本 Revision #${revNo} 已成功删除！`);
          await Promise.all([
            loadVersions(documentId, versionsPage, versionsPageSize),
            loadAuditLogs(documentId, auditLogsPage, auditLogsPageSize),
          ]);
        } catch (err: any) {
          feishuToast.error(`删除历史版本失败: ${err?.message || "服务端异常"}`);
        } finally {
          setActionLoading(false);
        }
      },
    });
  };

  // 渲染飞书风格彩色矢量格式图标
  const renderFormatIcon = (fileName?: string, size: number = 28) => {
    return <FeishuDocIcon fileName={fileName} size={size} />;
  };

  // 渲染版本状态胶囊
  const renderVersionStatusPill = (status: DocumentVersionStatus) => {
    switch (status) {
      case "INDEX_READY":
        return <FeishuPill variant="green" dotColor="#10A893">索引已就绪</FeishuPill>;
      case "INDEXING":
        return (
          <FeishuPill variant="blue" dotColor="#3370FF">
            <span className="inline-flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-[#3370FF] animate-ping shrink-0" />
              写入索引中
            </span>
          </FeishuPill>
        );
      case "CHUNKED":
        return <FeishuPill variant="purple" dotColor="#8D55ED">已完成切分</FeishuPill>;
      case "CHUNKING":
        return (
          <FeishuPill variant="purple" dotColor="#8D55ED">
            <span className="inline-flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-[#8D55ED] animate-ping shrink-0" />
              文本切分中
            </span>
          </FeishuPill>
        );
      case "PARSED":
        return <FeishuPill variant="blue" dotColor="#3370FF">解析完成</FeishuPill>;
      case "PARSING":
        return (
          <FeishuPill variant="blue" dotColor="#3370FF">
            <span className="inline-flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-[#3370FF] animate-ping shrink-0" />
              文档解析中
            </span>
          </FeishuPill>
        );
      case "QUEUED":
        return (
          <FeishuPill variant="orange" dotColor="#FF7D00">
            <span className="inline-flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-[#FF7D00] animate-pulse shrink-0" />
              排队中
            </span>
          </FeishuPill>
        );
      case "UPLOADED":
        return <FeishuPill variant="gray" dotColor="#8F959E">已就绪待处理</FeishuPill>;
      case "FAILED":
        return <FeishuPill variant="red" dotColor="#F53F3F">处理失败</FeishuPill>;
      case "DELETING":
        return <FeishuPill variant="red" dotColor="#F53F3F">删除清理中</FeishuPill>;
      default:
        return <FeishuPill variant="gray">{status}</FeishuPill>;
    }
  };

  // 渲染版本审计操作类型胶囊
  const renderAuditTypePill = (opType: DocumentVersionOperationType) => {
    switch (opType) {
      case "UPLOAD":
        return <FeishuPill variant="blue" dotColor="#3370FF">上传新版本</FeishuPill>;
      case "AUTO_PUBLISH":
        return <FeishuPill variant="green" dotColor="#10A893">自动发布上线</FeishuPill>;
      case "ROLLBACK":
        return <FeishuPill variant="orange" dotColor="#FF7D00">版本回退/切换</FeishuPill>;
      case "RETRY":
        return <FeishuPill variant="purple" dotColor="#8D55ED">人工重试</FeishuPill>;
      case "DELETE":
        return <FeishuPill variant="red" dotColor="#F53F3F">版本删除</FeishuPill>;
      default:
        return <FeishuPill variant="gray">{opType}</FeishuPill>;
    }
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

  // -------------------------------------------------------------
  // FeishuDataTable 列定义：版本历史 (Versions)
  // -------------------------------------------------------------
  const versionColumns: FeishuColumn<DocumentVersionVO>[] = [
    {
      key: "revisionNo",
      title: "版本序号",
      dataIndex: "revisionNo",
      dataType: "text",
      width: 110,
      sortable: true,
      render: (val, record) => {
        return (
          <div className="flex items-center gap-1.5 py-0.5">
            <span className="font-mono text-[13px] font-semibold text-[#1F2329] bg-[#F2F3F5] px-2 py-0.5 rounded-[4px]">
              Rev #{val}
            </span>
            {record.active && (
              <FeishuPill variant="green" showDot={false} className="text-[11px] px-1.5 py-0">当前生效</FeishuPill>
            )}
          </div>
        );
      },
    },
    {
      key: "originalFileName",
      title: "原始文件名",
      dataIndex: "originalFileName",
      dataType: "text",
      width: 260,
      render: (val) => (
        <div className="flex items-center gap-2 py-0.5 min-w-0">
          {renderFormatIcon(val)}
          <span className="text-[14px] text-[#1F2329] font-normal truncate" title={val}>
            {val || "—"}
          </span>
        </div>
      ),
    },
    {
      key: "status",
      title: "版本状态",
      dataIndex: "status",
      dataType: "select",
      width: 120,
      sortable: true,
      render: (val) => renderVersionStatusPill(val),
    },
    {
      key: "failureReason",
      title: "异常原因",
      dataIndex: "failureReason",
      dataType: "text",
      width: 150,
      render: (val, record) => {
        if (!val && !record.failureStage) return <span className="text-[#8F959E] text-[13px]">正常</span>;
        return (
          <span className="text-[#F53F3F] text-[12px] truncate max-w-[140px] block" title={val || record.failureStage}>
            [{record.failureStage || "异常"}] {val || "处理失败"}
          </span>
        );
      },
    },
    {
      key: "indexReadyTime",
      title: "索引预热完成时间",
      dataIndex: "indexReadyTime",
      dataType: "date",
      width: 160,
      sortable: true,
      render: (val) => (
        <span className="text-[13px] text-[#646A75] tabular-nums font-normal">
          {val ? new Date(val).toLocaleString("zh-CN") : "—"}
        </span>
      ),
    },
    {
      key: "createTime",
      title: "上传时间",
      dataIndex: "createTime",
      dataType: "date",
      width: 160,
      sortable: true,
      render: (val) => (
        <span className="text-[13px] text-[#646A75] tabular-nums font-normal">
          {val ? new Date(val).toLocaleString("zh-CN") : "—"}
        </span>
      ),
    },
    {
      key: "actions",
      title: "操作",
      width: 130,
      render: (_, record) => {
        return (
          <div className="flex items-center justify-start gap-2">
            {record.status === "INDEX_READY" && (
              <FeishuActionLink
                className={actionLoading ? "opacity-50 pointer-events-none" : ""}
                onClick={() => !actionLoading && handleActivateVersion(record.documentVersionId, record.revisionNo, record.active)}
              >
                {record.active ? "重新激活" : "设为生效"}
              </FeishuActionLink>
            )}

            {record.status === "FAILED" && (
              <FeishuActionLink
                className={actionLoading ? "opacity-50 pointer-events-none" : ""}
                onClick={() => !actionLoading && handleRetryVersion(record.documentVersionId, record.revisionNo)}
              >
                重试
              </FeishuActionLink>
            )}

            {record.status !== "DELETING" && !record.active && (
              <FeishuActionLink
                variant="danger"
                className={actionLoading ? "opacity-50 pointer-events-none" : ""}
                onClick={() => !actionLoading && handleDeleteVersion(record.documentVersionId, record.revisionNo)}
              >
                删除
              </FeishuActionLink>
            )}
          </div>
        );
      },
    },
  ];

  // -------------------------------------------------------------
  // FeishuDataTable 列定义：版本审计 (Audit Logs)
  // -------------------------------------------------------------
  const auditLogColumns: FeishuColumn<DocumentVersionOperationLogVO>[] = [
    {
      key: "operationLogId",
      title: "审计流水号",
      dataIndex: "operationLogId",
      dataType: "text",
      width: 190,
      sortable: true,
      render: (val) => (
        <span className="font-mono text-[13px] text-[#646A75] bg-[#F2F3F5] px-1.5 py-0.5 rounded">
          #{val}
        </span>
      ),
    },
    {
      key: "documentVersionId",
      title: "关联版本 ID",
      dataIndex: "documentVersionId",
      dataType: "text",
      width: 210,
      render: (val) => (
        <span className="font-mono text-[13px] text-[#3370FF]">
          Ver #{val}
        </span>
      ),
    },
    {
      key: "operationType",
      title: "操作类型",
      dataIndex: "operationType",
      dataType: "select",
      width: 130,
      sortable: true,
      render: (val) => renderAuditTypePill(val),
    },
    {
      key: "activationGeneration",
      title: "生效代次",
      dataIndex: "activationGeneration",
      dataType: "number",
      width: 90,
      render: (val) => (
        <span className="font-mono text-[13px] text-[#1F2329]">
          {val != null ? `Gen #${val}` : "—"}
        </span>
      ),
    },
    {
      key: "operatorId",
      title: "操作人",
      dataIndex: "operatorId",
      dataType: "user",
      width: 100,
      render: (val) => (
        <FeishuTag>{val || "system"}</FeishuTag>
      ),
    },
    {
      key: "operationDetail",
      title: "操作详情描述",
      dataIndex: "operationDetail",
      dataType: "text",
      width: 260,
      render: (val) => (
        <span className="text-[13px] text-[#1F2329] font-normal truncate block" title={val}>
          {val || "—"}
        </span>
      ),
    },
    {
      key: "createTime",
      title: "操作发生时间",
      dataIndex: "createTime",
      dataType: "date",
      width: 160,
      sortable: true,
      render: (val) => (
        <span className="text-[13px] text-[#646A75] tabular-nums font-normal">
          {val ? new Date(val).toLocaleString("zh-CN") : "—"}
        </span>
      ),
    },
  ];

  return (
    <div style={{ fontFamily: FEISHU_FONT_FAMILY }} className="flex items-start w-full select-none px-6 max-w-[1720px] mx-auto pb-10">
      {/* 左侧主区域 */}
      <div className="flex-1 min-w-0 flex flex-col gap-4 transition-all duration-300 ease-[cubic-bezier(0.16,1,0.3,1)]">
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
              {renderFormatIcon(overview?.originalFileName || overview?.fileName, 36)}
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
                  文档版本迭代、全链路审计与结构化分块工作区
                </p>
              </div>
            </div>
          </div>

          {/* 顶部操作按钮组 */}
          <div className="flex items-center gap-2">
            <button
              onClick={() => {
                if (documentId) {
                  loadData(documentId);
                  loadVersions(documentId, versionsPage, versionsPageSize);
                  loadAuditLogs(documentId, auditLogsPage, auditLogsPageSize);
                }
              }}
              disabled={isLoading || isVersionsLoading || actionLoading}
              className="h-8 px-3 rounded-[6px] border border-[#D0D3D6] bg-white hover:bg-[#EFF0F1] text-[13px] font-medium text-[#1F2329] inline-flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isLoading || isVersionsLoading ? "animate-spin text-[#3370FF]" : "text-[#646A75]"}`} />
              <span>刷新</span>
            </button>

            <button
              onClick={() => setIsUploadVersionModalOpen(true)}
              className="h-8 px-3.5 rounded-[6px] bg-[#3370FF] hover:bg-[#2860E1] active:bg-[#1F4EC9] text-[13px] font-medium text-white inline-flex items-center gap-1.5 transition-colors cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>上传新版本</span>
            </button>

            <button
              onClick={handleReprocess}
              disabled={actionLoading}
              className="h-8 px-3 rounded-[6px] border border-[#D0D3D6] bg-white hover:bg-[#EFF0F1] text-[13px] font-medium text-[#1F2329] inline-flex items-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
            >
              <Play className="w-3.5 h-3.5 fill-[#3370FF] text-[#3370FF]" />
              <span>重新切片</span>
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
            <CheckCircle2 className="w-4 h-4 text-[#00B42A]" />
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

        {/* 2. 统计指标看板条 (切片总数、文档总字符数、Token预估规模、平均分块长度) */}
        <div className="grid grid-cols-4 gap-4">
          <div className="p-3.5 bg-white border border-[#DEE0E3] rounded-[12px] shadow-2xs">
            <span className="text-[12px] text-[#646A75]">切片总数 (Chunks)</span>
            <p className="text-[20px] font-bold text-[#10A893] mt-1 font-mono">
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

        {/* 3. 飞书 Universe 风格 Tab 栏切换器 */}
        <div className="rounded-[12px] border border-[#DEE0E3] bg-white px-5 shadow-2xs flex items-center justify-between">
          <div className="flex items-center gap-8">
            <button
              type="button"
              onClick={() => setActiveTab("versions")}
              className={`py-3.5 text-[14px] font-medium border-b-2 transition-all cursor-pointer flex items-center gap-2 ${
                activeTab === "versions"
                  ? "border-[#3370FF] text-[#3370FF] font-semibold"
                  : "border-transparent text-[#646A75] hover:text-[#1F2329]"
              }`}
            >
              <History className="w-4 h-4" />
              <span>版本历史</span>
              <span className={`text-[11px] px-1.5 py-0.2 rounded-full ${activeTab === "versions" ? "bg-[#E8F3FF] text-[#3370FF]" : "bg-[#F2F3F5] text-[#8F959E]"}`}>
                {versionsTotal || versions.length}
              </span>
            </button>

            <button
              type="button"
              onClick={() => setActiveTab("auditLogs")}
              className={`py-3.5 text-[14px] font-medium border-b-2 transition-all cursor-pointer flex items-center gap-2 ${
                activeTab === "auditLogs"
                  ? "border-[#3370FF] text-[#3370FF] font-semibold"
                  : "border-transparent text-[#646A75] hover:text-[#1F2329]"
              }`}
            >
              <Activity className="w-4 h-4" />
              <span>版本审计</span>
              <span className={`text-[11px] px-1.5 py-0.2 rounded-full ${activeTab === "auditLogs" ? "bg-[#E8F3FF] text-[#3370FF]" : "bg-[#F2F3F5] text-[#8F959E]"}`}>
                {auditLogsTotal || auditLogs.length}
              </span>
            </button>

            <button
              type="button"
              onClick={() => setActiveTab("chunks")}
              className={`py-3.5 text-[14px] font-medium border-b-2 transition-all cursor-pointer flex items-center gap-2 ${
                activeTab === "chunks"
                  ? "border-[#3370FF] text-[#3370FF] font-semibold"
                  : "border-transparent text-[#646A75] hover:text-[#1F2329]"
              }`}
            >
              <Layers className="w-4 h-4" />
              <span>分块详情</span>
              <span className={`text-[11px] px-1.5 py-0.2 rounded-full ${activeTab === "chunks" ? "bg-[#E8F3FF] text-[#3370FF]" : "bg-[#F2F3F5] text-[#8F959E]"}`}>
                {chunks.length}
              </span>
            </button>
          </div>

          {/* 右侧微操作 */}
          {activeTab === "versions" && (
            <button
              onClick={() => setIsUploadVersionModalOpen(true)}
              className="h-7 px-2.5 rounded-[6px] bg-[#E8F3FF] hover:bg-[#D1E5FF] text-[#3370FF] text-[12px] font-medium inline-flex items-center gap-1 transition-colors cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>新建版本</span>
            </button>
          )}

          {activeTab === "chunks" && (
            <FeishuInput
              value={searchKeyword}
              onChange={(val) => setSearchKeyword(val)}
              onClear={() => setSearchKeyword("")}
              placeholder="搜索分块正文、序号..."
              prefix={<Search className="w-3.5 h-3.5 text-[#8F959E]" />}
              containerClassName="w-56 h-[28px] rounded-[6px]"
            />
          )}
        </div>

        {/* 4. Tab 区域内容呈现 */}
        <div>
          {/* Tab 1: 版本历史列表 (FeishuDataTable) */}
          {activeTab === "versions" && (
            <div className="transition-all duration-200">
              <FeishuDataTable
                columns={versionColumns}
                data={versions}
                rowKey="documentVersionId"
                selectable={false}
                searchPlaceholder="搜索版本文件名、状态..."
                addButtonText="上传新版本"
                onAdd={() => setIsUploadVersionModalOpen(true)}
                pagination={{
                  current: versionsPage,
                  total: versionsTotal,
                  pageSize: versionsPageSize,
                  onChange: (p, s) => {
                    setVersionsPage(p);
                    if (s && s !== versionsPageSize) {
                      setVersionsPageSize(s);
                      setVersionsPage(1);
                    }
                    if (documentId) {
                      loadVersions(documentId, p, s || versionsPageSize);
                    }
                  },
                }}
              />
            </div>
          )}

          {/* Tab 2: 版本操作审计列表 (FeishuDataTable) */}
          {activeTab === "auditLogs" && (
            <div className="transition-all duration-200">
              <FeishuDataTable
                columns={auditLogColumns}
                data={auditLogs}
                rowKey="operationLogId"
                selectable={false}
                searchPlaceholder="搜索审计操作人、类型或详情..."
                pagination={{
                  current: auditLogsPage,
                  total: auditLogsTotal,
                  pageSize: auditLogsPageSize,
                  onChange: (p, s) => {
                    setAuditLogsPage(p);
                    if (s && s !== auditLogsPageSize) {
                      setAuditLogsPageSize(s);
                      setAuditLogsPage(1);
                    }
                    if (documentId) {
                      loadAuditLogs(documentId, p, s || auditLogsPageSize);
                    }
                  },
                }}
              />
            </div>
          )}

          {/* Tab 3: 分块详情卡片流 */}
          {activeTab === "chunks" && (
            <div className="space-y-4">
              {isLoading ? (
                <div className="py-20 text-center text-xs text-feishu-text-muted bg-white rounded-xl border border-[#DEE0E3]">
                  正在加载文档分块内容流...
                </div>
              ) : filteredChunks.length === 0 ? (
                <div className="bg-white rounded-xl border border-[#DEE0E3] shadow-2xs">
                  <FeishuEmptyState
                    title={searchKeyword ? "未匹配到相关切片" : "暂无相关记录"}
                    description={
                      searchKeyword
                        ? `未找到包含 “${searchKeyword}” 的切片正文`
                        : "当前暂无切片数据，可点击上方「重新切片」触发处理"
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
                        className={`bg-white rounded-xl border shadow-2xs p-4 flex flex-col transition-colors duration-150 cursor-pointer group ${
                          isSelected
                            ? "border-feishu-blue!"
                            : "border-[#DEE0E3] hover:border-feishu-blue"
                        }`}
                      >
                        {/* 标头：Chunk #001 · 148 Tokens · 76 字符 */}
                        <div
                          className={`text-[13px] font-mono font-medium transition-colors truncate ${
                            isSelected
                              ? "text-feishu-blue"
                              : "text-feishu-text-secondary group-hover:text-feishu-blue"
                          }`}
                        >
                          <span
                            className={`font-semibold ${
                              isSelected
                                ? "text-feishu-blue"
                                : "text-feishu-text-primary group-hover:text-feishu-blue"
                            }`}
                          >
                            Chunk #{formattedIndex}
                          </span>
                          <span> · {tokenCount} Tokens · {charCount} 字符</span>
                        </div>

                        {/* 正文 */}
                        <div className="mt-3 text-[13px] text-feishu-text-primary leading-5.5 whitespace-pre-wrap wrap-break-word font-sans line-clamp-6">
                          {textContent}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* 右侧沉浸式分块 Markdown 抽屉面板 (支持流畅展开与收起双向动效、紧贴右侧与流体挤压) */}
      <div
        className={`shrink-0 sticky top-4 h-[calc(100vh-96px)] transition-all duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] overflow-hidden ${
          isDrawerOpen
            ? "w-115 lg:w-125 xl:w-135 opacity-100 translate-x-0 pointer-events-auto ml-5"
            : "w-0 opacity-0 translate-x-12 pointer-events-none ml-0"
        }`}
      >
        {displayChunk && (
          <div className="w-115 lg:w-125 xl:w-135 h-full bg-white rounded-xl border border-[#DEE0E3] shadow-[-6px_0_24px_rgba(31,35,41,0.08)] p-5 flex flex-col gap-4 overflow-hidden">
            {/* 抽屉头部 */}
            <div className="flex items-center justify-between pb-3 border-b border-[#DEE0E3] shrink-0">
              <div className="flex items-center gap-2 min-w-0">
                <span className="text-[16px] font-bold font-mono text-feishu-text-primary">
                  Chunk #{String(displayChunk.chunkOrder ?? displayChunk.chunkIndex ?? 1).padStart(3, "0")}
                </span>
                <span className="text-[12px] font-mono text-feishu-text-secondary bg-[#F2F3F5] px-2 py-0.5 rounded-sm truncate">
                  {displayChunk.tokenCount ?? Math.round(((displayChunk.text || displayChunk.content || "").length) * 0.7)} Tokens · {(displayChunk.text || displayChunk.content || "").length} 字符
                </span>
              </div>

              <button
                onClick={() => setSelectedChunk(null)}
                className="w-7 h-7 rounded-md hover:bg-[#EFF0F1] flex items-center justify-center text-feishu-text-secondary hover:text-feishu-text-primary transition-colors cursor-pointer"
                title="关闭"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Markdown 渲染正文视窗 */}
            <div className="bg-white rounded-lg border border-[#DEE0E3] flex-1 min-h-0 flex flex-col shadow-2xs overflow-hidden">
              <div className="flex items-center justify-between px-4 py-2.5 bg-[#F8F9FA] border-b border-[#DEE0E3] shrink-0">
                <span className="text-[12px] font-medium text-feishu-text-secondary">切片 Markdown 正文</span>
                <button
                  onClick={() => handleCopyContent(displayChunk.text || displayChunk.content || "")}
                  className="h-6 px-2 rounded-sm border border-feishu-border bg-white hover:bg-[#EFF0F1] text-[11px] font-medium text-[#646A75] hover:text-[#1F2329] inline-flex items-center gap-1 transition-colors cursor-pointer"
                >
                  {copied ? (
                    <>
                      <Check className="w-3 h-3 text-feishu-success" />
                      <span className="text-feishu-success">已复制</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-3 h-3" />
                      <span>复制正文</span>
                    </>
                  )}
                </button>
              </div>

              <div className="p-4 flex-1 overflow-y-auto custom-scrollbar leading-6 font-sans selection:bg-[#E8F4FF]">
                <FeishuMarkdown
                  content={displayChunk.text || displayChunk.content || ""}
                  className="text-feishu-text-primary"
                />
              </div>
            </div>
          </div>
        )}
      </div>

      {/* 5. 上传新版本弹窗 */}
      {documentId && (
        <DocumentVersionUploadModal
          isOpen={isUploadVersionModalOpen}
          documentId={documentId}
          documentTitle={overview?.title || overview?.originalFileName || overview?.fileName}
          knowledgeBaseId={knowledgeBaseId}
          onClose={() => setIsUploadVersionModalOpen(false)}
          onSuccess={() => {
            showFeedback("新版本文件已成功上传并提交后台流水线！");
            loadVersions(documentId, 1, versionsPageSize);
            loadAuditLogs(documentId, 1, auditLogsPageSize);
            loadData(documentId);
          }}
        />
      )}
    </div>
  );
};

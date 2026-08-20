import React, { useState, useEffect, useMemo } from "react";
import { useNavigate, useParams, Link } from "react-router-dom";
import {
  FileText,
  Plus,
  RefreshCw,
  FileCode,
  FileSpreadsheet,
  Globe,
  ArrowLeft,
  ChevronRight,
  BookOpen,
  Folder,
} from "lucide-react";
import { documentApi, knowledgeBaseApi, DEFAULT_KNOWLEDGE_BASE_ID } from "../../lib/api";
import { DocumentSummaryVO, DocumentStatus, FileType, KnowledgeBaseDetailVO } from "../../types";
import {
  FeishuDataTable,
  FeishuColumn,
  FeishuPill,
  FeishuCellMainSub,
  FeishuTag,
  FeishuActionLink,
  FEISHU_FONT_FAMILY,
} from "../../components/ui/feishu-table";
import { DocumentUploadModal } from "./components/DocumentUploadModal";
import { DocumentDrawer } from "./components/DocumentDrawer";

export const DocumentPage: React.FC = () => {
  const navigate = useNavigate();
  const { knowledgeBaseId: kbIdParam } = useParams<{ knowledgeBaseId?: string }>();
  const knowledgeBaseId = kbIdParam ? (isNaN(Number(kbIdParam)) ? kbIdParam : Number(kbIdParam)) : DEFAULT_KNOWLEDGE_BASE_ID;

  const [knowledgeBase, setKnowledgeBase] = useState<KnowledgeBaseDetailVO | null>(null);
  const [documents, setDocuments] = useState<DocumentSummaryVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedDocId, setSelectedDocId] = useState<string | number | null>(null);
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [totalCount, setTotalCount] = useState(0);

  useEffect(() => {
    loadKnowledgeBaseDetail();
  }, [knowledgeBaseId]);

  useEffect(() => {
    loadDocuments();
  }, [knowledgeBaseId, currentPage, pageSize]);

  const loadKnowledgeBaseDetail = async () => {
    try {
      const kb = await knowledgeBaseApi.getKnowledgeBase(knowledgeBaseId);
      setKnowledgeBase(kb);
    } catch {
      setKnowledgeBase({
        knowledgeBaseId,
        name: knowledgeBaseId === 1 ? "默认知识库" : `知识库 #${knowledgeBaseId}`,
        description: "系统内置知识库，统一管理智能检索增强与问答文档资产",
        isDefault: knowledgeBaseId === 1 ? 1 : 0,
      });
    }
  };

  const loadDocuments = async () => {
    setLoading(true);
    try {
      const page = await documentApi.listDocuments(currentPage, pageSize, knowledgeBaseId);
      setDocuments(page.records || []);
      setTotalCount(page.total || page.records?.length || 0);
    } catch (err) {
      console.error("Failed to load documents", err);
    } finally {
      setLoading(false);
    }
  };

  const isProcessing = (status: DocumentStatus) =>
    ["UPLOADED", "QUEUED", "PARSING", "PARSED", "CHUNKING", "CHUNKED", "INDEXING"].includes(status);

  // 自动轮询：当列表包含处理中的文档时，每 2 秒静默更新一次列表，完成时自动停止
  useEffect(() => {
    const hasProcessingDocs = documents.some((d) => isProcessing(d.status));
    if (!hasProcessingDocs) return;

    const timer = setInterval(async () => {
      try {
        const page = await documentApi.listDocuments(currentPage, pageSize, knowledgeBaseId);
        setDocuments(page.records || []);
        setTotalCount(page.total || page.records?.length || 0);
      } catch (err) {
        console.warn("Silent polling documents error:", err);
      }
    }, 2000);

    return () => clearInterval(timer);
  }, [documents, currentPage, pageSize, knowledgeBaseId]);

  // 各状态数量统计 (用于顶部概览条)
  const counts = useMemo(() => {
    const total = totalCount || documents.length;
    const indexed = documents.filter((d) => d.status === "INDEXED").length;
    const processing = documents.filter((d) => isProcessing(d.status)).length;
    const failed = documents.filter((d) => d.status === "FAILED").length;
    return { total, indexed, processing, failed };
  }, [documents, totalCount]);

  // 渲染飞书风格文件类型彩色图标
  const renderFileIcon = (fileType?: FileType, fileName?: string) => {
    const typeStr = fileType ? String(fileType).toUpperCase() : "";
    const lower = (fileName || "").toLowerCase();
    if (typeStr === "PDF" || lower.endsWith(".pdf")) {
      return (
        <div className="w-7 h-7 rounded-[6px] bg-[#FFF2F0] text-[#F53F3F] flex items-center justify-center shrink-0">
          <FileText className="w-4 h-4" />
        </div>
      );
    }
    if (typeStr === "MARKDOWN" || lower.endsWith(".md") || lower.endsWith(".markdown")) {
      return (
        <div className="w-7 h-7 rounded-[6px] bg-[#E8F3FF] text-[#3370FF] flex items-center justify-center shrink-0">
          <FileCode className="w-4 h-4" />
        </div>
      );
    }
    if (typeStr === "WORD" || lower.endsWith(".docx") || lower.endsWith(".doc")) {
      return (
        <div className="w-7 h-7 rounded-[6px] bg-[#E8F4FF] text-[#1456F0] flex items-center justify-center shrink-0">
          <FileText className="w-4 h-4" />
        </div>
      );
    }
    if (typeStr === "EXCEL" || lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv")) {
      return (
        <div className="w-7 h-7 rounded-[6px] bg-[#E8F7EC] text-[#00B42A] flex items-center justify-center shrink-0">
          <FileSpreadsheet className="w-4 h-4" />
        </div>
      );
    }
    if (typeStr === "PPT" || lower.endsWith(".pptx") || lower.endsWith(".ppt")) {
      return (
        <div className="w-7 h-7 rounded-[6px] bg-[#FFF7E8] text-[#FF7D00] flex items-center justify-center shrink-0">
          <FileText className="w-4 h-4" />
        </div>
      );
    }
    return (
      <div className="w-7 h-7 rounded-[6px] bg-[#F2F3F5] text-[#646A75] flex items-center justify-center shrink-0">
        <Globe className="w-4 h-4" />
      </div>
    );
  };

  // 渲染后端 9 大流转状态对应飞书胶囊标签
  const renderStatusPill = (status: DocumentStatus) => {
    switch (status) {
      case "INDEXED":
        return <FeishuPill variant="green" dotColor="#10A893">已完成索引</FeishuPill>;
      case "INDEXING":
        return (
          <FeishuPill variant="blue" dotColor="#3370FF">
            <span className="inline-flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-[#3370FF] animate-ping shrink-0" />
              索引写入中
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
              排队等待中
            </span>
          </FeishuPill>
        );
      case "UPLOADED":
        return <FeishuPill variant="gray" dotColor="#8F959E">已就绪待处理</FeishuPill>;
      case "FAILED":
        return <FeishuPill variant="red" dotColor="#F53F3F">处理失败</FeishuPill>;
      default:
        return <FeishuPill variant="gray">{status}</FeishuPill>;
    }
  };

  // 格式化文件大小
  const formatFileSize = (bytes?: number) => {
    if (bytes === undefined || bytes === null || bytes <= 0) return "—";
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  };

  // 1:1 飞书多维表格列定义 (无 font-mono，纯正飞书无衬线字体栈)
  const columns: FeishuColumn<DocumentSummaryVO>[] = [
    {
      key: "title",
      title: "文档名称",
      dataIndex: "title",
      dataType: "text",
      width: 340,
      sortable: true,
      render: (_, record) => {
        const displayTitle = record.title || record.originalFileName || "未命名文档";
        return (
          <div className="flex items-center gap-2.5 py-0.5 min-w-0">
            {renderFileIcon(record.fileType, record.originalFileName)}
            <span
              onClick={() => navigate(`/knowledge-base/${knowledgeBaseId}/documents/${record.documentId}`)}
              className="text-[14px] font-normal text-[#1F2329] hover:text-[#3370FF] transition-colors cursor-pointer truncate"
              title={displayTitle}
            >
              {displayTitle}
            </span>
          </div>
        );
      },
    },
    {
      key: "status",
      title: "处理状态",
      dataIndex: "status",
      dataType: "select",
      width: 150,
      sortable: true,
      options: [
        { label: "已完成索引", value: "INDEXED", pillVariant: "green" },
        { label: "索引写入中", value: "INDEXING", pillVariant: "blue" },
        { label: "已完成切分", value: "CHUNKED", pillVariant: "purple" },
        { label: "文本切分中", value: "CHUNKING", pillVariant: "purple" },
        { label: "解析完成", value: "PARSED", pillVariant: "blue" },
        { label: "文档解析中", value: "PARSING", pillVariant: "blue" },
        { label: "排队等待中", value: "QUEUED", pillVariant: "orange" },
        { label: "已就绪待处理", value: "UPLOADED", pillVariant: "gray" },
        { label: "处理失败", value: "FAILED", pillVariant: "red" },
      ],
      render: (val) => renderStatusPill(val),
    },
    {
      key: "fileType",
      title: "文件格式",
      dataIndex: "fileType",
      dataType: "select",
      width: 110,
      sortable: true,
      options: [
        { label: "PDF", value: "PDF" },
        { label: "WORD", value: "WORD" },
        { label: "EXCEL", value: "EXCEL" },
        { label: "PPT", value: "PPT" },
        { label: "MARKDOWN", value: "MARKDOWN" },
        { label: "TEXT", value: "TEXT" },
      ],
      render: (val) => (
        <span className="text-[14px] text-[#646A75] font-normal">
          {val || "—"}
        </span>
      ),
    },
    {
      key: "fileSize",
      title: "文件大小",
      dataIndex: "fileSize",
      dataType: "number",
      width: 120,
      sortable: true,
      render: (val) => (
        <span className="text-[14px] text-[#646A75] tabular-nums font-normal">
          {formatFileSize(val)}
        </span>
      ),
    },
    {
      key: "createBy",
      title: "所有者",
      dataIndex: "createBy",
      dataType: "user",
      width: 120,
      render: (val) => (
        <FeishuTag>{val || "系统管理员"}</FeishuTag>
      ),
    },
    {
      key: "updatedTime",
      title: "更新时间",
      dataIndex: "updatedTime",
      dataType: "date",
      width: 140,
      sortable: true,
      render: (val) => (
        <span className="text-[14px] text-[#646A75] tabular-nums font-normal">
          {val ? new Date(val).toLocaleDateString("zh-CN") : "—"}
        </span>
      ),
    },
    {
      key: "actions",
      title: "操作",
      width: 220,
      render: (_, record) => (
        <div className="flex items-center justify-start gap-1">
          <FeishuActionLink
            onClick={() => navigate(`/knowledge-base/${knowledgeBaseId}/documents/${record.documentId}`)}
          >
            分块探查
          </FeishuActionLink>

          <FeishuActionLink
            onClick={() => setSelectedDocId(record.documentId)}
          >
            诊断与配置
          </FeishuActionLink>

          {record.status === "FAILED" && (
            <FeishuActionLink
              onClick={async () => {
                await documentApi.retryDocument(record.documentId, knowledgeBaseId);
                loadDocuments();
              }}
            >
              重试
            </FeishuActionLink>
          )}

          <FeishuActionLink
            variant="danger"
            onClick={async () => {
              if (confirm(`确定要删除文档吗？`)) {
                await documentApi.deleteDocument(record.documentId, knowledgeBaseId);
                loadDocuments();
              }
            }}
          >
            删除
          </FeishuActionLink>
        </div>
      ),
    },
  ];

  const kbName = knowledgeBase?.name || "知识库文档管理";
  const isDefault = knowledgeBase?.isDefault === 1;

  return (
    <div style={{ fontFamily: FEISHU_FONT_FAMILY }} className="space-y-4 select-none max-w-7xl mx-auto pb-8">
      {/* 0. 顶部面包屑导航 */}
      <nav aria-label="Breadcrumb" className="flex items-center gap-2 text-[13px] text-[#8F959E]">
        <Link
          to="/knowledge-base"
          className="flex items-center gap-1 text-[#646A73] hover:text-[#3370FF] transition-colors"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          知识库管理
        </Link>
        <ChevronRight className="h-3 w-3 text-[#8F959E]" />
        <span className="font-semibold text-[#1F2329] flex items-center gap-1.5">
          {isDefault ? <BookOpen className="h-3.5 w-3.5 text-[#3370FF]" /> : <Folder className="h-3.5 w-3.5 text-[#8F959E]" />}
          {kbName}
        </span>
      </nav>

      {/* 1. 顶部飞书知识库工作台概览卡片 (纯净白底 + 12px 圆角 + CRM 粗体指标) */}
      <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-5 shadow-2xs flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <h1 className="text-[20px] font-semibold text-[#1F2329]">
              {kbName}
            </h1>
            {isDefault && (
              <FeishuPill variant="blue" showDot={false} className="text-[11px] px-2 py-0">
                默认
              </FeishuPill>
            )}
          </div>
          <p className="mt-1 text-[13px] text-[#646A73]">
            {knowledgeBase?.description || "统一管理企业级知识库文档、检索增强语料与向量化索引状态"}
          </p>

          {/* 底部指标行 (1:1 飞书 CRM 粗体青绿数字，无 mono) */}
          <div className="flex items-center gap-4 text-[13px] text-[#646A73] mt-3">
            <span className="flex items-center gap-1">
              总文档资产: <strong className="font-bold text-[#10A893] text-[15px] tabular-nums">{counts.total}</strong> 篇
            </span>
            <span>·</span>
            <span>
              已就绪: <strong className="font-bold text-[#10A893] tabular-nums">{counts.indexed}</strong> 篇
            </span>
            <span>·</span>
            <span>
              流水线处理中: <strong className="font-bold text-[#3370FF] tabular-nums">{counts.processing}</strong> 篇
            </span>
            {counts.failed > 0 && (
              <>
                <span>·</span>
                <span>
                  异常告警: <strong className="font-bold text-[#F54A45] tabular-nums">{counts.failed}</strong> 篇
                </span>
              </>
            )}
          </div>
        </div>

        {/* 右侧操作按钮组 (去冗余，统一为飞书标准按钮) */}
        <div className="flex items-center gap-2.5">
          <button
            type="button"
            onClick={loadDocuments}
            disabled={loading}
            className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] border border-[#DEE0E3] bg-white px-3.5 text-[14px] font-normal text-[#1F2329] hover:bg-[#F2F3F5] active:bg-[#E5E6EB] transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin text-[#3370FF]" : "text-[#646A73]"}`} />
            刷新
          </button>

          <button
            type="button"
            onClick={() => setIsUploadModalOpen(true)}
            className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] bg-[#3370FF] px-4 text-[14px] font-normal text-white hover:bg-[#2860E1] active:bg-[#1F4EC9] transition-colors"
          >
            <Plus className="h-4 w-4" />
            导入文档
          </button>
        </div>
      </div>

      {/* 2. 核心 1:1 飞书多维表格组件 (纯净无冗余 Tab，内置搜索、筛选与自适应列宽) */}
      <FeishuDataTable
        columns={columns}
        data={documents}
        rowKey="documentId"
        selectable={false}
        searchPlaceholder="搜索文档名称、文件格式..."
        addButtonText="导入文档"
        onAdd={() => setIsUploadModalOpen(true)}
        pagination={{
          current: currentPage,
          total: totalCount,
          pageSize: pageSize,
          onChange: (p, s) => {
            setCurrentPage(p);
            if (s && s !== pageSize) {
              setPageSize(s);
              setCurrentPage(1);
            }
          },
        }}
      />

      {/* 3. 飞书多源文档接入 Hub 模态弹窗 */}
      <DocumentUploadModal
        isOpen={isUploadModalOpen}
        knowledgeBaseId={knowledgeBaseId}
        onClose={() => setIsUploadModalOpen(false)}
        onSuccess={() => {
          loadDocuments();
        }}
      />

      {/* 4. 飞书风格右侧滑出文档诊断与切片抽屉 (Slide-over Drawer) */}
      <DocumentDrawer
        documentId={selectedDocId}
        knowledgeBaseId={knowledgeBaseId}
        onClose={() => setSelectedDocId(null)}
        onReloadList={loadDocuments}
      />
    </div>
  );
};

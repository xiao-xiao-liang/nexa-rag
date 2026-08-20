import React, { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import {
  AlertCircle,
  Plus,
  Search,
  X,
  LayoutGrid,
  Table as TableIcon,
  RefreshCw,
  BookOpen,
  Folder,
} from "lucide-react";
import { knowledgeBaseApi } from "../../lib/api";
import { KnowledgeBaseSummaryVO } from "../../types";
import { KnowledgeBaseCard } from "./KnowledgeBaseCard";
import { KnowledgeBaseDialog } from "./KnowledgeBaseDialog";
import { DeleteKnowledgeBaseDialog } from "./DeleteKnowledgeBaseDialog";
import {
  FeishuDataTable,
  FeishuColumn,
  FeishuPill,
  FeishuActionLink,
  FeishuEmptyState,
  FEISHU_FONT_FAMILY,
} from "../../components/ui/feishu-table";
import { cn } from "../../lib/utils";

type ViewMode = "grid" | "table";

export const KnowledgeBaseManagePage: React.FC = () => {
  const navigate = useNavigate();
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseSummaryVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [viewMode, setViewMode] = useState<ViewMode>("grid");

  // 弹窗状态
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<KnowledgeBaseSummaryVO | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<KnowledgeBaseSummaryVO | null>(null);

  useEffect(() => {
    loadKnowledgeBases();
  }, []);

  const loadKnowledgeBases = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await knowledgeBaseApi.listKnowledgeBases(1, 100);
      setKnowledgeBases(res.records || []);
    } catch (err: any) {
      setError(err?.message || "加载知识库列表失败，请重试");
    } finally {
      setLoading(false);
    }
  };

  // 模糊搜索过滤
  const filteredKbs = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return knowledgeBases;
    return knowledgeBases.filter(
      (kb) =>
        kb.name.toLowerCase().includes(q) ||
        (kb.description && kb.description.toLowerCase().includes(q))
    );
  }, [knowledgeBases, query]);

  // 全局汇总统计
  const summaryStats = useMemo(() => {
    let totalDocs = 0;
    let indexedDocs = 0;
    let processingDocs = 0;
    let failedDocs = 0;

    knowledgeBases.forEach((kb) => {
      const stats = kb.statistics;
      if (stats) {
        totalDocs += stats.totalCount || 0;
        indexedDocs += stats.indexedCount || 0;
        processingDocs += stats.processingCount || 0;
        failedDocs += stats.failedCount || 0;
      }
    });

    return {
      kbCount: knowledgeBases.length,
      totalDocs,
      indexedDocs,
      processingDocs,
      failedDocs,
    };
  }, [knowledgeBases]);

  // 存在处理中文档时，每 3 秒静默更新知识库列表和统计数据，完成后自动停止
  useEffect(() => {
    if (summaryStats.processingDocs <= 0) return;

    const timer = setInterval(async () => {
      try {
        const res = await knowledgeBaseApi.listKnowledgeBases(1, 100);
        setKnowledgeBases(res.records || []);
      } catch (err) {
        console.warn("Silent polling kb error:", err);
      }
    }, 3000);

    return () => clearInterval(timer);
  }, [summaryStats.processingDocs]);

  // 处理新建知识库
  const handleCreate = async (data: { name: string; description: string }) => {
    await knowledgeBaseApi.createKnowledgeBase(data);
    await loadKnowledgeBases();
  };

  // 处理编辑知识库
  const handleUpdate = async (data: { name: string; description: string }) => {
    if (!editTarget) return;
    await knowledgeBaseApi.updateKnowledgeBase(editTarget.knowledgeBaseId, data);
    await loadKnowledgeBases();
  };

  // 处理删除知识库
  const handleDelete = async (id: number | string) => {
    await knowledgeBaseApi.deleteKnowledgeBase(id);
    await loadKnowledgeBases();
  };

  // 飞书表格视图 Columns 定义
  const kbColumns: FeishuColumn<KnowledgeBaseSummaryVO>[] = [
    {
      key: "name",
      title: "知识库名称",
      dataIndex: "name",
      dataType: "text",
      width: 260,
      render: (_, record) => {
        const isDefault = record.isDefault === 1;
        return (
          <div className="flex items-center gap-2.5 min-w-0">
            <div
              className={cn(
                "flex h-7 w-7 shrink-0 items-center justify-center rounded-[6px]",
                isDefault ? "bg-[#E8F3FF] text-[#3370FF]" : "bg-[#F0F4FF] text-[#3370FF]"
              )}
            >
              {isDefault ? <BookOpen className="h-3.5 w-3.5" /> : <Folder className="h-3.5 w-3.5" />}
            </div>
            <span
              onClick={() => navigate(`/knowledge-base/${record.knowledgeBaseId}`)}
              className="font-medium text-[#1F2329] hover:text-[#3370FF] transition-colors cursor-pointer truncate"
            >
              {record.name}
            </span>
            {isDefault && (
              <FeishuPill variant="blue" showDot={false} className="text-[11px] px-1.5 py-0 shrink-0">
                默认
              </FeishuPill>
            )}
          </div>
        );
      },
    },
    {
      key: "description",
      title: "描述说明",
      dataIndex: "description",
      dataType: "text",
      width: 280,
      render: (val) => (
        <span className="text-[#646A73] text-[13px] truncate block" title={val}>
          {val || "—"}
        </span>
      ),
    },
    {
      key: "totalCount",
      title: "文档总数",
      align: "right",
      dataType: "number",
      width: 110,
      render: (_, record) => (
        <span className="text-[#1F2329] tabular-nums font-normal text-[14px]">
          {record.statistics?.totalCount || 0}
        </span>
      ),
    },
    {
      key: "indexedCount",
      title: "已就绪",
      align: "right",
      dataType: "number",
      width: 100,
      render: (_, record) => (
        <span className="text-[#00B42A] tabular-nums font-normal text-[14px]">
          {record.statistics?.indexedCount || 0}
        </span>
      ),
    },
    {
      key: "processingCount",
      title: "处理中",
      align: "right",
      dataType: "number",
      width: 100,
      render: (_, record) => (
        <span className="text-[#3370FF] tabular-nums font-normal text-[14px]">
          {record.statistics?.processingCount || 0}
        </span>
      ),
    },
    {
      key: "failedCount",
      title: "异常",
      align: "right",
      dataType: "number",
      width: 100,
      render: (_, record) => (
        <span
          className={cn(
            "tabular-nums font-normal text-[14px]",
            (record.statistics?.failedCount || 0) > 0 ? "text-[#F54A45]" : "text-[#8F959E]"
          )}
        >
          {record.statistics?.failedCount || 0}
        </span>
      ),
    },
    {
      key: "updatedTime",
      title: "更新时间",
      dataType: "date",
      width: 140,
      render: (val) => (
        <span className="text-[#646A73] text-[13px] tabular-nums">
          {val ? new Date(val).toLocaleDateString("zh-CN") : "—"}
        </span>
      ),
    },
    {
      key: "actions",
      title: "操作",
      width: 210,
      fixed: "right",
      render: (_, record) => {
        const isDefault = record.isDefault === 1;
        return (
          <div className="flex items-center justify-start gap-1" onClick={(e) => e.stopPropagation()}>
            <FeishuActionLink onClick={() => navigate(`/knowledge-base/${record.knowledgeBaseId}`)}>
              进入知识库
            </FeishuActionLink>
            <FeishuActionLink onClick={() => setEditTarget(record)}>
              {isDefault ? "属性" : "编辑"}
            </FeishuActionLink>
            {!isDefault && (
              <FeishuActionLink variant="danger" onClick={() => setDeleteTarget(record)}>
                删除
              </FeishuActionLink>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <div className="space-y-5 max-w-7xl mx-auto pb-10" style={{ fontFamily: FEISHU_FONT_FAMILY }}>
      {/* 1. 顶部 Header 与操作区 */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <h1 className="text-[20px] font-semibold text-[#1F2329]">知识库管理</h1>
            <span className="text-[13px] text-[#8F959E] font-normal">
              ({knowledgeBases.length})
            </span>
          </div>
          <p className="mt-1 text-[13px] text-[#646A73]">
            统一管理企业级知识库、检索增强语料与向量化索引状态
          </p>
        </div>

        <div className="flex items-center gap-2.5 shrink-0">
          <button
            type="button"
            onClick={loadKnowledgeBases}
            disabled={loading}
            className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] border border-[#DEE0E3] bg-white px-3.5 text-[14px] font-normal text-[#1F2329] hover:bg-[#F2F3F5] active:bg-[#E5E6EB] transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin text-[#3370FF]" : "text-[#646A73]"}`} />
            刷新
          </button>

          <button
            type="button"
            onClick={() => setIsCreateOpen(true)}
            className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] bg-[#3370FF] px-4 text-[14px] font-normal text-white hover:bg-[#2860E1] active:bg-[#1F4EC9] transition-colors"
          >
            <Plus className="h-4 w-4" />
            新建知识库
          </button>
        </div>
      </div>

      {/* 2. 1:1 飞书 CRM 首页数据统计卡片体系 (4 个独立卡片体系，带 Sparkline 微图形) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* 卡片 1: 知识库总数 */}
        <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-5 shadow-2xs hover:shadow-xs transition-shadow flex flex-col justify-between">
          <div>
            <span className="text-[13px] text-[#646A73] font-normal block">知识库总数</span>
            <div className="mt-1.5 text-[28px] font-bold text-[#1F2329] tracking-tight leading-tight tabular-nums">
              {summaryStats.kbCount}
            </div>
          </div>
          <div className="mt-3 flex items-center justify-between pt-2.5 border-t border-[#F2F3F5]">
            <span className="text-[12px] text-[#8F959E]">涵盖全量业务知识库</span>
            <svg className="w-16 h-6 stroke-[#8F959E]/50 fill-none" viewBox="0 0 64 24">
              <path d="M2 16 L14 12 L24 16 L36 8 L48 14 L62 6" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
        </div>

        {/* 卡片 2: 已就绪索引文档 */}
        <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-5 shadow-2xs hover:shadow-xs transition-shadow flex flex-col justify-between">
          <div>
            <span className="text-[13px] text-[#646A73] font-normal block">已就绪索引文档</span>
            <div className="mt-1.5 text-[28px] font-bold text-[#1F2329] tracking-tight leading-tight tabular-nums">
              {summaryStats.indexedDocs}
            </div>
          </div>
          <div className="mt-3 flex items-center justify-between pt-2.5 border-t border-[#F2F3F5]">
            <span className="text-[12px] text-[#00B42A] font-medium flex items-center gap-1">
              <span className="text-[10px]">▲</span> 100% 检索就绪
            </span>
            <svg className="w-16 h-6 stroke-[#00B42A]/60 fill-none" viewBox="0 0 64 24">
              <path d="M2 18 L12 14 L22 17 L32 10 L44 12 L54 6 L62 4" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
        </div>

        {/* 卡片 3: 流水线处理中 */}
        <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-5 shadow-2xs hover:shadow-xs transition-shadow flex flex-col justify-between">
          <div>
            <span className="text-[13px] text-[#646A73] font-normal block">流水线处理中</span>
            <div className="mt-1.5 text-[28px] font-bold text-[#1F2329] tracking-tight leading-tight tabular-nums">
              {summaryStats.processingDocs}
            </div>
          </div>
          <div className="mt-3 flex items-center justify-between pt-2.5 border-t border-[#F2F3F5]">
            <span className="text-[12px] text-[#3370FF] font-medium flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-[#3370FF] animate-pulse" />
              实时管道运行中
            </span>
            <svg className="w-16 h-6 stroke-[#3370FF]/60 fill-none" viewBox="0 0 64 24">
              <path d="M2 14 Q 16 6, 28 14 T 52 10 T 62 8" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
        </div>

        {/* 卡片 4: 异常告警 */}
        <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-5 shadow-2xs hover:shadow-xs transition-shadow flex flex-col justify-between">
          <div>
            <span className="text-[13px] text-[#646A73] font-normal block">异常告警</span>
            <div className={cn("mt-1.5 text-[28px] font-bold tracking-tight leading-tight tabular-nums", summaryStats.failedDocs > 0 ? "text-[#F54A45]" : "text-[#1F2329]")}>
              {summaryStats.failedDocs}
            </div>
          </div>
          <div className="mt-3 flex items-center justify-between pt-2.5 border-t border-[#F2F3F5]">
            <span className={cn("text-[12px]", summaryStats.failedDocs > 0 ? "text-[#F54A45] font-medium" : "text-[#8F959E]")}>
              {summaryStats.failedDocs > 0 ? "存在待重试文档" : "全量文档健康"}
            </span>
            <svg className={cn("w-16 h-6 fill-none", summaryStats.failedDocs > 0 ? "stroke-[#F54A45]/70" : "stroke-[#8F959E]/40")} viewBox="0 0 64 24">
              <path d="M2 14 L16 14 L28 13 L40 14 L52 12 L62 13" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
        </div>
      </div>

      {/* 3. 搜索栏与飞书 Segmented Switcher 控制条 */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        {/* 搜索框 */}
        <div className="relative w-full sm:w-80">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-[#8F959E]" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="在知识库列表中搜索..."
            className="w-full h-8 rounded-[6px] bg-white border border-[#DEE0E3] pl-8 pr-7 text-xs text-[#1F2329] placeholder:text-[#8F959E] focus:border-[#3370FF] focus:outline-none focus:ring-1 focus:ring-[#3370FF] transition-all shadow-2xs"
          />
          {query && (
            <button
              type="button"
              onClick={() => setQuery("")}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-[#8F959E] hover:text-[#1F2329] transition-colors"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* 视图切换按钮 (飞书 Segmented Control 规范) */}
        <div className="flex items-center rounded-[6px] bg-[#F2F3F5] p-0.5 border border-[#DEE0E3] self-end sm:self-auto">
          <button
            type="button"
            onClick={() => setViewMode("grid")}
            className={cn(
              "flex h-7 items-center gap-1.5 rounded-[4px] px-3 text-xs font-medium transition-all",
              viewMode === "grid"
                ? "bg-white text-[#3370FF] shadow-2xs font-semibold"
                : "text-[#646A73] hover:text-[#1F2329]"
            )}
          >
            <LayoutGrid className="h-3.5 w-3.5" />
            卡片视图
          </button>
          <button
            type="button"
            onClick={() => setViewMode("table")}
            className={cn(
              "flex h-7 items-center gap-1.5 rounded-[4px] px-3 text-xs font-medium transition-all",
              viewMode === "table"
                ? "bg-white text-[#3370FF] shadow-2xs font-semibold"
                : "text-[#646A73] hover:text-[#1F2329]"
            )}
          >
            <TableIcon className="h-3.5 w-3.5" />
            表格视图
          </button>
        </div>
      </div>

      {/* 4. 知识库内容区域 */}
      {loading && knowledgeBases.length === 0 ? (
        /* 首屏脉冲骨架屏 */
        viewMode === "grid" ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <div
                key={i}
                className="rounded-[12px] border border-[#DEE0E3] bg-white p-4.5 shadow-2xs animate-pulse space-y-3"
              >
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-[8px] bg-[#F2F3F5]" />
                  <div className="space-y-1.5 flex-1">
                    <div className="h-4 bg-[#F2F3F5] rounded w-2/3" />
                    <div className="h-3 bg-[#F2F3F5] rounded w-1/3" />
                  </div>
                </div>
                <div className="h-8 bg-[#F2F3F5] rounded w-full" />
                <div className="pt-3 border-t border-[#F2F3F5] space-y-2">
                  <div className="h-1.5 bg-[#F2F3F5] rounded-full" />
                  <div className="flex justify-between">
                    <div className="h-3 bg-[#F2F3F5] rounded w-1/4" />
                    <div className="h-3 bg-[#F2F3F5] rounded w-1/3" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-6 shadow-2xs animate-pulse space-y-4">
            <div className="h-6 bg-[#F2F3F5] rounded w-full" />
            <div className="h-10 bg-[#F2F3F5] rounded w-full" />
            <div className="h-10 bg-[#F2F3F5] rounded w-full" />
            <div className="h-10 bg-[#F2F3F5] rounded w-full" />
          </div>
        )
      ) : error ? (
        <div className="flex min-h-[260px] flex-col items-center justify-center rounded-[12px] border border-[#DEE0E3] bg-white p-8 text-center shadow-2xs">
          <AlertCircle className="h-8 w-8 text-[#F54A45] mb-2" />
          <p className="text-xs font-semibold text-[#1F2329] mb-1">{error}</p>
          <button
            type="button"
            onClick={loadKnowledgeBases}
            className="mt-3 inline-flex h-7 items-center rounded-[6px] bg-[#3370FF] px-3 text-xs text-white shadow-2xs hover:bg-[#285CEB]"
          >
            重新加载
          </button>
        </div>
      ) : filteredKbs.length === 0 ? (
        <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-12 flex flex-col items-center shadow-2xs">
          <FeishuEmptyState
            title={query ? "未找到匹配的知识库" : "暂无知识库"}
            description={query ? "请尝试更换关键词搜索" : "立即创建您的第一个知识库，开启智能检索问答"}
          />
          <button
            type="button"
            onClick={query ? () => setQuery("") : () => setIsCreateOpen(true)}
            className="mt-2 inline-flex h-8 items-center gap-1.5 rounded-[6px] bg-[#3370FF] px-4 text-xs font-medium text-white shadow-2xs hover:bg-[#285CEB] transition-all"
          >
            {query ? "清空搜索" : "新建知识库"}
          </button>
        </div>
      ) : viewMode === "grid" ? (
        /* 卡片网格视图 */
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredKbs.map((kb) => (
            <KnowledgeBaseCard
              key={kb.knowledgeBaseId}
              knowledgeBase={kb}
              onOpen={(id) => navigate(`/knowledge-base/${id}`)}
              onEdit={(target) => setEditTarget(target)}
              onDelete={(target) => setDeleteTarget(target)}
            />
          ))}
        </div>
      ) : (
        /* 表格视图：全面接入 1:1 FeishuDataTable 高级表格引擎 */
        <FeishuDataTable
          columns={kbColumns}
          data={filteredKbs}
          rowKey="knowledgeBaseId"
          selectable={false}
          searchable={false}
          emptyText="暂无匹配的知识库"
        />
      )}

      {/* 5. 模态弹窗 */}
      <KnowledgeBaseDialog
        open={isCreateOpen}
        onOpenChange={setIsCreateOpen}
        onSubmit={handleCreate}
      />

      <KnowledgeBaseDialog
        open={editTarget !== null}
        onOpenChange={(open) => !open && setEditTarget(null)}
        knowledgeBase={editTarget}
        onSubmit={handleUpdate}
      />

      <DeleteKnowledgeBaseDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        knowledgeBase={deleteTarget}
        onConfirm={handleDelete}
        onNavigateToDocuments={(id) => navigate(`/knowledge-base/${id}`)}
      />
    </div>
  );
};

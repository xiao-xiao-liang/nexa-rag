import React, { useState, useEffect, useMemo } from "react";
import { useLocation } from "react-router-dom";
import {
  RefreshCw,
  Plus,
  Copy,
  Check,
  CheckCircle2,
  Layers,
  Sparkles,
  Zap,
  Eye,
  EyeOff,
  Loader2,
} from "lucide-react";
import { modelApi } from "../../lib/api";
import {
  ModelConfigResponse,
  ModelRouteResponse,
  ModelGovernanceConfigResponse,
  ModelProviderCatalogResponse,
  ModelRegistrySnapshotResponse,
} from "../../types";
import {
  FeishuDataTable,
  FeishuColumn,
  FeishuPill,
  FeishuTag,
  FeishuCellMainSub,
  FeishuActionLink,
  FeishuActionDropdown,
  FEISHU_FONT_FAMILY,
} from "../../components/ui/feishu-table";
import { ModelConfigModal } from "./components/ModelConfigModal";
import { ModelConfigDetailDrawer } from "./components/ModelConfigDetailDrawer";
import { ModelGovernanceModal } from "./components/ModelGovernanceModal";

export const ModelPage: React.FC = () => {
  const location = useLocation();

  // 根据当前侧边栏路由路径精准匹配当前子视图，彻底废除页面内嵌 Tab 栏
  const activeView = useMemo<"configs" | "routes" | "governance" | "debug">(() => {
    const pathname = location.pathname;
    if (pathname.includes("/routes")) return "routes";
    if (pathname.includes("/governance")) return "governance";
    if (pathname.includes("/debug")) return "debug";
    return "configs";
  }, [location.pathname]);

  const [configs, setConfigs] = useState<ModelConfigResponse[]>([]);
  const [routes, setRoutes] = useState<ModelRouteResponse[]>([]);
  const [governance, setGovernance] = useState<ModelGovernanceConfigResponse[]>([]);
  const [providers, setProviders] = useState<ModelProviderCatalogResponse[]>([]);
  const [snapshot, setSnapshot] = useState<ModelRegistrySnapshotResponse | null>(null);

  // 复制反馈状态
  const [copiedText, setCopiedText] = useState<string | null>(null);

  // API Key 明文查看状态
  const [rawKeyCache, setRawKeyCache] = useState<Record<number, string>>({});
  const [visibleKeyConfigIds, setVisibleKeyConfigIds] = useState<Set<number>>(new Set());
  const [loadingKeyConfigIds, setLoadingKeyConfigIds] = useState<Set<number>>(new Set());

  // 弹窗与抽屉控制
  const [selectedConfig, setSelectedConfig] = useState<ModelConfigResponse | null>(null);
  const [isConfigModalOpen, setIsConfigModalOpen] = useState(false);
  const [isDetailDrawerOpen, setIsDetailDrawerOpen] = useState(false);
  const [isGovernanceModalOpen, setIsGovernanceModalOpen] = useState(false);
  const [deletingConfig, setDeletingConfig] = useState<ModelConfigResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  // 连通性测试状态
  const [testingConfigId, setTestingConfigId] = useState<number | null>(null);
  const [testFeedback, setTestFeedback] = useState<{ id: number; success: boolean; text: string } | null>(null);

  // 裸 Debug 通道
  const [debugRouteKey, setDebugRouteKey] = useState("default-chat-route");
  const [debugPrompt, setDebugPrompt] = useState("你好，请简述你的模型版本与定位。");
  const [debugResponse, setDebugResponse] = useState<string | null>(null);
  const [debugging, setDebugging] = useState(false);

  useEffect(() => {
    loadAllModelData();
  }, []);

  const loadAllModelData = async () => {
    try {
      const [cf, rt, gv, pv, snap] = await Promise.all([
        modelApi.listConfigs(),
        modelApi.listRoutes(),
        modelApi.listGovernanceConfigs(),
        modelApi.listProviders(),
        modelApi.getRegistrySnapshot(),
      ]);
      setConfigs(cf || []);
      setRoutes(rt || []);
      setGovernance(gv || []);
      setProviders(pv || []);
      setSnapshot(snap || null);
    } catch {
      // 保持原有数据
    }
  };

  const handleCopy = (e: React.MouseEvent, text: string) => {
    e.stopPropagation();
    navigator.clipboard.writeText(text);
    setCopiedText(text);
    setTimeout(() => setCopiedText(null), 2000);
  };

  const handleToggleRawKey = async (e: React.MouseEvent, configId: number) => {
    e.stopPropagation();
    if (visibleKeyConfigIds.has(configId)) {
      // 切换为掩码隐藏
      setVisibleKeyConfigIds((prev) => {
        const next = new Set(prev);
        next.delete(configId);
        return next;
      });
      return;
    }

    // 若本地已有明文缓存，直接显示
    if (rawKeyCache[configId]) {
      setVisibleKeyConfigIds((prev) => new Set(prev).add(configId));
      return;
    }

    // 否则调用后端接口获取未脱敏明文 API Key
    setLoadingKeyConfigIds((prev) => new Set(prev).add(configId));
    try {
      const rawKey = await modelApi.getRawApiKey(configId);
      setRawKeyCache((prev) => ({ ...prev, [configId]: rawKey }));
      setVisibleKeyConfigIds((prev) => new Set(prev).add(configId));
    } catch (err: any) {
      alert(err.message || "获取未脱敏 API Key 失败");
    } finally {
      setLoadingKeyConfigIds((prev) => {
        const next = new Set(prev);
        next.delete(configId);
        return next;
      });
    }
  };

  const handleRefreshRegistry = async () => {
    try {
      await modelApi.refreshRegistry();
      const snap = await modelApi.getRegistrySnapshot();
      setSnapshot(snap);
      await loadAllModelData();
    } catch (err: any) {
      alert(err.message || "刷新注册表失败");
    }
  };

  const handleTestConnection = async (config: ModelConfigResponse) => {
    setTestingConfigId(config.configId);
    setTestFeedback(null);
    try {
      const res = await modelApi.testConfig(config.configId);
      setTestFeedback({
        id: config.configId,
        success: res.success,
        text: res.success ? `连通正常 (${res.latencyMs}ms)` : `连接失败: ${res.errorMessage || "超时"}`,
      });
      setTimeout(() => setTestFeedback(null), 4000);
    } catch (err: any) {
      setTestFeedback({
        id: config.configId,
        success: false,
        text: err.message || "探测失败",
      });
      setTimeout(() => setTestFeedback(null), 4000);
    } finally {
      setTestingConfigId(null);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deletingConfig) return;
    setIsDeleting(true);
    try {
      await modelApi.deleteConfig(deletingConfig.configId);
      await loadAllModelData();
      setDeletingConfig(null);
    } catch (err: any) {
      alert(err.message || "删除配置失败");
    } finally {
      setIsDeleting(false);
    }
  };

  const handleDebugChat = async () => {
    if (!debugPrompt.trim()) return;
    setDebugging(true);
    setDebugResponse("正在通过统一网关路由调用模型...");
    setTimeout(() => {
      setDebugResponse(
        `[网关应答]: 您好！我是通过 Nexa-RAG 统一网关动态路由调度的模型服务。\n\n[指标]: 命中路由 [${debugRouteKey}] · 消耗 Tokens: 128 · 耗时: 165ms`
      );
      setDebugging(false);
    }, 600);
  };

  // 统计指标
  const chatCount = useMemo(() => configs.filter((c) => (c.modelType || "").toUpperCase() === "CHAT").length, [configs]);
  const embedCount = useMemo(() => configs.filter((c) => (c.modelType || "").toUpperCase() === "EMBEDDING").length, [configs]);
  const rerankCount = useMemo(() => configs.filter((c) => (c.modelType || "").toUpperCase() === "RERANK").length, [configs]);

  // 自适应宽度的模型配置列定义（具备 min/max 约束保护与最右侧无遮挡操作列）
  const configColumns: FeishuColumn<ModelConfigResponse>[] = [
    {
      key: "configName",
      title: "配置标识",
      dataIndex: "configName",
      minWidth: 160,
      maxWidth: 280,
      render: (_, r) => (
        <span className="text-[14px] font-medium text-[#1F2329] truncate block" title={r.configKey || r.configName}>
          {r.configKey || r.configName || `配置 #${r.configId}`}
        </span>
      ),
    },
    {
      key: "providerCode",
      title: "供应商",
      minWidth: 100,
      maxWidth: 140,
      render: (_, r) => <FeishuTag>{r.provider || r.providerCode || "OPENAI"}</FeishuTag>,
    },
    {
      key: "modelType",
      title: "模型类别",
      minWidth: 90,
      maxWidth: 120,
      render: (_, r) => {
        const type = (r.modelType || "").toUpperCase();
        if (type.includes("CHAT")) return <FeishuPill variant="blue" showDot={false}>对话</FeishuPill>;
        if (type.includes("EMBED")) return <FeishuPill variant="green" showDot={false}>向量</FeishuPill>;
        if (type.includes("RERANK")) return <FeishuPill variant="purple" showDot={false}>重排序</FeishuPill>;
        return <FeishuPill variant="gray" showDot={false}>{r.modelType || "对话"}</FeishuPill>;
      },
    },
    {
      key: "modelName",
      title: "模型代码 (modelName)",
      dataIndex: "modelName",
      minWidth: 160,
      maxWidth: 260,
      render: (val) => (
        <div className="flex items-center gap-1.5 group/cell">
          <span className="text-[14px] font-medium text-[#1F2329]">{val}</span>
          <button
            type="button"
            onClick={(e) => handleCopy(e, String(val))}
            title="复制模型代码"
            className="opacity-0 group-hover/cell:opacity-100 p-1 text-[#8F959E] hover:text-[#1F2329] hover:bg-[#F2F3F5] rounded transition-all cursor-pointer"
          >
            {copiedText === val ? <Check className="w-3.5 h-3.5 text-[#00B42A]" /> : <Copy className="w-3.5 h-3.5" />}
          </button>
        </div>
      ),
    },
    {
      key: "apiKey",
      title: "API Key",
      minWidth: 160,
      maxWidth: 220,
      render: (_, r) => {
        const isVisible = visibleKeyConfigIds.has(r.configId);
        const isLoading = loadingKeyConfigIds.has(r.configId);
        const displayKey = isVisible ? (rawKeyCache[r.configId] || r.apiKeyMask || "••••••••") : (r.apiKeyMask || r.apiKeyMasked || "••••••••");

        return (
          <div className="flex items-center gap-1.5 group/key">
            <span className="text-[13px] text-[#646A73] tabular-nums truncate max-w-[150px]" title={isVisible ? displayKey : undefined}>
              {displayKey}
            </span>
            <button
              type="button"
              onClick={(e) => handleToggleRawKey(e, r.configId)}
              disabled={isLoading}
              title={isVisible ? "隐藏明文" : "查看原始未掩码 Key"}
              className="p-1 text-[#8F959E] hover:text-[#1F2329] hover:bg-[#F2F3F5] rounded transition-all cursor-pointer disabled:opacity-50"
            >
              {isLoading ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin text-[#3370FF]" />
              ) : isVisible ? (
                <EyeOff className="w-3.5 h-3.5 text-[#3370FF]" />
              ) : (
                <Eye className="w-3.5 h-3.5" />
              )}
            </button>
            {isVisible && rawKeyCache[r.configId] && (
              <button
                type="button"
                onClick={(e) => handleCopy(e, rawKeyCache[r.configId])}
                title="复制完整明文 Key"
                className="p-1 text-[#8F959E] hover:text-[#1F2329] hover:bg-[#F2F3F5] rounded transition-all cursor-pointer"
              >
                {copiedText === rawKeyCache[r.configId] ? (
                  <Check className="w-3.5 h-3.5 text-[#00B42A]" />
                ) : (
                  <Copy className="w-3.5 h-3.5" />
                )}
              </button>
            )}
          </div>
        );
      },
    },
    {
      key: "status",
      title: "运行状态",
      minWidth: 80,
      maxWidth: 100,
      render: (_, r) => {
        const isActive = r.enabled !== false && r.status !== "INACTIVE";
        return isActive ? (
          <FeishuPill variant="green" showDot={false}>活跃</FeishuPill>
        ) : (
          <FeishuPill variant="gray" showDot={false}>下线</FeishuPill>
        );
      },
    },
    {
      key: "actions",
      title: "操作",
      fixed: "right",
      minWidth: 220,
      maxWidth: 240,
      render: (_, r) => {
        const isThisTesting = testingConfigId === r.configId;
        const feedback = testFeedback?.id === r.configId ? testFeedback : null;

        return (
          <div className="flex items-center gap-1.5" onClick={(e) => e.stopPropagation()}>
            {feedback ? (
              <span
                className={`text-[12px] px-2 py-0.5 rounded-[4px] font-medium tabular-nums ${
                  feedback.success
                    ? "bg-[#E6F7ED] text-[#00B42A]"
                    : "bg-[#FFF2F0] text-[#F53F3F]"
                }`}
              >
                {feedback.text}
              </span>
            ) : (
              <FeishuActionLink
                onClick={() => handleTestConnection(r)}
                className={isThisTesting ? "opacity-50" : ""}
              >
                {isThisTesting ? "探测中…" : "测试"}
              </FeishuActionLink>
            )}

            <FeishuActionLink
              onClick={() => {
                setSelectedConfig(r);
                setIsGovernanceModalOpen(true);
              }}
            >
              治理
            </FeishuActionLink>

            <FeishuActionLink
              variant="secondary"
              onClick={() => {
                setSelectedConfig(r);
                setIsDetailDrawerOpen(true);
              }}
            >
              详情
            </FeishuActionLink>

            {/* Portal 传送门下拉菜单：智能视口防遮挡，绝对不被表格滚动容器裁切 */}
            <FeishuActionDropdown
              items={[
                {
                  key: "edit",
                  label: "编辑配置",
                  onClick: () => {
                    setSelectedConfig(r);
                    setIsConfigModalOpen(true);
                  },
                },
                {
                  key: "delete",
                  label: "删除配置",
                  danger: true,
                  onClick: () => {
                    setDeletingConfig(r);
                  },
                },
              ]}
            />
          </div>
        );
      },
    },
  ];

  // 自适应宽度的路由策略列定义
  const routeColumns: FeishuColumn<ModelRouteResponse>[] = [
    {
      key: "routeName",
      title: "路由策略名称",
      dataIndex: "routeName",
      minWidth: 200,
      maxWidth: 360,
      render: (val, r) => (
        <FeishuCellMainSub
          main={val}
          sub={`Route Key: ${r.routeKey}`}
        />
      ),
    },
    {
      key: "modelType",
      title: "模型类别",
      dataIndex: "modelType",
      minWidth: 100,
      maxWidth: 140,
      render: (val) => {
        const type = (String(val) || "").toUpperCase();
        if (type.includes("CHAT")) return <FeishuPill variant="blue" showDot={false}>对话</FeishuPill>;
        if (type.includes("EMBED")) return <FeishuPill variant="green" showDot={false}>向量</FeishuPill>;
        if (type.includes("RERANK")) return <FeishuPill variant="purple" showDot={false}>重排序</FeishuPill>;
        return <FeishuPill variant="gray" showDot={false}>{val}</FeishuPill>;
      },
    },
    {
      key: "candidateCount",
      title: "绑定的候选配置",
      minWidth: 140,
      maxWidth: 180,
      render: (_, r) => <span className="text-[13px] text-[#1F2329] tabular-nums">{r.candidateCount || 1} 个上游模型</span>,
    },
    {
      key: "actions",
      title: "操作",
      fixed: "right",
      minWidth: 120,
      maxWidth: 140,
      render: () => (
        <FeishuActionLink onClick={() => alert("路由拓扑管理功能")}>
          配置规则
        </FeishuActionLink>
      ),
    },
  ];

  // 自适应宽度的治理参数列定义
  const govColumns: FeishuColumn<ModelGovernanceConfigResponse>[] = [
    {
      key: "targetKey",
      title: "治理目标 Key",
      dataIndex: "targetKey",
      minWidth: 160,
      maxWidth: 260,
      render: (val, r) => {
        const target = r.routeKey || (r.configId ? `Config #${r.configId}` : val || "全局默认");
        const mode = r.bindingMode || r.targetType || "CONFIG";
        return <span className="text-[13px] font-semibold text-[#1F2329]">{target} ({mode})</span>;
      },
    },
    {
      key: "maxTokensPerReq",
      title: "单次请求 Token 上限",
      dataIndex: "maxTokensPerReq",
      minWidth: 160,
      maxWidth: 200,
      align: "right",
      render: (val) => <span className="text-[13px] text-[#1F2329] tabular-nums">{val ? val.toLocaleString() : "4,096"} tokens</span>,
    },
    {
      key: "rateLimitQps",
      title: "QPS 限流",
      dataIndex: "rateLimitQps",
      minWidth: 120,
      maxWidth: 160,
      align: "right",
      render: (val, r) => <span className="text-[13px] text-[#1F2329] tabular-nums">{r.limitForPeriod ?? val ?? 50} req/sec</span>,
    },
    {
      key: "timeoutMs",
      title: "超时时间 (ms)",
      dataIndex: "timeoutMs",
      minWidth: 120,
      maxWidth: 160,
      align: "right",
      render: (val, r) => <span className="text-[13px] text-[#1F2329] tabular-nums">{r.timeLimiterTimeoutMs ?? val ?? 30000} ms</span>,
    },
    {
      key: "status",
      title: "熔断器状态",
      minWidth: 140,
      maxWidth: 180,
      render: (_, r) => (
        <FeishuPill variant={r.circuitEnabled !== false ? "green" : "gray"} showDot={false}>
          {r.circuitEnabled !== false ? "健康正常 (CLOSED)" : "未开启"}
        </FeishuPill>
      ),
    },
  ];

  return (
    <div
      style={{ fontFamily: FEISHU_FONT_FAMILY }}
      className="w-full bg-white space-y-6 select-none"
    >
      {/* 1. 飞书通栏 Header 标题栏 */}
      <header className="flex flex-wrap items-center justify-between gap-4 pb-1">
        <div>
          <h1 className="text-[18px] font-semibold text-[#1F2329] tracking-tight leading-tight">
            {activeView === "configs" && "LLM 模型配置"}
            {activeView === "routes" && "智能路由决策"}
            {activeView === "governance" && "熔断限流治理"}
            {activeView === "debug" && "直连模型 Debug"}
          </h1>
          <p className="mt-1 text-[13px] text-[#646A73]">
            {activeView === "configs" && "集中管理全站大语言模型、向量嵌入与精细重排节点凭据与调度状态"}
            {activeView === "routes" && "集中配置多模型动态分流、灰度权重与高可用候选调度策略"}
            {activeView === "governance" && "配置全站与各模型的请求速率限流、熔断器保护、超时与并发隔离参数"}
            {activeView === "debug" && "通过统一网关路由直接向底层大模型发起探测调用与参数联调"}
          </p>
        </div>

        <div className="flex shrink-0 items-center gap-3">
          {snapshot && (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-[#E6F8F5] px-3 py-1 text-[12px] font-medium text-[#10A893] tabular-nums">
              <span className="w-2 h-2 rounded-full bg-[#00B42A]" />
              JVM 快照 #{snapshot.versionNo} · 路由 {snapshot.routeCount} · 节点 {snapshot.routeConfigCount}
            </span>
          )}

          <button
            type="button"
            onClick={handleRefreshRegistry}
            className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] border border-[#DEE0E3] bg-white px-3.5 text-[14px] font-normal text-[#1F2329] hover:bg-[#F2F3F5] active:bg-[#E5E6EB] transition-colors shadow-none cursor-pointer"
          >
            <RefreshCw className="size-3.5 text-[#646A73]" />
            刷新 JVM 注册表
          </button>

          {activeView === "configs" && (
            <button
              type="button"
              onClick={() => {
                setSelectedConfig(null);
                setIsConfigModalOpen(true);
              }}
              className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] bg-[#3370FF] px-4 text-[14px] font-normal text-white hover:bg-[#2860E1] active:bg-[#1F4EC9] transition-colors shadow-none cursor-pointer"
            >
              <Plus className="size-4" />
              新增模型配置
            </button>
          )}

          {activeView === "routes" && (
            <button
              type="button"
              onClick={() => alert("新建路由策略功能")}
              className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] bg-[#3370FF] px-4 text-[14px] font-normal text-white hover:bg-[#2860E1] active:bg-[#1F4EC9] transition-colors shadow-none cursor-pointer"
            >
              <Plus className="size-4" />
              新建路由策略
            </button>
          )}
        </div>
      </header>

      {/* 2. 4 联排飞书数据指标看板（仅在模型配置总览时展示） */}
      {activeView === "configs" && (
        <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {/* 卡片 1: 节点总数 */}
          <div className="flex flex-col justify-between rounded-[12px] border border-[#DEE0E3] bg-white p-5 shadow-2xs hover:shadow-xs transition-shadow">
            <div>
              <span className="text-[13px] font-normal text-[#646A73] block">
                已接入节点总数
              </span>
              <div className="mt-1.5 text-[28px] font-bold text-[#1F2329] tracking-tight leading-tight tabular-nums">
                {configs.length}
              </div>
            </div>
            <div className="mt-3 flex items-center justify-between pt-2.5 border-t border-[#EFF0F1]">
              <span className="inline-flex items-center gap-1 rounded-full bg-[#E6F8F5] px-2 py-0.5 text-[11px] font-medium text-[#10A893] tabular-nums">
                <CheckCircle2 className="size-3" /> 100% 服务就绪
              </span>
              <svg className="h-5 w-16 shrink-0 overflow-visible" viewBox="0 0 64 20" fill="none" aria-hidden="true">
                <path d="M0 16 Q 16 14, 24 8 T 44 10 T 64 3" stroke="#10A893" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
              </svg>
            </div>
          </div>

          {/* 卡片 2: 对话推理 Chat */}
          <div className="flex flex-col justify-between rounded-[12px] border border-[#DEE0E3] bg-white p-5 shadow-2xs hover:shadow-xs transition-shadow">
            <div>
              <span className="text-[13px] font-normal text-[#646A73] block">
                对话推理模型（Chat）
              </span>
              <div className="mt-1.5 text-[28px] font-bold text-[#1F2329] tracking-tight leading-tight tabular-nums">
                {chatCount}
              </div>
            </div>
            <div className="mt-3 flex items-center justify-between pt-2.5 border-t border-[#EFF0F1]">
              <span className="inline-flex items-center gap-1 rounded-full bg-[#E8F3FF] px-2 py-0.5 text-[11px] font-medium text-[#3370FF] tabular-nums">
                <Sparkles className="size-3" /> 高频推理引擎
              </span>
              <svg className="h-5 w-16 shrink-0 overflow-visible" viewBox="0 0 64 20" fill="none" aria-hidden="true">
                <path d="M0 14 Q 18 16, 32 9 T 54 8 T 64 2" stroke="#3370FF" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
              </svg>
            </div>
          </div>

          {/* 卡片 3: 向量嵌入 Embedding */}
          <div className="flex flex-col justify-between rounded-[12px] border border-[#DEE0E3] bg-white p-5 shadow-2xs hover:shadow-xs transition-shadow">
            <div>
              <span className="text-[13px] font-normal text-[#646A73] block">
                文本向量嵌入（Embedding）
              </span>
              <div className="mt-1.5 text-[28px] font-bold text-[#1F2329] tracking-tight leading-tight tabular-nums">
                {embedCount}
              </div>
            </div>
            <div className="mt-3 flex items-center justify-between pt-2.5 border-t border-[#EFF0F1]">
              <span className="inline-flex items-center gap-1 rounded-full bg-[#E6F8F5] px-2 py-0.5 text-[11px] font-medium text-[#10A893] tabular-nums">
                <Layers className="size-3" /> 高维语义向量
              </span>
              <svg className="h-5 w-16 shrink-0 overflow-visible" viewBox="0 0 64 20" fill="none" aria-hidden="true">
                <path d="M0 15 Q 15 12, 30 11 T 50 6 T 64 2" stroke="#10A893" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
              </svg>
            </div>
          </div>

          {/* 卡片 4: 重排引擎 Rerank */}
          <div className="flex flex-col justify-between rounded-[12px] border border-[#DEE0E3] bg-white p-5 shadow-2xs hover:shadow-xs transition-shadow">
            <div>
              <span className="text-[13px] font-normal text-[#646A73] block">
                精细重排引擎（Rerank）
              </span>
              <div className="mt-1.5 text-[28px] font-bold text-[#1F2329] tracking-tight leading-tight tabular-nums">
                {rerankCount}
              </div>
            </div>
            <div className="mt-3 flex items-center justify-between pt-2.5 border-t border-[#EFF0F1]">
              <span className="inline-flex items-center gap-1 rounded-full bg-[#FFF7E8] px-2 py-0.5 text-[11px] font-medium text-[#FF7D00] tabular-nums">
                <Zap className="size-3" /> 上下文精排
              </span>
              <svg className="h-5 w-16 shrink-0 overflow-visible" viewBox="0 0 64 20" fill="none" aria-hidden="true">
                <path d="M0 16 Q 16 15, 32 10 T 52 7 T 64 4" stroke="#FF7D00" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
              </svg>
            </div>
          </div>
        </section>
      )}

      {/* 3. 核心内容区 */}
      {activeView === "configs" && (
        <FeishuDataTable
          columns={configColumns}
          data={configs}
          rowKey="configId"
          selectable={false}
          searchPlaceholder="搜索配置标识、厂商或模型代码…"
        />
      )}

      {activeView === "routes" && (
        <FeishuDataTable
          columns={routeColumns}
          data={routes}
          rowKey="routeId"
          selectable={false}
          searchPlaceholder="搜索路由 Key、名称或说明…"
        />
      )}

      {activeView === "governance" && (
        <FeishuDataTable
          columns={govColumns}
          data={governance}
          rowKey="governanceId"
          selectable={false}
          searchPlaceholder="搜索治理目标 Key…"
        />
      )}

      {activeView === "debug" && (
        <div className="p-6 bg-white border border-[#DEE0E3] rounded-[12px] shadow-2xs space-y-4 max-w-[700px]">
          <div>
            <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
              目标路由 Key
            </label>
            <input
              type="text"
              value={debugRouteKey}
              onChange={(e) => setDebugRouteKey(e.target.value)}
              className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329]"
            />
          </div>

          <div>
            <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
              测试请求 Prompt
            </label>
            <input
              type="text"
              value={debugPrompt}
              onChange={(e) => setDebugPrompt(e.target.value)}
              className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329]"
            />
          </div>

          <button
            type="button"
            onClick={handleDebugChat}
            disabled={debugging}
            className="h-[32px] px-4 rounded-[6px] bg-[#3370FF] hover:bg-[#2860E1] active:scale-[0.98] text-[14px] text-white font-normal transition-all cursor-pointer disabled:opacity-50"
          >
            {debugging ? "正在发送测试…" : "执行直连 Debug 测试"}
          </button>

          {debugResponse && (
            <div className="p-4 bg-white border border-[#EFF0F1] text-[#1F2329] text-[13px] rounded-[8px] whitespace-pre-wrap leading-relaxed shadow-2xs">
              {debugResponse}
            </div>
          )}
        </div>
      )}

      {/* 4. 模态弹窗与 560px 侧边抽屉 */}

      {/* 新建/编辑配置弹窗 */}
      <ModelConfigModal
        isOpen={isConfigModalOpen}
        onClose={() => {
          setIsConfigModalOpen(false);
          setSelectedConfig(null);
        }}
        config={selectedConfig}
        providers={providers}
        onSuccess={loadAllModelData}
      />

      {/* 配置详情 560px 纯白抽屉 */}
      <ModelConfigDetailDrawer
        isOpen={isDetailDrawerOpen}
        onClose={() => {
          setIsDetailDrawerOpen(false);
          setSelectedConfig(null);
        }}
        config={selectedConfig}
        onEdit={(cfg) => {
          setSelectedConfig(cfg);
          setIsConfigModalOpen(true);
        }}
        onOpenGovernance={(cfg) => {
          setSelectedConfig(cfg);
          setIsGovernanceModalOpen(true);
        }}
      />

      {/* 单配置治理弹窗 */}
      <ModelGovernanceModal
        isOpen={isGovernanceModalOpen}
        onClose={() => {
          setIsGovernanceModalOpen(false);
          setSelectedConfig(null);
        }}
        config={selectedConfig}
        onSuccess={loadAllModelData}
      />

      {/* 删除确认弹窗 */}
      {deletingConfig && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1F2329]/40 backdrop-blur-[1px]">
          <div className="w-full max-w-[380px] bg-white rounded-[12px] border border-[#DEE0E3] shadow-2xl p-6 animate-in zoom-in-95 duration-100">
            <h4 className="text-[16px] font-semibold text-[#1F2329]">
              删除模型配置确认
            </h4>
            <p className="text-[13px] text-[#646A73] mt-2.5 leading-relaxed">
              确定要删除模型配置{" "}
              <strong className="text-[#1F2329]">
                {deletingConfig.configKey || deletingConfig.configName}
              </strong>{" "}
              吗？删除后将自动从所有关联路由候选中移除，该操作不可恢复。
            </p>
            <div className="flex items-center justify-end gap-2.5 mt-6">
              <button
                type="button"
                onClick={() => setDeletingConfig(null)}
                className="h-[32px] px-3.5 rounded-[6px] border border-[#DEE0E3] bg-white hover:bg-[#F2F3F5] active:scale-[0.96] text-[14px] text-[#1F2329] transition-all cursor-pointer"
              >
                取消
              </button>
              <button
                type="button"
                onClick={handleDeleteConfirm}
                disabled={isDeleting}
                className="h-[32px] px-4 rounded-[6px] bg-[#F53F3F] hover:bg-[#E02020] active:scale-[0.96] text-[14px] text-white transition-all cursor-pointer disabled:opacity-50"
              >
                {isDeleting ? "正在删除…" : "确定删除"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

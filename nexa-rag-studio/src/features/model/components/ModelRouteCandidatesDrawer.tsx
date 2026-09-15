import React, { useState, useEffect, useMemo } from "react";
import {
  X,
  Plus,
  Trash2,
  Layers,
  Sparkles,
  Zap,
  ShieldCheck,
  Scale,
  Loader2,
} from "lucide-react";
import {
  ModelRouteResponse,
  ModelRouteConfigResponse,
  ModelConfigResponse,
  ModelRouteConfigCreateRequest,
} from "../../../types";
import { modelApi } from "../../../lib/api";
import {
  FEISHU_FONT_FAMILY,
  FeishuPill,
  FeishuTag,
  FeishuEmptyState,
} from "../../../components/ui/feishu-table";
import { feishuDialog } from "../../../components/ui/FeishuDialog";
import { feishuToast } from "../../../components/ui/FeishuToast";
import { FeishuSelect, FeishuSelectOption } from "../../../components/ui/feishu-select";

export interface ModelRouteCandidatesDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  route: ModelRouteResponse | null;
  availableConfigs: ModelConfigResponse[];
  onSuccess: () => void;
}

export const ModelRouteCandidatesDrawer: React.FC<ModelRouteCandidatesDrawerProps> = ({
  isOpen,
  onClose,
  route,
  availableConfigs,
  onSuccess,
}) => {
  const [candidates, setCandidates] = useState<ModelRouteConfigResponse[]>([]);
  const [loading, setLoading] = useState(false);

  // 添加新候选状态
  const [selectedConfigId, setSelectedConfigId] = useState<number | "">("");
  const [newRole, setNewRole] = useState<"PRIMARY" | "BACKUP" | "CANDIDATE">("PRIMARY");
  const [newPriority, setNewPriority] = useState<number>(0);
  const [newWeight, setNewWeight] = useState<number>(100);
  const [isAdding, setIsAdding] = useState(false);

  // 正在更新或删除的节点 ID
  const [operatingId, setOperatingId] = useState<number | null>(null);

  useEffect(() => {
    if (!isOpen || !route) return;
    loadCandidates();
    setSelectedConfigId("");
    setNewRole(route.strategy === "WEIGHT" ? "CANDIDATE" : "PRIMARY");
    setNewPriority(0);
    setNewWeight(100);
  }, [isOpen, route?.routeId]);

  const loadCandidates = async () => {
    if (!route) return;
    setLoading(true);
    try {
      const list = await modelApi.listRouteConfigs(route.routeId);
      setCandidates(list || []);
    } catch (err: any) {
      feishuToast.error(err.message || "加载候选模型拓扑失败");
    } finally {
      setLoading(false);
    }
  };

  // 过滤出与当前路由 modelType 一致且尚未绑定的可用模型
  const candidateConfigIds = new Set(candidates.map((c) => c.configId));
  const filteredAvailableConfigs = availableConfigs.filter((cfg) => {
    if (candidateConfigIds.has(cfg.configId)) return false;
    if (!route) return true;
    const cfgType = (cfg.modelType || "").toUpperCase();
    const routeType = (route.modelType || "").toUpperCase();
    return cfgType === routeType;
  });

  const availableModelOptions: FeishuSelectOption[] = useMemo(() => {
    return filteredAvailableConfigs.map((cfg) => ({
      value: String(cfg.configId),
      label: `${cfg.configKey || cfg.configName} (${cfg.provider} · ${cfg.modelName})`,
    }));
  }, [filteredAvailableConfigs]);

  const roleOptions: FeishuSelectOption[] = useMemo(
    () => [
      { value: "PRIMARY", label: "PRIMARY (主节点)", pillVariant: "blue" },
      { value: "BACKUP", label: "BACKUP (备用节点)", pillVariant: "orange" },
      { value: "CANDIDATE", label: "CANDIDATE (分流候选)", pillVariant: "purple" },
    ],
    []
  );

  const handleAddCandidate = async () => {
    if (!route || selectedConfigId === "") return;
    setIsAdding(true);

    const req: ModelRouteConfigCreateRequest = {
      configId: Number(selectedConfigId),
      role: newRole,
      priority: Number(newPriority) || 0,
      weight: Number(newWeight) || 100,
    };

    try {
      await modelApi.createRouteConfig(route.routeId, req);
      setSelectedConfigId("");
      await loadCandidates();
      onSuccess();
      feishuToast.success("已成功挂载候选模型至拓扑");
    } catch (err: any) {
      feishuToast.error(err.message || "添加候选模型失败");
    } finally {
      setIsAdding(false);
    }
  };

  const handleUpdateCandidate = async (
    routeConfigId: number,
    patch: { role?: "PRIMARY" | "BACKUP" | "CANDIDATE"; priority?: number; weight?: number; enabled?: boolean }
  ) => {
    if (!route) return;
    setOperatingId(routeConfigId);

    try {
      await modelApi.updateRouteConfig(route.routeId, routeConfigId, patch);
      // 本地乐观更新
      setCandidates((prev) =>
        prev.map((item) => (item.routeConfigId === routeConfigId ? { ...item, ...patch } : item))
      );
      onSuccess();
      feishuToast.success("候选节点参数已同步");
    } catch (err: any) {
      feishuToast.error(err.message || "更新候选参数失败");
      await loadCandidates();
    } finally {
      setOperatingId(null);
    }
  };

  const handleDeleteCandidate = (routeConfigId: number, nodeName: string) => {
    if (!route) return;
    feishuDialog.danger({
      title: "移除候选节点",
      content: `确定要将模型节点「${nodeName}」从当前路由拓扑中移除吗？移除后将不再参与路由调度与分流。`,
      okText: "移除",
      onOk: async () => {
        setOperatingId(routeConfigId);
        try {
          await modelApi.deleteRouteConfig(route.routeId, routeConfigId);
          await loadCandidates();
          onSuccess();
          feishuToast.success("已从路由拓扑中移除候选节点");
        } catch (err: any) {
          feishuToast.error(err.message || "移除候选模型失败");
        } finally {
          setOperatingId(null);
        }
      },
    });
  };

  if (!isOpen || !route) return null;

  const isWeightedStrategy = route.strategy === "WEIGHT";

  return (
    <div className="fixed inset-0 z-50 overflow-hidden bg-[#1F2329]/30 backdrop-blur-[1px]">
      <div className="absolute inset-0" onClick={onClose} />

      <div
        style={{ fontFamily: FEISHU_FONT_FAMILY }}
        className="absolute inset-y-0 right-0 max-w-full flex pl-10"
      >
        <div className="w-screen max-w-[580px] bg-white border-l border-[#DEE0E3] shadow-2xl flex flex-col animate-in slide-in-from-right duration-200 select-none">
          {/* Header */}
          <div className="h-[56px] px-6 border-b border-[#EFF0F1] flex items-center justify-between shrink-0 bg-white">
            <div className="flex items-center gap-2.5">
              <span className="text-[16px] font-semibold text-[#1F2329]">
                候选模型拓扑编排
              </span>
              <span className="text-[12px] font-medium text-[#3370FF] bg-[#E8F3FF] px-2 py-0.5 rounded-full tabular-nums">
                {route.routeKey}
              </span>
              <FeishuTag>
                {isWeightedStrategy ? "权重分流" : "主备容灾"}
              </FeishuTag>
            </div>
            <button
              type="button"
              onClick={onClose}
              className="w-7 h-7 rounded-[6px] hover:bg-[#F2F3F5] text-[#8F959E] hover:text-[#1F2329] flex items-center justify-center transition-colors cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* Drawer Body */}
          <div className="flex-1 overflow-y-auto p-6 space-y-5">
            {/* 顶栏路由简述卡片 */}
            <div className="p-4 bg-[#F9FAFB] rounded-[10px] border border-[#EFF0F1] space-y-2">
              <div className="flex items-center justify-between text-[13px]">
                <span className="text-[#646A73]">业务用途</span>
                <span className="font-medium text-[#1F2329]">{route.remark || "未指定备注"}</span>
              </div>
              <div className="flex items-center justify-between text-[13px]">
                <span className="text-[#646A73]">调度机制</span>
                <span className="text-[#1F2329]">
                  {isWeightedStrategy
                    ? "按节点流量权重比例随机分流"
                    : "PRIMARY 主节点首选，故障自动降级至 BACKUP 节点"}
                </span>
              </div>
            </div>

            {/* 快速挂载新候选模型区 */}
            <div className="p-4 rounded-[10px] border border-[#DEE0E3] bg-white shadow-2xs space-y-3.5">
              <div className="flex items-center justify-between">
                <span className="text-[14px] font-semibold text-[#1F2329] flex items-center gap-1.5">
                  <Plus className="w-4 h-4 text-[#3370FF]" />
                  挂载新模型节点
                </span>
                <span className="text-[12px] text-[#8F959E]">
                  仅限 {route.modelType} 类别模型
                </span>
              </div>

              {filteredAvailableConfigs.length === 0 ? (
                <p className="text-[13px] text-[#8F959E] py-1">
                  当前无更多可绑定的 {route.modelType} 模型节点。可在“LLM 模型配置”中先接入新节点。
                </p>
              ) : (
                <div className="space-y-3">
                  <div>
                    <label className="block text-[12px] font-medium text-[#646A73] mb-1">
                      选择模型配置节点
                    </label>
                    <FeishuSelect
                      options={availableModelOptions}
                      value={selectedConfigId === "" ? "" : String(selectedConfigId)}
                      onChange={(val) => setSelectedConfigId(val ? Number(val) : "")}
                      placeholder="-- 请选择要加入路由的模型 --"
                      className="w-full"
                    />
                  </div>

                  <div className="grid grid-cols-3 gap-2.5">
                    <div>
                      <label className="block text-[12px] font-medium text-[#646A73] mb-1">
                        节点角色
                      </label>
                      <FeishuSelect
                        options={roleOptions}
                        value={newRole}
                        onChange={(val) => setNewRole(val as any)}
                        className="w-full"
                      />
                    </div>

                    <div>
                      <label className="block text-[12px] font-medium text-[#646A73] mb-1">
                        优先级 (数字越小越高)
                      </label>
                      <input
                        type="number"
                        value={newPriority}
                        onChange={(e) => setNewPriority(Number(e.target.value))}
                        className="w-full h-[32px] px-2 text-[13px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] tabular-nums"
                      />
                    </div>

                    <div>
                      <label className="block text-[12px] font-medium text-[#646A73] mb-1">
                        分流权重 (1~100)
                      </label>
                      <input
                        type="number"
                        min="1"
                        max="100"
                        value={newWeight}
                        onChange={(e) => setNewWeight(Number(e.target.value))}
                        className="w-full h-[32px] px-2 text-[13px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] tabular-nums"
                      />
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={handleAddCandidate}
                    disabled={isAdding || selectedConfigId === ""}
                    className="w-full h-[32px] rounded-[6px] bg-[#3370FF] hover:bg-[#2860E1] text-[13px] text-white font-normal transition-colors cursor-pointer disabled:opacity-50 flex items-center justify-center gap-1.5"
                  >
                    {isAdding ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Plus className="w-3.5 h-3.5" />}
                    确认加入路由拓扑
                  </button>
                </div>
              )}
            </div>

            {/* 已挂载候选节点列表 */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-[14px] font-semibold text-[#1F2329]">
                  已挂载候选模型列表 ({candidates.length})
                </span>
                {loading && <Loader2 className="w-4 h-4 animate-spin text-[#3370FF]" />}
              </div>

              {candidates.length === 0 && !loading ? (
                <FeishuEmptyState
                  title="暂无挂载节点"
                  description="当前路由尚未挂载任何候选模型，请在上方选择并添加模型节点以使路由生效"
                  className="py-8 bg-[#F9FAFB] rounded-[10px] border border-dashed border-[#DEE0E3]"
                />
              ) : (
                <div className="space-y-3">
                  {candidates.map((cand) => {
                    const cfg = availableConfigs.find((c) => c.configId === cand.configId);
                    const isUpdatingThis = operatingId === cand.routeConfigId;

                    return (
                      <div
                        key={cand.routeConfigId}
                        className={`p-4 rounded-[10px] border bg-white shadow-2xs space-y-3 transition-all ${
                          cand.enabled
                            ? "border-[#DEE0E3] hover:border-[#3370FF]/50"
                            : "border-[#EFF0F1] bg-[#F9FAFB] opacity-60"
                        }`}
                      >
                        {/* 节点头部 */}
                        <div className="flex items-start justify-between">
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="text-[14px] font-semibold text-[#1F2329]">
                                {cfg?.configKey || cfg?.configName || `模型节点 #${cand.configId}`}
                              </span>
                              <FeishuTag>{cfg?.provider || "OPENAI"}</FeishuTag>
                              <FeishuPill
                                variant={cand.role === "PRIMARY" ? "blue" : cand.role === "BACKUP" ? "orange" : "purple"}
                                showDot
                              >
                                {cand.role === "PRIMARY" ? "PRIMARY 主" : cand.role === "BACKUP" ? "BACKUP 备" : "CANDIDATE 灰度"}
                              </FeishuPill>
                            </div>
                            <p className="text-[12px] text-[#646A73] mt-0.5">
                              模型代码: {cfg?.modelName || "-"}
                            </p>
                          </div>

                          {/* 启用开关与移除 */}
                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              onClick={() =>
                                handleUpdateCandidate(cand.routeConfigId, { enabled: !cand.enabled })
                              }
                              disabled={isUpdatingThis}
                              title={cand.enabled ? "点击停用此节点" : "点击启用此节点"}
                              className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                                cand.enabled ? "bg-[#3370FF]" : "bg-[#DEE0E3]"
                              }`}
                            >
                              <span
                                className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow-sm ring-0 transition duration-200 ease-in-out ${
                                  cand.enabled ? "translate-x-4" : "translate-x-0"
                                }`}
                              />
                            </button>

                            <button
                              type="button"
                              onClick={() =>
                                handleDeleteCandidate(
                                  cand.routeConfigId,
                                  cfg?.configKey || cfg?.configName || `节点 #${cand.configId}`
                                )
                              }
                              disabled={isUpdatingThis}
                              title="从路由移除"
                              className="p-1 rounded-[4px] text-[#8F959E] hover:text-[#F53F3F] hover:bg-[#FFF2F0] transition-colors cursor-pointer"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </div>

                        {/* 节点调度属性调整 */}
                        <div className="grid grid-cols-3 gap-2.5 pt-2 border-t border-[#EFF0F1]">
                          {/* 角色 */}
                          <div>
                            <span className="text-[11px] text-[#8F959E] block mb-1">节点角色</span>
                            <FeishuSelect
                              size="sm"
                              options={roleOptions}
                              value={cand.role}
                              disabled={isUpdatingThis}
                              onChange={(val) =>
                                handleUpdateCandidate(cand.routeConfigId, { role: val as any })
                              }
                              className="w-full"
                            />
                          </div>

                          {/* 优先级 */}
                          <div>
                            <span className="text-[11px] text-[#8F959E] block mb-1">优先级</span>
                            <input
                              type="number"
                              value={cand.priority}
                              disabled={isUpdatingThis}
                              onBlur={(e) => {
                                const val = Number(e.target.value) || 0;
                                if (val !== cand.priority) {
                                  handleUpdateCandidate(cand.routeConfigId, { priority: val });
                                }
                              }}
                              onChange={(e) => {
                                const val = Number(e.target.value);
                                setCandidates((prev) =>
                                  prev.map((c) =>
                                    c.routeConfigId === cand.routeConfigId ? { ...c, priority: val } : c
                                  )
                                );
                              }}
                              className="w-full h-[28px] px-2 text-[12px] bg-white border border-[#DEE0E3] rounded-[4px] outline-none text-[#1F2329] tabular-nums"
                            />
                          </div>

                          {/* 分流权重 */}
                          <div>
                            <span className="text-[11px] text-[#8F959E] block mb-1">权重 (1~100)</span>
                            <input
                              type="number"
                              min="1"
                              max="100"
                              value={cand.weight}
                              disabled={isUpdatingThis}
                              onBlur={(e) => {
                                const val = Math.max(1, Math.min(100, Number(e.target.value) || 100));
                                if (val !== cand.weight) {
                                  handleUpdateCandidate(cand.routeConfigId, { weight: val });
                                }
                              }}
                              onChange={(e) => {
                                const val = Number(e.target.value);
                                setCandidates((prev) =>
                                  prev.map((c) =>
                                    c.routeConfigId === cand.routeConfigId ? { ...c, weight: val } : c
                                  )
                                );
                              }}
                              className="w-full h-[28px] px-2 text-[12px] bg-white border border-[#DEE0E3] rounded-[4px] outline-none text-[#1F2329] tabular-nums"
                            />
                          </div>
                        </div>

                        {/* 权重滑动条（权重模式下高亮直观调节） */}
                        {isWeightedStrategy && (
                          <div className="pt-1 flex items-center gap-2">
                            <span className="text-[11px] text-[#8F959E] w-12 shrink-0">流量占比:</span>
                            <input
                              type="range"
                              min="1"
                              max="100"
                              value={cand.weight}
                              disabled={isUpdatingThis}
                              onChange={(e) => {
                                const val = Number(e.target.value);
                                setCandidates((prev) =>
                                  prev.map((c) =>
                                    c.routeConfigId === cand.routeConfigId ? { ...c, weight: val } : c
                                  )
                                );
                              }}
                              onMouseUp={() =>
                                handleUpdateCandidate(cand.routeConfigId, { weight: cand.weight })
                              }
                              className="flex-1 h-1.5 bg-[#EFF0F1] rounded-lg appearance-none cursor-pointer accent-[#3370FF]"
                            />
                            <span className="text-[12px] font-medium text-[#1F2329] tabular-nums w-8 text-right">
                              {cand.weight}
                            </span>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>

          {/* Footer */}
          <div className="h-[52px] px-6 border-t border-[#EFF0F1] flex items-center justify-between shrink-0 bg-[#F9FAFB] text-[12px] text-[#8F959E]">
            <span>变更实时热更新至 JVM 注册表</span>
            <button
              type="button"
              onClick={onClose}
              className="h-[30px] px-3.5 rounded-[6px] border border-[#DEE0E3] bg-white hover:bg-[#F2F3F5] text-[13px] text-[#1F2329] transition-colors cursor-pointer"
            >
              完成并退出
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

import React, { useState, useEffect } from "react";
import { X, Sparkles, Layers, Zap, ShieldCheck, Scale } from "lucide-react";
import {
  ModelRouteResponse,
  ModelRouteCreateRequest,
  ModelRouteUpdateRequest,
} from "../../../types";
import { modelApi } from "../../../lib/api";
import { FEISHU_FONT_FAMILY } from "../../../components/ui/feishu-table";
import { feishuToast } from "../../../components/ui/FeishuToast";

export interface ModelRouteModalProps {
  isOpen: boolean;
  onClose: () => void;
  route: ModelRouteResponse | null;
  onSuccess: () => void;
}

export const ModelRouteModal: React.FC<ModelRouteModalProps> = ({
  isOpen,
  onClose,
  route,
  onSuccess,
}) => {
  const isEdit = !!route;

  const [routeKey, setRouteKey] = useState("");
  const [modelType, setModelType] = useState<"CHAT" | "EMBEDDING" | "RERANK">("CHAT");
  const [strategy, setStrategy] = useState<"PRIMARY_BACKUP" | "WEIGHT" | "RULE">("PRIMARY_BACKUP");
  const [remark, setRemark] = useState("");
  const [enabled, setEnabled] = useState(true);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    if (route) {
      setRouteKey(route.routeKey || "");
      setModelType((route.modelType as any) || "CHAT");
      setStrategy((route.strategy as any) || "PRIMARY_BACKUP");
      setRemark(route.remark || "");
      setEnabled(route.enabled !== false);
    } else {
      setRouteKey("");
      setModelType("CHAT");
      setStrategy("PRIMARY_BACKUP");
      setRemark("");
      setEnabled(true);
    }
    setErrorMessage(null);
  }, [isOpen, route]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!routeKey.trim()) {
      setErrorMessage("请输入路由标识 (Route Key)");
      return;
    }

    // 格式校验：英文字母、数字、短横线、下划线或点
    if (!/^[a-zA-Z0-9_.-]+$/.test(routeKey.trim())) {
      setErrorMessage("路由标识格式不合法，仅支持英文字母、数字、下划线、短横线和点");
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);

    try {
      if (isEdit && route) {
        const updateData: ModelRouteUpdateRequest = {
          routeKey: routeKey.trim(),
          modelType,
          strategy,
          enabled,
          remark: remark.trim() || undefined,
        };
        await modelApi.updateRoute(route.routeId, updateData);
      } else {
        const createData: ModelRouteCreateRequest = {
          routeKey: routeKey.trim(),
          modelType,
          strategy,
          remark: remark.trim() || undefined,
        };
        await modelApi.createRoute(createData);
      }
      feishuToast.success(isEdit ? "路由策略已更新" : "智能路由策略已创建");
      onSuccess();
      onClose();
    } catch (err: any) {
      const msg = err.message || (isEdit ? "更新路由策略失败" : "创建路由策略失败");
      setErrorMessage(msg);
      feishuToast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1F2329]/40 backdrop-blur-[1px]">
      <div
        style={{ fontFamily: FEISHU_FONT_FAMILY }}
        className="w-full max-w-[560px] bg-white rounded-[12px] border border-[#DEE0E3] shadow-2xl flex flex-col animate-in zoom-in-95 duration-150 overflow-hidden"
      >
        {/* Header */}
        <div className="h-[54px] px-6 border-b border-[#EFF0F1] flex items-center justify-between shrink-0 bg-white">
          <div className="flex items-center gap-2">
            <span className="text-[16px] font-semibold text-[#1F2329]">
              {isEdit ? "编辑路由策略" : "新建智能路由策略"}
            </span>
            {isEdit && (
              <span className="text-[12px] font-medium text-[#3370FF] bg-[#E8F3FF] px-2 py-0.5 rounded-full tabular-nums">
                #{route?.routeId}
              </span>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-7 h-7 rounded-[6px] hover:bg-[#F2F3F5] text-[#8F959E] hover:text-[#1F2329] flex items-center justify-center transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-5 overflow-y-auto max-h-[75vh]">
          {errorMessage && (
            <div className="p-3 bg-[#FFF2F0] border border-[#FFCCC7] rounded-[6px] text-[13px] text-[#F53F3F] leading-relaxed">
              {errorMessage}
            </div>
          )}

          {/* 路由标识 (Route Key) */}
          <div>
            <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
              路由调用标识 (Route Key) <span className="text-[#F53F3F]">*</span>
            </label>
            <input
              type="text"
              value={routeKey}
              onChange={(e) => setRouteKey(e.target.value)}
              disabled={isEdit}
              placeholder="例如: default-chat-route, deepseek-code"
              className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] placeholder:text-[#8F959E] disabled:bg-[#F5F6F7] disabled:text-[#8F959E]"
            />
            <p className="text-[12px] text-[#8F959E] mt-1">
              业务流水线与问答网关统一通过该 Key 绑定调用，创建后作为稳定依赖不可更改。
            </p>
          </div>

          {/* 模型类别 (Model Type) */}
          <div>
            <label className="block text-[13px] font-medium text-[#1F2329] mb-2">
              模型类别 (Model Type) <span className="text-[#F53F3F]">*</span>
            </label>
            <div className="grid grid-cols-3 gap-2.5">
              {[
                { id: "CHAT", label: "对话推理", desc: "大模型生成", icon: Sparkles, color: "text-[#3370FF]" },
                { id: "EMBEDDING", label: "向量嵌入", desc: "高维语义向量", icon: Layers, color: "text-[#10A893]" },
                { id: "RERANK", label: "精细重排", desc: "检索相关度精排", icon: Zap, color: "text-[#722ED1]" },
              ].map((item) => {
                const Icon = item.icon;
                const isSelected = modelType === item.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => setModelType(item.id as any)}
                    className={`flex flex-col items-start p-3 rounded-[8px] border text-left transition-all cursor-pointer ${
                      isSelected
                        ? "border-[#3370FF] bg-[#F0F4FF] ring-1 ring-[#3370FF]"
                        : "border-[#DEE0E3] bg-white hover:bg-[#F8F9FA]"
                    }`}
                  >
                    <div className="flex items-center gap-1.5">
                      <Icon className={`w-3.5 h-3.5 ${item.color}`} />
                      <span className="text-[13px] font-medium text-[#1F2329]">{item.label}</span>
                    </div>
                    <span className="text-[11px] text-[#8F959E] mt-1">{item.desc}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* 调度策略 (Strategy) */}
          <div>
            <label className="block text-[13px] font-medium text-[#1F2329] mb-2">
              调度策略 (Routing Strategy) <span className="text-[#F53F3F]">*</span>
            </label>
            <div className="grid grid-cols-2 gap-3">
              {[
                {
                  id: "PRIMARY_BACKUP",
                  title: "主备容灾模式 (Primary-Backup)",
                  desc: "首选 PRIMARY 节点执行；主节点超时或熔断自动秒级无缝降级至 BACKUP 备用节点。",
                  icon: ShieldCheck,
                  badge: "高可用推荐",
                },
                {
                  id: "WEIGHT",
                  title: "权重分流模式 (Weighted)",
                  desc: "按挂载候选节点的权重配比（1~100）随机调度流量，适用于灰度发布与多供应商分流。",
                  icon: Scale,
                  badge: "多模型分流",
                },
              ].map((item) => {
                const Icon = item.icon;
                const isSelected = strategy === item.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => setStrategy(item.id as any)}
                    className={`flex flex-col justify-between p-3.5 rounded-[8px] border text-left transition-all cursor-pointer ${
                      isSelected
                        ? "border-[#3370FF] bg-[#F0F4FF] ring-1 ring-[#3370FF]"
                        : "border-[#DEE0E3] bg-white hover:bg-[#F8F9FA]"
                    }`}
                  >
                    <div>
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-1.5">
                          <Icon className={`w-4 h-4 ${isSelected ? "text-[#3370FF]" : "text-[#646A73]"}`} />
                          <span className="text-[13px] font-semibold text-[#1F2329]">{item.title}</span>
                        </div>
                      </div>
                      <p className="text-[12px] text-[#646A73] mt-2 leading-relaxed">{item.desc}</p>
                    </div>
                    <div className="mt-3">
                      <span
                        className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${
                          isSelected ? "bg-[#3370FF] text-white" : "bg-[#F2F3F5] text-[#646A73]"
                        }`}
                      >
                        {item.badge}
                      </span>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* 业务描述说明 (Remark) */}
          <div>
            <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
              策略描述与业务说明 (Remark)
            </label>
            <input
              type="text"
              value={remark}
              onChange={(e) => setRemark(e.target.value)}
              placeholder="例如: 生产环境主力问答高可用通道"
              className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] placeholder:text-[#8F959E]"
            />
          </div>

          {/* 启用状态 (仅编辑时展示) */}
          {isEdit && (
            <div className="flex items-center justify-between p-3.5 bg-[#F9FAFB] rounded-[8px] border border-[#EFF0F1]">
              <div>
                <span className="text-[13px] font-medium text-[#1F2329] block">路由启用状态</span>
                <span className="text-[12px] text-[#8F959E]">停用后网关将对该 routeKey 拦截请求</span>
              </div>
              <button
                type="button"
                onClick={() => setEnabled(!enabled)}
                className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                  enabled ? "bg-[#3370FF]" : "bg-[#DEE0E3]"
                }`}
              >
                <span
                  className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow-sm ring-0 transition duration-200 ease-in-out ${
                    enabled ? "translate-x-4" : "translate-x-0"
                  }`}
                />
              </button>
            </div>
          )}
        </form>

        {/* Footer */}
        <div className="h-[56px] px-6 border-t border-[#EFF0F1] flex items-center justify-end gap-2.5 shrink-0 bg-white">
          <button
            type="button"
            onClick={onClose}
            className="h-[32px] px-4 rounded-[6px] border border-[#DEE0E3] bg-white hover:bg-[#F2F3F5] active:scale-[0.98] text-[14px] text-[#1F2329] transition-all cursor-pointer"
          >
            取消
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={isSubmitting}
            className="h-[32px] px-4 rounded-[6px] bg-[#3370FF] hover:bg-[#2860E1] active:scale-[0.98] text-[14px] text-white font-normal transition-all cursor-pointer disabled:opacity-50"
          >
            {isSubmitting ? "正在保存…" : isEdit ? "保存修改" : "创建策略"}
          </button>
        </div>
      </div>
    </div>
  );
};

import React, { useState, useEffect } from "react";
import { X, Copy, Check, Eye, EyeOff, Loader2 } from "lucide-react";
import {
  ModelConfigResponse,
  ModelConnectionTestResponse,
} from "@/types";
import { modelApi } from "@/lib/api.ts";
import { FEISHU_FONT_FAMILY, FeishuPill, FeishuTag } from "@/components/ui/feishu-table";

export interface ModelConfigDetailDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  config: ModelConfigResponse | null;
  onEdit: (config: ModelConfigResponse) => void;
  onOpenGovernance: (config: ModelConfigResponse) => void;
}

export const ModelConfigDetailDrawer: React.FC<ModelConfigDetailDrawerProps> = ({
                                                                                  isOpen,
                                                                                  onClose,
                                                                                  config,
                                                                                  onEdit,
                                                                                  onOpenGovernance,
                                                                                }) => {
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<ModelConnectionTestResponse | null>(null);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  // API Key 明文查看状态
  const [rawKey, setRawKey] = useState<string | null>(null);
  const [showRawKey, setShowRawKey] = useState(false);
  const [loadingRawKey, setLoadingRawKey] = useState(false);

  useEffect(() => {
    // 切换配置时重置明文状态
    setRawKey(null);
    setShowRawKey(false);
    setLoadingRawKey(false);
    setTestResult(null);
  }, [config?.configId]);

  if (!isOpen || !config) return null;

  const handleCopy = (key: string, val: string) => {
    navigator.clipboard.writeText(val);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const handleToggleRawKey = async () => {
    if (showRawKey) {
      setShowRawKey(false);
      return;
    }
    if (rawKey) {
      setShowRawKey(true);
      return;
    }

    setLoadingRawKey(true);
    try {
      const res = await modelApi.getRawApiKey(config.configId);
      setRawKey(res);
      setShowRawKey(true);
    } catch (err: any) {
      alert(err.message || "获取未脱敏 API Key 失败");
    } finally {
      setLoadingRawKey(false);
    }
  };

  const handleTestConnection = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const res = await modelApi.testConfig(config.configId);
      setTestResult(res);
    } catch {
      setTestResult({
        success: false,
        latencyMs: 0,
        errorMessage: "网络探测失败或接口无响应",
        testedAt: new Date().toISOString(),
      });
    } finally {
      setTesting(false);
    }
  };

  const isActive = config.enabled !== false && config.status !== "INACTIVE";

  const getModelTypeLabel = (type?: string) => {
    const t = (type || "").toUpperCase();
    if (t.includes("CHAT")) return "对话";
    if (t.includes("EMBED")) return "向量";
    if (t.includes("RERANK")) return "重排序";
    return type || "对话";
  };

  const getModelTypeVariant = (type?: string) => {
    const t = (type || "").toUpperCase();
    if (t.includes("CHAT")) return "blue";
    if (t.includes("EMBED")) return "green";
    if (t.includes("RERANK")) return "purple";
    return "gray";
  };

  return (
    <div className="fixed inset-0 z-50 overflow-hidden bg-[#1F2329]/30 backdrop-blur-[1px]">
      <div className="absolute inset-0" onClick={onClose} />

      <div
        style={{ fontFamily: FEISHU_FONT_FAMILY }}
        className="absolute inset-y-0 right-0 max-w-full flex pl-10"
      >
        <div className="w-screen max-w-[560px] bg-white border-l border-[#DEE0E3] shadow-2xl flex flex-col animate-in slide-in-from-right duration-200">
          {/* Header */}
          <div className="h-[56px] px-6 border-b border-[#EFF0F1] flex items-center justify-between shrink-0 bg-white">
            <div className="flex items-center gap-2.5">
              <span className="text-[16px] font-semibold text-[#1F2329]">
                模型契约详情
              </span>
              <FeishuPill variant={isActive ? "green" : "gray"} showDot={false}>
                {isActive ? "活跃" : "下线"}
              </FeishuPill>
            </div>
            <button
              type="button"
              onClick={onClose}
              className="w-7 h-7 rounded-[6px] hover:bg-[#F2F3F5] text-[#8F959E] hover:text-[#1F2329] flex items-center justify-center transition-colors cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* Drawer Body - 纯白卡片架构（零灰色大底） */}
          <div className="flex-1 overflow-y-auto p-6 space-y-5">
            {/* 顶栏卡片 */}
            <div className="p-5 bg-white rounded-[12px] border border-[#DEE0E3] shadow-2xs space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-[16px] font-semibold text-[#1F2329]">
                  {config.configKey || config.configName}
                </span>
                <FeishuTag>{config.provider || config.providerCode || "OPENAI"}</FeishuTag>
              </div>
              <div className="text-[13px] text-[#646A73] flex items-center gap-2">
                <span>模型类别：</span>
                <FeishuPill
                  variant={getModelTypeVariant(config.modelType)}
                  showDot={false}
                >
                  {getModelTypeLabel(config.modelType)}
                </FeishuPill>
              </div>
            </div>

            {/* 探测状态反馈 */}
            {testResult && (
              <div
                className={`p-3.5 rounded-[8px] border text-[13px] leading-relaxed ${
                  testResult.success
                    ? "bg-[#E6F7ED] border-[#B7EB8F] text-[#00B42A]"
                    : "bg-[#FFF2F0] border-[#FFCCC7] text-[#F53F3F]"
                }`}
              >
                探测状态：{testResult.success ? "连通正常" : "连通异常"} · 响应耗时 {testResult.latencyMs}ms
                {testResult.errorMessage && ` (${testResult.errorMessage})`}
              </div>
            )}

            {/* 核心参数列表 */}
            <div className="p-5 bg-white rounded-[12px] border border-[#DEE0E3] shadow-2xs space-y-4">
              <h4 className="text-[14px] font-semibold text-[#1F2329]">
                接入契约与网关参数
              </h4>

              <div className="space-y-3.5 text-[13px]">
                <div className="flex flex-col gap-1 pb-3 border-b border-[#EFF0F1]">
                  <div className="flex items-center justify-between text-[#8F959E]">
                    <span>Base URL</span>
                    <button
                      type="button"
                      onClick={() => handleCopy("baseUrl", config.baseUrl || "")}
                      className="text-[#3370FF] hover:underline inline-flex items-center gap-1 text-[12px] cursor-pointer"
                    >
                      {copiedKey === "baseUrl" ? <Check className="w-3 h-3 text-[#00B42A]" /> : <Copy className="w-3 h-3" />}
                      复制
                    </button>
                  </div>
                  <span className="text-[#1F2329] font-medium break-all select-all">
                    {config.baseUrl || "-"}
                  </span>
                </div>

                <div className="flex items-center justify-between pb-3 border-b border-[#EFF0F1]">
                  <span className="text-[#8F959E]">Endpoint Path</span>
                  <span className="text-[#1F2329] font-medium">
                    {config.endpointPath || "/chat/completions"}
                  </span>
                </div>

                <div className="flex items-center justify-between pb-3 border-b border-[#EFF0F1]">
                  <span className="text-[#8F959E]">模型代码 (modelName)</span>
                  <div className="flex items-center gap-1.5">
                    <span className="font-medium text-[#1F2329]">
                      {config.modelName || "-"}
                    </span>
                    <button
                      type="button"
                      onClick={() => handleCopy("modelName", config.modelName || "")}
                      className="text-[#8F959E] hover:text-[#1F2329] p-1 rounded cursor-pointer"
                    >
                      {copiedKey === "modelName" ? <Check className="w-3.5 h-3.5 text-[#00B42A]" /> : <Copy className="w-3.5 h-3.5" />}
                    </button>
                  </div>
                </div>

                <div className="flex items-center justify-between pb-3 border-b border-[#EFF0F1]">
                  <span className="text-[#8F959E]">API Key</span>
                  <div className="flex items-center gap-1.5">
                    <span className="text-[#646A73] tabular-nums font-medium break-all select-all max-w-[280px]">
                      {showRawKey ? (rawKey || config.apiKeyMask || "••••••••") : (config.apiKeyMask || config.apiKeyMasked || "••••••••")}
                    </span>
                    <button
                      type="button"
                      onClick={handleToggleRawKey}
                      disabled={loadingRawKey}
                      title={showRawKey ? "隐藏明文" : "查看原始未掩码 Key"}
                      className="p-1 text-[#8F959E] hover:text-[#1F2329] hover:bg-[#F2F3F5] rounded transition-all cursor-pointer disabled:opacity-50"
                    >
                      {loadingRawKey ? (
                        <Loader2 className="w-3.5 h-3.5 animate-spin text-[#3370FF]" />
                      ) : showRawKey ? (
                        <EyeOff className="w-3.5 h-3.5 text-[#3370FF]" />
                      ) : (
                        <Eye className="w-3.5 h-3.5" />
                      )}
                    </button>
                    {showRawKey && rawKey && (
                      <button
                        type="button"
                        onClick={() => handleCopy("rawKey", rawKey)}
                        title="复制完整明文 Key"
                        className="p-1 text-[#8F959E] hover:text-[#1F2329] hover:bg-[#F2F3F5] rounded transition-all cursor-pointer"
                      >
                        {copiedKey === "rawKey" ? <Check className="w-3.5 h-3.5 text-[#00B42A]" /> : <Copy className="w-3.5 h-3.5" />}
                      </button>
                    )}
                  </div>
                </div>

                <div className="flex items-center justify-between pb-3 border-b border-[#EFF0F1]">
                  <span className="text-[#8F959E]">超时时间</span>
                  <span className="text-[#1F2329] tabular-nums">
                    {config.timeoutMs ? `${config.timeoutMs} ms` : "30000 ms"}
                  </span>
                </div>

                <div className="flex items-center justify-between">
                  <span className="text-[#8F959E]">最大重试次数</span>
                  <span className="text-[#1F2329] tabular-nums">
                    {config.maxRetries !== undefined ? `${config.maxRetries} 次` : "2 次"}
                  </span>
                </div>
              </div>
            </div>

            {/* 扩展与系统信息 */}
            <div className="p-5 bg-white rounded-[12px] border border-[#DEE0E3] shadow-2xs space-y-4">
              <h4 className="text-[14px] font-semibold text-[#1F2329]">
                基本元信息
              </h4>

              <div className="space-y-3.5 text-[13px]">
                <div className="flex items-center justify-between pb-3 border-b border-[#EFF0F1]">
                  <span className="text-[#8F959E]">配置 ID / 乐观锁版本</span>
                  <span className="text-[#1F2329] font-medium tabular-nums">
                    #{config.configId} · v{config.version || 1}
                  </span>
                </div>

                <div className="flex items-center justify-between pb-3 border-b border-[#EFF0F1]">
                  <span className="text-[#8F959E]">创建时间</span>
                  <span className="text-[#646A73] tabular-nums">
                    {config.createTime || config.createdTime || "-"}
                  </span>
                </div>

                <div className="flex items-center justify-between pb-3 border-b border-[#EFF0F1]">
                  <span className="text-[#8F959E]">更新时间</span>
                  <span className="text-[#646A73] tabular-nums">
                    {config.updateTime || config.updatedTime || "-"}
                  </span>
                </div>

                {config.remark && (
                  <div className="flex flex-col gap-1.5 pt-1">
                    <span className="text-[#8F959E]">备注说明</span>
                    <p className="text-[#1F2329] bg-white border border-[#EFF0F1] p-3 rounded-[8px] text-[13px] leading-relaxed shadow-2xs">
                      {config.remark}
                    </p>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Footer Actions */}
          <div className="h-[56px] px-6 border-t border-[#EFF0F1] flex items-center justify-between shrink-0 bg-white">
            <button
              type="button"
              onClick={handleTestConnection}
              disabled={testing}
              className="h-[32px] px-3.5 rounded-[6px] border border-[#DEE0E3] bg-white hover:bg-[#F2F3F5] active:scale-[0.98] text-[14px] text-[#1F2329] transition-all cursor-pointer disabled:opacity-50"
            >
              {testing ? "正在探测…" : "测试连通性"}
            </button>

            <div className="flex items-center gap-2.5">
              <button
                type="button"
                onClick={() => {
                  onOpenGovernance(config);
                  onClose();
                }}
                className="h-[32px] px-3.5 rounded-[6px] border border-[#3370FF] text-[#3370FF] hover:bg-[#E8F3FF] active:scale-[0.98] text-[14px] transition-all cursor-pointer"
              >
                治理配置
              </button>

              <button
                type="button"
                onClick={() => {
                  onEdit(config);
                  onClose();
                }}
                className="h-[32px] px-4 rounded-[6px] bg-[#3370FF] hover:bg-[#2860E1] active:scale-[0.98] text-[14px] text-white font-normal transition-all cursor-pointer"
              >
                编辑配置
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

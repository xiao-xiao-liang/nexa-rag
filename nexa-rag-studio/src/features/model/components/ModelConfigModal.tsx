import React, { useState, useEffect } from "react";
import { X, Eye, EyeOff } from "lucide-react";
import {
  ModelConfigResponse,
  ModelConfigCreateRequest,
  ModelConfigUpdateRequest,
  ModelProviderCatalogResponse,
  ModelConnectionTestResponse,
} from "../../../types";
import { modelApi } from "../../../lib/api";
import { FEISHU_FONT_FAMILY } from "../../../components/ui/feishu-table";
import { FeishuSelect } from "../../../components/ui/feishu-select";

export interface ModelConfigModalProps {
  isOpen: boolean;
  onClose: () => void;
  config: ModelConfigResponse | null;
  providers: ModelProviderCatalogResponse[];
  onSuccess: () => void;
}

const DEFAULT_BASE_URLS: Record<string, { baseUrl: string; endpointPath: string; defaultModel: string }> = {
  OPENAI: {
    baseUrl: "https://api.openai.com/v1",
    endpointPath: "/chat/completions",
    defaultModel: "gpt-4o",
  },
  DEEPSEEK: {
    baseUrl: "https://api.deepseek.com/v1",
    endpointPath: "/chat/completions",
    defaultModel: "deepseek-chat",
  },
  ALIBABA: {
    baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    endpointPath: "/chat/completions",
    defaultModel: "qwen-plus",
  },
  ANTHROPIC: {
    baseUrl: "https://api.anthropic.com/v1",
    endpointPath: "/messages",
    defaultModel: "claude-3-5-sonnet-20241022",
  },
  LOCAL: {
    baseUrl: "http://localhost:11434/v1",
    endpointPath: "/chat/completions",
    defaultModel: "qwen2.5-coder",
  },
};

export const ModelConfigModal: React.FC<ModelConfigModalProps> = ({
  isOpen,
  onClose,
  config,
  providers,
  onSuccess,
}) => {
  const isEdit = !!config;

  const [configKey, setConfigKey] = useState("");
  const [provider, setProvider] = useState("DEEPSEEK");
  const [modelType, setModelType] = useState("CHAT");
  const [baseUrl, setBaseUrl] = useState("https://api.deepseek.com/v1");
  const [endpointPath, setEndpointPath] = useState("/chat/completions");
  const [modelName, setModelName] = useState("deepseek-chat");
  const [apiKey, setApiKey] = useState("");
  const [showApiKey, setShowApiKey] = useState(false);
  const [timeoutMs, setTimeoutMs] = useState(30000);
  const [maxRetries, setMaxRetries] = useState(2);
  const [enabled, setEnabled] = useState(true);
  const [extraConfig, setExtraConfig] = useState("");
  const [remark, setRemark] = useState("");

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<ModelConnectionTestResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;

    if (config) {
      setConfigKey(config.configKey || config.configName || "");
      setProvider(config.provider || config.providerCode || "DEEPSEEK");
      setModelType(config.modelType || "CHAT");
      setBaseUrl(config.baseUrl || "");
      setEndpointPath(config.endpointPath || "");
      setModelName(config.modelName || "");
      setApiKey("");
      setTimeoutMs(config.timeoutMs || 30000);
      setMaxRetries(config.maxRetries ?? 2);
      setEnabled(config.enabled !== false && config.status !== "INACTIVE");
      setExtraConfig(config.extraConfig || "");
      setRemark(config.remark || "");
    } else {
      setConfigKey("");
      setProvider("DEEPSEEK");
      setModelType("CHAT");
      const defaults = DEFAULT_BASE_URLS.DEEPSEEK;
      setBaseUrl(defaults.baseUrl);
      setEndpointPath(defaults.endpointPath);
      setModelName(defaults.defaultModel);
      setApiKey("");
      setTimeoutMs(30000);
      setMaxRetries(2);
      setEnabled(true);
      setExtraConfig("");
      setRemark("");
    }
    setTestResult(null);
    setErrorMessage(null);
  }, [isOpen, config]);

  const handleProviderChange = (val: string) => {
    setProvider(val);
    const defaults = DEFAULT_BASE_URLS[val];
    if (defaults) {
      if (!isEdit || !baseUrl) {
        setBaseUrl(defaults.baseUrl);
        setEndpointPath(defaults.endpointPath);
        setModelName(defaults.defaultModel);
      }
    }
  };

  // 获取当前厂商的推荐模型列表
  const currentProviderCatalog = providers.find(
    (p) => (p.provider || p.providerCode || "").toUpperCase() === provider.toUpperCase()
  );
  const recommendedModels: string[] = currentProviderCatalog
    ? Array.isArray(currentProviderCatalog.recommendedModels)
      ? currentProviderCatalog.recommendedModels
      : currentProviderCatalog.recommendedModels && typeof currentProviderCatalog.recommendedModels === "object"
      ? (currentProviderCatalog.recommendedModels as any)[modelType] || Object.values(currentProviderCatalog.recommendedModels).flat()
      : []
    : [];

  const handleTestConnection = async () => {
    if (!config?.configId) {
      setErrorMessage("新建配置请先保存后再执行在线连通性探测");
      return;
    }
    setTesting(true);
    setTestResult(null);
    setErrorMessage(null);
    try {
      const res = await modelApi.testConfig(config.configId);
      setTestResult(res);
    } catch (err: any) {
      setErrorMessage(err.message || "连通性探测失败");
    } finally {
      setTesting(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!configKey.trim()) {
      setErrorMessage("请输入配置标识 Key");
      return;
    }
    if (!baseUrl.trim()) {
      setErrorMessage("请输入 Base URL");
      return;
    }
    if (!modelName.trim()) {
      setErrorMessage("请输入模型代码 (modelName)");
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);

    try {
      if (isEdit && config) {
        const updateData: ModelConfigUpdateRequest = {
          configKey: configKey.trim(),
          provider,
          modelType,
          baseUrl: baseUrl.trim(),
          endpointPath: endpointPath.trim() || undefined,
          apiKey: apiKey.trim() ? apiKey.trim() : undefined,
          modelName: modelName.trim(),
          enabled,
          timeoutMs: Number(timeoutMs) || 30000,
          maxRetries: Number(maxRetries) || 0,
          extraConfig: extraConfig.trim() || undefined,
          remark: remark.trim() || undefined,
        };
        await modelApi.updateConfig(config.configId, updateData);
      } else {
        const createData: ModelConfigCreateRequest = {
          configKey: configKey.trim(),
          provider,
          modelType,
          baseUrl: baseUrl.trim(),
          endpointPath: endpointPath.trim() || undefined,
          apiKey: apiKey.trim() || undefined,
          modelName: modelName.trim(),
          timeoutMs: Number(timeoutMs) || 30000,
          maxRetries: Number(maxRetries) || 0,
          extraConfig: extraConfig.trim() || undefined,
          remark: remark.trim() || undefined,
        };
        await modelApi.createConfig(createData);
      }
      onSuccess();
      onClose();
    } catch (err: any) {
      setErrorMessage(err.message || "保存模型配置失败");
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  const providerOptions = providers.length > 0
    ? providers.map((p) => {
        const code = p.provider || p.providerCode || "";
        const name = p.displayName || p.providerName || code;
        return {
          value: code,
          label: `${name} (${code})`,
        };
      })
    : Object.keys(DEFAULT_BASE_URLS).map((k) => ({
        value: k,
        label: k,
      }));

  const modelTypeOptions = [
    { value: "CHAT", label: "对话大模型 (CHAT)" },
    { value: "EMBEDDING", label: "向量嵌入模型 (EMBEDDING)" },
    { value: "RERANK", label: "精细重排模型 (RERANK)" },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1F2329]/40 backdrop-blur-[1px]">
      <div
        style={{ fontFamily: FEISHU_FONT_FAMILY }}
        className="w-full max-w-[620px] max-h-[90vh] bg-white rounded-[12px] border border-[#DEE0E3] shadow-2xl flex flex-col animate-in zoom-in-95 duration-150 overflow-hidden"
      >
        {/* Header - 飞书纯文字 0 假图标 */}
        <div className="h-[54px] px-6 border-b border-[#EFF0F1] flex items-center justify-between shrink-0 bg-white">
          <span className="text-[16px] font-semibold text-[#1F2329]">
            {isEdit ? "编辑模型配置" : "新增模型配置"}
          </span>
          <button
            type="button"
            onClick={onClose}
            className="w-7 h-7 rounded-[6px] hover:bg-[#F2F3F5] text-[#8F959E] hover:text-[#1F2329] flex items-center justify-center transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-4">
          {errorMessage && (
            <div className="p-3 bg-[#FFF2F0] border border-[#FFCCC7] rounded-[6px] text-[13px] text-[#F53F3F] leading-relaxed">
              {errorMessage}
            </div>
          )}

          {testResult && (
            <div
              className={`p-3 rounded-[6px] border text-[13px] leading-relaxed ${
                testResult.success
                  ? "bg-[#E6F7ED] border-[#B7EB8F] text-[#00B42A]"
                  : "bg-[#FFF2F0] border-[#FFCCC7] text-[#F53F3F]"
              }`}
            >
              探测结果：{testResult.success ? "连通测试成功" : "连通失败"} (响应耗时 {testResult.latencyMs}ms)
              {testResult.errorMessage && ` - ${testResult.errorMessage}`}
            </div>
          )}

          {/* 快捷推荐模型 Chip 列表 */}
          {recommendedModels.length > 0 && (
            <div className="space-y-1.5 pb-1">
              <label className="block text-[12px] font-normal text-[#646A73]">
                快捷接入推荐模型
              </label>
              <div className="flex flex-wrap gap-1.5">
                {recommendedModels.map((m) => (
                  <button
                    key={m}
                    type="button"
                    onClick={() => setModelName(m)}
                    className={`px-2.5 py-1 rounded-full text-[12px] transition-all cursor-pointer border ${
                      modelName === m
                        ? "bg-[#E8F3FF] text-[#3370FF] border-[#3370FF] font-medium"
                        : "bg-white text-[#1F2329] border-[#DEE0E3] hover:bg-[#F2F3F5]"
                    }`}
                  >
                    {m}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* 基础信息 */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
                配置标识 Key <span className="text-[#F53F3F]">*</span>
              </label>
              <input
                type="text"
                value={configKey}
                onChange={(e) => setConfigKey(e.target.value)}
                placeholder="例如: deepseek-chat-prod"
                className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] placeholder:text-[#8F959E]"
              />
            </div>

            <div>
              <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
                模型类型 <span className="text-[#F53F3F]">*</span>
              </label>
              <FeishuSelect
                options={modelTypeOptions}
                value={modelType}
                onChange={(val) => setModelType(val)}
                className="w-full"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
                供应商 (Provider) <span className="text-[#F53F3F]">*</span>
              </label>
              <FeishuSelect
                options={providerOptions}
                value={provider}
                onChange={handleProviderChange}
                className="w-full"
              />
            </div>

            <div>
              <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
                模型代码 (modelName) <span className="text-[#F53F3F]">*</span>
              </label>
              <input
                type="text"
                value={modelName}
                onChange={(e) => setModelName(e.target.value)}
                placeholder="例如: deepseek-chat, gpt-4o"
                className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] placeholder:text-[#8F959E]"
              />
            </div>
          </div>

          {/* 接口地址与鉴权 */}
          <div className="grid grid-cols-3 gap-4">
            <div className="col-span-2">
              <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
                Base URL <span className="text-[#F53F3F]">*</span>
              </label>
              <input
                type="text"
                value={baseUrl}
                onChange={(e) => setBaseUrl(e.target.value)}
                placeholder="https://api.openai.com/v1"
                className="w-full h-[36px] px-3 text-[13px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] placeholder:text-[#8F959E]"
              />
            </div>

            <div>
              <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
                Endpoint Path
              </label>
              <input
                type="text"
                value={endpointPath}
                onChange={(e) => setEndpointPath(e.target.value)}
                placeholder="/chat/completions"
                className="w-full h-[36px] px-3 text-[13px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] placeholder:text-[#8F959E]"
              />
            </div>
          </div>

          <div>
            <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
              API Key 密钥
            </label>
            <div className="relative flex items-center">
              <input
                type={showApiKey ? "text" : "password"}
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder={isEdit ? "留空表示保留原密钥不变" : "输入 API Key (如 sk-...)"}
                className="w-full h-[36px] pl-3 pr-10 text-[13px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] placeholder:text-[#8F959E]"
              />
              <button
                type="button"
                onClick={() => setShowApiKey(!showApiKey)}
                className="absolute right-2 p-1.5 text-[#8F959E] hover:text-[#1F2329] rounded cursor-pointer"
              >
                {showApiKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {/* 超时与重试 */}
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
                超时时间 (ms)
              </label>
              <input
                type="number"
                value={String(timeoutMs)}
                onChange={(e) => setTimeoutMs(Number(e.target.value) || 0)}
                placeholder="30000"
                className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329]"
              />
            </div>

            <div>
              <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
                最大重试次数
              </label>
              <input
                type="number"
                value={String(maxRetries)}
                onChange={(e) => setMaxRetries(Number(e.target.value) || 0)}
                placeholder="2"
                className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329]"
              />
            </div>

            <div>
              <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
                状态
              </label>
              <div className="flex items-center gap-4 h-[36px]">
                <label className="flex items-center gap-1.5 text-[14px] text-[#1F2329] cursor-pointer">
                  <input
                    type="radio"
                    name="enabled_status"
                    checked={enabled}
                    onChange={() => setEnabled(true)}
                    className="accent-[#3370FF]"
                  />
                  启用
                </label>
                <label className="flex items-center gap-1.5 text-[14px] text-[#646A73] cursor-pointer">
                  <input
                    type="radio"
                    name="enabled_status"
                    checked={!enabled}
                    onChange={() => setEnabled(false)}
                    className="accent-[#3370FF]"
                  />
                  停用
                </label>
              </div>
            </div>
          </div>

          {/* 备注说明 */}
          <div>
            <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
              备注说明
            </label>
            <input
              type="text"
              value={remark}
              onChange={(e) => setRemark(e.target.value)}
              placeholder="例如: 生产环境主力大模型"
              className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] placeholder:text-[#8F959E]"
            />
          </div>
        </form>

        {/* Footer */}
        <div className="h-[56px] px-6 border-t border-[#EFF0F1] flex items-center justify-between shrink-0 bg-white">
          <div>
            {isEdit && (
              <button
                type="button"
                onClick={handleTestConnection}
                disabled={testing}
                className="h-[32px] px-3.5 rounded-[6px] border border-[#DEE0E3] bg-white hover:bg-[#F2F3F5] active:scale-[0.98] text-[14px] text-[#1F2329] transition-all cursor-pointer disabled:opacity-50"
              >
                {testing ? "正在测试…" : "测试连通性"}
              </button>
            )}
          </div>

          <div className="flex items-center gap-2.5">
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
              {isSubmitting ? "正在保存…" : "保存配置"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

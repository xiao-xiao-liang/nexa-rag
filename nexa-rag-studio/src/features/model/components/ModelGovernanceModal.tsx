import React, { useState, useEffect } from "react";
import { X } from "lucide-react";
import {
  ModelConfigResponse,
  ModelGovernanceConfigRequest,
} from "../../../types";
import { modelApi } from "../../../lib/api";
import { FEISHU_FONT_FAMILY } from "../../../components/ui/feishu-table";

export interface ModelGovernanceModalProps {
  isOpen: boolean;
  onClose: () => void;
  config: ModelConfigResponse | null;
  onSuccess: () => void;
}

export const ModelGovernanceModal: React.FC<ModelGovernanceModalProps> = ({
  isOpen,
  onClose,
  config,
  onSuccess,
}) => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // 治理开关
  const [enabled, setEnabled] = useState(true);

  // 重试策略
  const [retryEnabled, setRetryEnabled] = useState(true);
  const [maxAttempts, setMaxAttempts] = useState(3);
  const [retryWaitMs, setRetryWaitMs] = useState(500);

  // QPS 限流
  const [rateLimitEnabled, setRateLimitEnabled] = useState(true);
  const [limitForPeriod, setLimitForPeriod] = useState(50);
  const [limitRefreshPeriodMs, setLimitRefreshPeriodMs] = useState(1000);
  const [timeoutDurationMs, setTimeoutDurationMs] = useState(500);

  // 熔断降级
  const [circuitEnabled, setCircuitEnabled] = useState(true);
  const [failureRateThreshold, setFailureRateThreshold] = useState(50);
  const [slowCallRateThreshold, setSlowCallRateThreshold] = useState(70);
  const [slowCallDurationMs, setSlowCallDurationMs] = useState(3000);
  const [slidingWindowSize, setSlidingWindowSize] = useState(20);
  const [minimumNumberOfCalls, setMinimumNumberOfCalls] = useState(10);
  const [waitDurationInOpenStateMs, setWaitDurationInOpenStateMs] = useState(10000);

  // 并发与超时
  const [bulkheadEnabled, setBulkheadEnabled] = useState(true);
  const [maxConcurrentCalls, setMaxConcurrentCalls] = useState(20);
  const [maxWaitDurationMs, setMaxWaitDurationMs] = useState(200);

  const [timeLimiterEnabled, setTimeLimiterEnabled] = useState(true);
  const [timeLimiterTimeoutMs, setTimeLimiterTimeoutMs] = useState(30000);
  const [streamFirstChunkTimeoutMs, setStreamFirstChunkTimeoutMs] = useState(8000);
  const [streamMaxDurationMs, setStreamMaxDurationMs] = useState(120000);

  useEffect(() => {
    if (!isOpen || !config) return;
    loadGovernanceConfig();
  }, [isOpen, config]);

  const loadGovernanceConfig = async () => {
    if (!config) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const gv: any = await modelApi.getGovernance(config.configId);
      if (gv) {
        setEnabled(gv.enabled !== false);
        setRetryEnabled(gv.retryEnabled !== false);
        setMaxAttempts(gv.maxAttempts ?? 3);
        setRetryWaitMs(gv.retryWaitMs ?? 500);

        setRateLimitEnabled(gv.rateLimitEnabled !== false);
        setLimitForPeriod(gv.limitForPeriod ?? gv.rateLimitQps ?? 50);
        setLimitRefreshPeriodMs(gv.limitRefreshPeriodMs ?? 1000);
        setTimeoutDurationMs(gv.timeoutDurationMs ?? 500);

        setCircuitEnabled(gv.circuitEnabled !== false);
        setFailureRateThreshold(gv.failureRateThreshold ?? 50);
        setSlowCallRateThreshold(gv.slowCallRateThreshold ?? 70);
        setSlowCallDurationMs(gv.slowCallDurationMs ?? 3000);
        setSlidingWindowSize(gv.slidingWindowSize ?? 20);
        setMinimumNumberOfCalls(gv.minimumNumberOfCalls ?? 10);
        setWaitDurationInOpenStateMs(gv.waitDurationInOpenStateMs ?? 10000);

        setBulkheadEnabled(gv.bulkheadEnabled !== false);
        setMaxConcurrentCalls(gv.maxConcurrentCalls ?? 20);
        setMaxWaitDurationMs(gv.maxWaitDurationMs ?? 200);

        setTimeLimiterEnabled(gv.timeLimiterEnabled !== false);
        setTimeLimiterTimeoutMs(gv.timeLimiterTimeoutMs ?? gv.timeoutMs ?? 30000);
        setStreamFirstChunkTimeoutMs(gv.streamFirstChunkTimeoutMs ?? 8000);
        setStreamMaxDurationMs(gv.streamMaxDurationMs ?? 120000);
      }
    } catch {
      // 默认参数已就绪
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    if (!config) return;
    setSaving(true);
    setErrorMessage(null);

    const payload: ModelGovernanceConfigRequest = {
      bindingMode: "CONFIG",
      enabled,
      retryEnabled,
      maxAttempts: Number(maxAttempts) || 3,
      retryWaitMs: Number(retryWaitMs) || 500,
      rateLimitEnabled,
      limitForPeriod: Number(limitForPeriod) || 50,
      limitRefreshPeriodMs: Number(limitRefreshPeriodMs) || 1000,
      timeoutDurationMs: Number(timeoutDurationMs) || 500,
      circuitEnabled,
      failureRateThreshold: Number(failureRateThreshold) || 50,
      slowCallRateThreshold: Number(slowCallRateThreshold) || 70,
      slowCallDurationMs: Number(slowCallDurationMs) || 3000,
      slidingWindowSize: Number(slidingWindowSize) || 20,
      minimumNumberOfCalls: Number(minimumNumberOfCalls) || 10,
      waitDurationInOpenStateMs: Number(waitDurationInOpenStateMs) || 10000,
      bulkheadEnabled,
      maxConcurrentCalls: Number(maxConcurrentCalls) || 20,
      maxWaitDurationMs: Number(maxWaitDurationMs) || 200,
      timeLimiterEnabled,
      timeLimiterTimeoutMs: Number(timeLimiterTimeoutMs) || 30000,
      streamFirstChunkTimeoutMs: Number(streamFirstChunkTimeoutMs) || 8000,
      streamMaxDurationMs: Number(streamMaxDurationMs) || 120000,
    };

    try {
      await modelApi.saveGovernance(config.configId, payload);
      onSuccess();
      onClose();
    } catch (err: any) {
      setErrorMessage(err.message || "保存治理配置失败");
    } finally {
      setSaving(false);
    }
  };

  if (!isOpen || !config) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1F2329]/40 backdrop-blur-[1px]">
      <div
        style={{ fontFamily: FEISHU_FONT_FAMILY }}
        className="w-full max-w-[700px] max-h-[90vh] bg-white rounded-[12px] border border-[#DEE0E3] shadow-2xl flex flex-col animate-in zoom-in-95 duration-150 overflow-hidden"
      >
        {/* Header */}
        <div className="h-[54px] px-6 border-b border-[#EFF0F1] flex items-center justify-between shrink-0 bg-white">
          <div className="flex items-center gap-2">
            <span className="text-[16px] font-semibold text-[#1F2329]">
              模型配置治理参数
            </span>
            <span className="text-[12px] font-medium text-[#3370FF] bg-[#E8F3FF] px-2 py-0.5 rounded-full tabular-nums">
              {config.configKey || config.configName}
            </span>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-7 h-7 rounded-[6px] hover:bg-[#F2F3F5] text-[#8F959E] hover:text-[#1F2329] flex items-center justify-center transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Body - 纯白卡片体系 */}
        <div className="flex-1 overflow-y-auto p-6 space-y-5">
          {errorMessage && (
            <div className="p-3 bg-[#FFF2F0] border border-[#FFCCC7] rounded-[6px] text-[13px] text-[#F53F3F] leading-relaxed">
              {errorMessage}
            </div>
          )}

          {/* 全局开关 */}
          <div className="flex items-center justify-between p-4 bg-white rounded-[12px] border border-[#DEE0E3] shadow-2xs">
            <div>
              <div className="text-[14px] font-semibold text-[#1F2329]">
                启用此配置专属治理策略
              </div>
              <div className="text-[12px] text-[#646A73] mt-0.5">
                开启后，针对该模型的调用将受以下重试、限流、熔断与并发限制保护
              </div>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={enabled}
                onChange={(e) => setEnabled(e.target.checked)}
                className="sr-only peer"
              />
              <div className="w-10 h-5.5 bg-[#DEE0E3] peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[3px] after:left-[3px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-[#3370FF]"></div>
            </label>
          </div>

          {/* 1. 重试策略 */}
          <div className="p-4 bg-white border border-[#EFF0F1] rounded-[10px] space-y-3">
            <div className="flex items-center justify-between pb-2 border-b border-[#EFF0F1]">
              <span className="text-[13px] font-semibold text-[#1F2329]">
                重试策略 (Retry)
              </span>
              <label className="flex items-center gap-1.5 text-[12px] text-[#646A73] cursor-pointer">
                <input
                  type="checkbox"
                  checked={retryEnabled}
                  onChange={(e) => setRetryEnabled(e.target.checked)}
                  className="accent-[#3370FF]"
                />
                启用重试
              </label>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  最大尝试次数 (次)
                </label>
                <input
                  type="number"
                  disabled={!retryEnabled}
                  value={String(maxAttempts)}
                  onChange={(e) => setMaxAttempts(Number(e.target.value) || 1)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] disabled:bg-[#F2F3F5] disabled:cursor-not-allowed"
                />
              </div>
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  重试等待间隔 (ms)
                </label>
                <input
                  type="number"
                  disabled={!retryEnabled}
                  value={String(retryWaitMs)}
                  onChange={(e) => setRetryWaitMs(Number(e.target.value) || 0)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] disabled:bg-[#F2F3F5] disabled:cursor-not-allowed"
                />
              </div>
            </div>
          </div>

          {/* 2. 限流策略 */}
          <div className="p-4 bg-white border border-[#EFF0F1] rounded-[10px] space-y-3">
            <div className="flex items-center justify-between pb-2 border-b border-[#EFF0F1]">
              <span className="text-[13px] font-semibold text-[#1F2329]">
                QPS 速率限流 (Rate Limiter)
              </span>
              <label className="flex items-center gap-1.5 text-[12px] text-[#646A73] cursor-pointer">
                <input
                  type="checkbox"
                  checked={rateLimitEnabled}
                  onChange={(e) => setRateLimitEnabled(e.target.checked)}
                  className="accent-[#3370FF]"
                />
                启用限流
              </label>
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  单周期允许请求数
                </label>
                <input
                  type="number"
                  disabled={!rateLimitEnabled}
                  value={String(limitForPeriod)}
                  onChange={(e) => setLimitForPeriod(Number(e.target.value) || 1)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] disabled:bg-[#F2F3F5] disabled:cursor-not-allowed"
                />
              </div>
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  刷新周期 (ms)
                </label>
                <input
                  type="number"
                  disabled={!rateLimitEnabled}
                  value={String(limitRefreshPeriodMs)}
                  onChange={(e) => setLimitRefreshPeriodMs(Number(e.target.value) || 1000)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] disabled:bg-[#F2F3F5] disabled:cursor-not-allowed"
                />
              </div>
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  获取许可超时 (ms)
                </label>
                <input
                  type="number"
                  disabled={!rateLimitEnabled}
                  value={String(timeoutDurationMs)}
                  onChange={(e) => setTimeoutDurationMs(Number(e.target.value) || 0)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] disabled:bg-[#F2F3F5] disabled:cursor-not-allowed"
                />
              </div>
            </div>
          </div>

          {/* 3. 熔断降级 */}
          <div className="p-4 bg-white border border-[#EFF0F1] rounded-[10px] space-y-3">
            <div className="flex items-center justify-between pb-2 border-b border-[#EFF0F1]">
              <span className="text-[13px] font-semibold text-[#1F2329]">
                熔断器保护 (Circuit Breaker)
              </span>
              <label className="flex items-center gap-1.5 text-[12px] text-[#646A73] cursor-pointer">
                <input
                  type="checkbox"
                  checked={circuitEnabled}
                  onChange={(e) => setCircuitEnabled(e.target.checked)}
                  className="accent-[#3370FF]"
                />
                启用熔断
              </label>
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  失败率阈值 (%)
                </label>
                <input
                  type="number"
                  disabled={!circuitEnabled}
                  value={String(failureRateThreshold)}
                  onChange={(e) => setFailureRateThreshold(Number(e.target.value) || 50)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] disabled:bg-[#F2F3F5] disabled:cursor-not-allowed"
                />
              </div>
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  慢调用比例阈值 (%)
                </label>
                <input
                  type="number"
                  disabled={!circuitEnabled}
                  value={String(slowCallRateThreshold)}
                  onChange={(e) => setSlowCallRateThreshold(Number(e.target.value) || 70)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] disabled:bg-[#F2F3F5] disabled:cursor-not-allowed"
                />
              </div>
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  慢调用判定标准 (ms)
                </label>
                <input
                  type="number"
                  disabled={!circuitEnabled}
                  value={String(slowCallDurationMs)}
                  onChange={(e) => setSlowCallDurationMs(Number(e.target.value) || 3000)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] disabled:bg-[#F2F3F5] disabled:cursor-not-allowed"
                />
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4 pt-1">
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  统计滑动窗口大小
                </label>
                <input
                  type="number"
                  disabled={!circuitEnabled}
                  value={String(slidingWindowSize)}
                  onChange={(e) => setSlidingWindowSize(Number(e.target.value) || 20)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] disabled:bg-[#F2F3F5] disabled:cursor-not-allowed"
                />
              </div>
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  最小触发样本数
                </label>
                <input
                  type="number"
                  disabled={!circuitEnabled}
                  value={String(minimumNumberOfCalls)}
                  onChange={(e) => setMinimumNumberOfCalls(Number(e.target.value) || 10)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] disabled:bg-[#F2F3F5] disabled:cursor-not-allowed"
                />
              </div>
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  熔断开路恢复时间 (ms)
                </label>
                <input
                  type="number"
                  disabled={!circuitEnabled}
                  value={String(waitDurationInOpenStateMs)}
                  onChange={(e) => setWaitDurationInOpenStateMs(Number(e.target.value) || 10000)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329] disabled:bg-[#F2F3F5] disabled:cursor-not-allowed"
                />
              </div>
            </div>
          </div>

          {/* 4. 并发隔离与超时保护 */}
          <div className="p-4 bg-white border border-[#EFF0F1] rounded-[10px] space-y-3">
            <div className="flex items-center justify-between pb-2 border-b border-[#EFF0F1]">
              <span className="text-[13px] font-semibold text-[#1F2329]">
                并发隔离与超时 (Bulkhead & Timeout)
              </span>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  最大并发调用数
                </label>
                <input
                  type="number"
                  value={String(maxConcurrentCalls)}
                  onChange={(e) => setMaxConcurrentCalls(Number(e.target.value) || 20)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329]"
                />
              </div>
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  同步调用超时保护 (ms)
                </label>
                <input
                  type="number"
                  value={String(timeLimiterTimeoutMs)}
                  onChange={(e) => setTimeLimiterTimeoutMs(Number(e.target.value) || 30000)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329]"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4 pt-1">
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  流式首包等待超时 (ms)
                </label>
                <input
                  type="number"
                  value={String(streamFirstChunkTimeoutMs)}
                  onChange={(e) => setStreamFirstChunkTimeoutMs(Number(e.target.value) || 8000)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329]"
                />
              </div>
              <div>
                <label className="block text-[12px] text-[#646A73] mb-1">
                  流式单次最大持续时间 (ms)
                </label>
                <input
                  type="number"
                  value={String(streamMaxDurationMs)}
                  onChange={(e) => setStreamMaxDurationMs(Number(e.target.value) || 120000)}
                  className="w-full h-[36px] px-3 text-[14px] bg-white border border-[#DEE0E3] rounded-[6px] focus:border-[#3370FF] outline-none text-[#1F2329]"
                />
              </div>
            </div>
          </div>
        </div>

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
            onClick={handleSave}
            disabled={saving || loading}
            className="h-[32px] px-4 rounded-[6px] bg-[#3370FF] hover:bg-[#2860E1] active:scale-[0.98] text-[14px] text-white font-normal transition-all cursor-pointer disabled:opacity-50"
          >
            {saving ? "正在保存…" : "保存治理配置"}
          </button>
        </div>
      </div>
    </div>
  );
};

import React, { useState, useMemo } from "react";
import {
  GitCommit,
  History,
  RotateCcw,
  GitCompare,
  UploadCloud,
  ChevronRight,
  ChevronLeft,
  Loader2,
  Clock,
  User,
  Sliders,
} from "lucide-react";
import { PromptResponse, PromptVersionVO } from "../../../types";
import { FeishuPill } from "../../../components/ui/feishu-table";
import { FeishuSelect, FeishuSelectOption } from "../../../components/ui/feishu-select";

interface PromptInspectorProps {
  prompt: PromptResponse;
  currentStableVersionId?: number;
  currentCanaryVersionId?: number;
  canaryPercentage: number;
  onCanaryPercentageChange: (val: number) => void;
  selectedCanaryVersionId: number | string;
  onSelectedCanaryVersionIdChange: (val: number | string) => void;
  onUpdateCanaryRelease: () => void;
  isUpdatingCanary: boolean;
  onRollback: (versionId: number) => void;
  rollingBackVersionId: number | null;
  onLoadVersionToEditor: (content: string) => void;
  onOpenDiffModal: (ver: PromptVersionVO) => void;
  schemaVariables: string[];
  isOpen: boolean;
  onToggleOpen: () => void;
  className?: string;
}

export const PromptInspector: React.FC<PromptInspectorProps> = ({
  prompt,
  currentStableVersionId,
  currentCanaryVersionId,
  canaryPercentage,
  onCanaryPercentageChange,
  selectedCanaryVersionId,
  onSelectedCanaryVersionIdChange,
  onUpdateCanaryRelease,
  isUpdatingCanary,
  onRollback,
  rollingBackVersionId,
  onLoadVersionToEditor,
  onOpenDiffModal,
  schemaVariables,
  isOpen,
  onToggleOpen,
  className = "",
}) => {
  const [activeSubTab, setActiveSubTab] = useState<"canary" | "history">("canary");

  const versions = prompt.versions || [];

  // 构造 FeishuSelect 下拉选项
  const canarySelectOptions: FeishuSelectOption[] = useMemo(() => {
    const defaultOption: FeishuSelectOption = {
      value: "",
      label: "关闭灰度 (100% 走稳定版)",
      pillVariant: "gray",
    };

    const verOptions: FeishuSelectOption[] = versions.map((ver) => ({
      value: String(ver.versionId),
      label: `Version #${ver.versionId} (${ver.content?.slice(0, 20)}…)`,
      pillVariant: ver.versionId === currentStableVersionId ? "green" : "orange",
    }));

    return [defaultOption, ...verOptions];
  }, [versions, currentStableVersionId]);

  if (!isOpen) {
    return (
      <div className="flex flex-col items-center py-4 bg-white border-l border-[#EFF0F1] w-10 shrink-0 select-none">
        <button
          type="button"
          onClick={onToggleOpen}
          title="展开发布与治理面板"
          className="flex h-7 w-7 items-center justify-center rounded-[6px] text-[#646A73] hover:text-[#1F2329] hover:bg-[#F2F3F5] transition-colors cursor-pointer"
        >
          <ChevronLeft className="size-4" />
        </button>
        <span className="[writing-mode:vertical-lr] text-[12px] text-[#8F959E] font-medium tracking-widest mt-6">
          发布与版本治理
        </span>
      </div>
    );
  }

  return (
    <aside
      className={`w-[320px] flex flex-col h-full bg-white border-l border-[#EFF0F1] select-none shrink-0 ${className}`}
    >
      {/* 1. 顶部 Header 与折叠按钮 */}
      <div className="flex items-center justify-between px-4 py-2.5 bg-white border-b border-[#EFF0F1] shrink-0">
        <div className="flex items-center gap-2">
          <Sliders className="size-4 text-[#3370FF]" />
          <h3 className="text-[14px] font-semibold text-[#1F2329]">发布与版本治理</h3>
        </div>

        <button
          type="button"
          onClick={onToggleOpen}
          title="收起治理面板"
          className="flex h-7 w-7 items-center justify-center rounded-[6px] text-[#8F959E] hover:text-[#1F2329] hover:bg-[#F2F3F5] transition-colors cursor-pointer"
        >
          <ChevronRight className="size-4" />
        </button>
      </div>

      {/* 2. 飞书 1:1 分段切换器 (纯白背景 + 微边框) */}
      <div className="p-3 pb-0 shrink-0 bg-white">
        <div className="flex items-center bg-white border border-[#DEE0E3] p-0.5 rounded-[6px] text-[13px]">
          <button
            type="button"
            onClick={() => setActiveSubTab("canary")}
            className={`flex-1 py-1 rounded-[4px] font-normal transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
              activeSubTab === "canary"
                ? "bg-[#E8F3FF] text-[#3370FF] font-medium"
                : "text-[#646A73] hover:text-[#1F2329] hover:bg-[#F2F3F5]"
            }`}
          >
            <GitCommit className="size-3.5" /> 灰度切流 (Canary)
          </button>

          <button
            type="button"
            onClick={() => setActiveSubTab("history")}
            className={`flex-1 py-1 rounded-[4px] font-normal transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
              activeSubTab === "history"
                ? "bg-[#E8F3FF] text-[#3370FF] font-medium"
                : "text-[#646A73] hover:text-[#1F2329] hover:bg-[#F2F3F5]"
            }`}
          >
            <History className="size-3.5" /> 版本历史 ({versions.length})
          </button>
        </div>
      </div>

      {/* 3. 内容展示区 (纯白背景) */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3 bg-white">
        {/* TAB 1: 灰度切流分发设置 */}
        {activeSubTab === "canary" && (
          <div className="space-y-3">
            {/* 状态总览卡片 */}
            <div className="p-3.5 bg-white rounded-[8px] border border-[#DEE0E3] space-y-2.5 shadow-2xs">
              <div className="text-[13px] font-medium text-[#1F2329]">当前线上生效策略</div>

              <div className="flex items-center justify-between text-[13px]">
                <span className="text-[#646A73]">正式稳定版:</span>
                <FeishuPill variant="green" showDot={false}>
                  Version #{currentStableVersionId || "1"}
                </FeishuPill>
              </div>

              <div className="flex items-center justify-between text-[13px]">
                <span className="text-[#646A73]">灰度分流版:</span>
                {currentCanaryVersionId ? (
                  <FeishuPill variant="orange" showDot={false}>
                    Version #{currentCanaryVersionId} ({canaryPercentage}%)
                  </FeishuPill>
                ) : (
                  <span className="text-[#8F959E]">未开启灰度</span>
                )}
              </div>
            </div>

            {/* 目标灰度版本选择 (FeishuSelect) */}
            <div className="p-3.5 bg-white rounded-[8px] border border-[#DEE0E3] space-y-3 shadow-2xs">
              <div>
                <label className="block text-[13px] font-medium text-[#1F2329] mb-1.5">
                  选择灰度目标版本
                </label>
                <FeishuSelect
                  options={canarySelectOptions}
                  value={String(selectedCanaryVersionId || "")}
                  onChange={(val) => onSelectedCanaryVersionIdChange(val)}
                  size="md"
                  placeholder="选择灰度版本"
                  className="w-full"
                />
              </div>

              {/* 流量滑块与百分比 */}
              <div className="space-y-2 pt-2 border-t border-[#EFF0F1]">
                <div className="flex items-center justify-between text-[13px]">
                  <span className="text-[#646A73]">分流比例配比:</span>
                  <span className="font-bold text-[#3370FF] tabular-nums">
                    {100 - canaryPercentage}% vs {canaryPercentage}%
                  </span>
                </div>

                {/* 配比色彩条 */}
                <div className="h-2 w-full rounded-full bg-[#E6F8F5] overflow-hidden flex">
                  <div
                    className="bg-[#00B42A] h-full transition-all duration-150"
                    style={{ width: `${100 - canaryPercentage}%` }}
                    title={`稳定版 ${100 - canaryPercentage}%`}
                  />
                  <div
                    className="bg-[#FF7D00] h-full transition-all duration-150"
                    style={{ width: `${canaryPercentage}%` }}
                    title={`灰度版 ${canaryPercentage}%`}
                  />
                </div>

                <input
                  type="range"
                  min="0"
                  max="100"
                  step="5"
                  value={canaryPercentage}
                  onChange={(e) => onCanaryPercentageChange(parseInt(e.target.value, 10))}
                  className="w-full accent-[#3370FF] cursor-pointer"
                />

                <div className="flex justify-between text-[11px] text-[#8F959E] tabular-nums">
                  <span>0% (全量稳定)</span>
                  <span>50%</span>
                  <span>100% (全量灰度)</span>
                </div>
              </div>

              {/* 发布切流规则按钮 (飞书主行动点 32px) */}
              <button
                type="button"
                onClick={onUpdateCanaryRelease}
                disabled={isUpdatingCanary}
                className="w-full h-[32px] mt-1 inline-flex items-center justify-center gap-1.5 rounded-[6px] bg-[#3370FF] hover:bg-[#2860E1] text-[14px] font-normal text-white shadow-none transition-colors cursor-pointer disabled:opacity-50"
              >
                {isUpdatingCanary ? (
                  <Loader2 className="size-3.5 animate-spin" />
                ) : (
                  <UploadCloud className="size-3.5" />
                )}
                发布当前切流规则 (Release)
              </button>
            </div>

            {/* 变量契约清单 */}
            <div className="p-3.5 bg-white rounded-[8px] border border-[#DEE0E3] space-y-2 shadow-2xs">
              <div className="text-[13px] font-medium text-[#1F2329]">已声明的变量契约</div>
              <div className="flex flex-wrap gap-1.5">
                {schemaVariables.length > 0 ? (
                  schemaVariables.map((v) => (
                    <span
                      key={v}
                      className="px-2 py-0.5 bg-[#E8F3FF] text-[#3370FF] rounded-[4px] text-[12px] border border-[#B3D4FF]/50"
                    >
                      {"{{" + v + "}}"}
                    </span>
                  ))
                ) : (
                  <span className="text-[12px] text-[#8F959E]">自由 Handlebars 变量</span>
                )}
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: 版本历史演进与一键回滚 */}
        {activeSubTab === "history" && (
          <div className="space-y-2">
            {versions.length === 0 ? (
              <div className="p-6 text-center text-[13px] text-[#8F959E] bg-white rounded-[8px] border border-[#DEE0E3]">
                暂无历史版本记录
              </div>
            ) : (
              versions.map((ver) => {
                const isStable = ver.versionId === currentStableVersionId;
                const isCanary = ver.versionId === currentCanaryVersionId;
                const isRolling = rollingBackVersionId === ver.versionId;

                return (
                  <div
                    key={ver.versionId}
                    className={`p-3 rounded-[8px] border bg-white space-y-2 transition-all shadow-2xs ${
                      isStable
                        ? "border-[#00B42A]/40 bg-[#F6FDF9]"
                        : isCanary
                        ? "border-[#FF7D00]/40 bg-[#FFFDF9]"
                        : "border-[#DEE0E3] hover:border-[#3370FF]/40"
                    }`}
                  >
                    {/* 头部版本号与标签 */}
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-[14px] text-[#1F2329] tabular-nums">
                        Version #{ver.versionId}
                      </span>
                      {isStable && (
                        <FeishuPill variant="green" showDot={false}>
                          当前稳定版
                        </FeishuPill>
                      )}
                      {isCanary && (
                        <FeishuPill variant="orange" showDot={false}>
                          灰度 {canaryPercentage}%
                        </FeishuPill>
                      )}
                    </div>

                    {/* 提交人与时间 */}
                    <div className="flex items-center gap-2 text-[12px] text-[#8F959E]">
                      {ver.createdBy && (
                        <span className="flex items-center gap-1">
                          <User className="size-3" /> {ver.createdBy}
                        </span>
                      )}
                      {(ver.createdAt || ver.createdTime) && (
                        <span className="flex items-center gap-1">
                          <Clock className="size-3" /> {ver.createdAt || ver.createdTime}
                        </span>
                      )}
                    </div>

                    {/* 正文摘要 (纯白底色) */}
                    <div className="p-2 bg-white rounded-[4px] border border-[#EFF0F1] font-mono text-[12px] text-[#646A73] line-clamp-2 leading-relaxed">
                      {ver.content}
                    </div>

                    {/* 动作栏 */}
                    <div className="flex items-center justify-end gap-1.5 pt-1.5 border-t border-[#EFF0F1]">
                      <button
                        type="button"
                        onClick={() => onLoadVersionToEditor(ver.content)}
                        title="将该版本正文载入当前编辑器"
                        className="h-[26px] px-2 rounded-[6px] border border-[#DEE0E3] bg-white text-[12px] text-[#1F2329] hover:bg-[#F2F3F5] transition-colors cursor-pointer"
                      >
                        载入编辑器
                      </button>

                      <button
                        type="button"
                        onClick={() => onOpenDiffModal(ver)}
                        title="与线上稳定版对比差异"
                        className="h-[26px] px-2 rounded-[6px] border border-[#DEE0E3] bg-white text-[12px] text-[#3370FF] hover:bg-[#E8F3FF] transition-colors cursor-pointer flex items-center gap-0.5"
                      >
                        <GitCompare className="size-3" /> Diff
                      </button>

                      {!isStable && (
                        <button
                          type="button"
                          onClick={() => onRollback(ver.versionId)}
                          disabled={isRolling}
                          title="将线上稳定版回滚到该版本"
                          className="h-[26px] px-2 rounded-[6px] border border-[#DEE0E3] bg-white text-[12px] text-[#F53F3F] hover:bg-[#FFF2F0] hover:border-[#F53F3F]/40 transition-colors cursor-pointer disabled:opacity-50 flex items-center gap-0.5"
                        >
                          {isRolling ? (
                            <Loader2 className="size-3 animate-spin" />
                          ) : (
                            <RotateCcw className="size-3" />
                          )}
                          回滚
                        </button>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}
      </div>
    </aside>
  );
};

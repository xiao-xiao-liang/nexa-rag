import React, { useState, useEffect, useMemo, useRef } from "react";
import {
  Sliders,
  RefreshCw,
  CheckCircle2,
  AlertCircle,
  X,
} from "lucide-react";
import { promptApi } from "../../lib/api";
import { PromptResponse, PromptVersionVO } from "../../types";
import { FEISHU_FONT_FAMILY } from "../../components/ui/feishu-table";
import { FeishuEmptyState } from "../../components/ui/feishu-table/FeishuEmptyState";
import { PromptSidebar } from "./components/PromptSidebar";
import { PromptCanvas, CanvasViewMode } from "./components/PromptCanvas";
import { PromptInspector } from "./components/PromptInspector";
import { PromptMetaModal } from "./components/PromptMetaModal";
import { PromptDiffViewer } from "./components/PromptDiffViewer";

export const PromptPage: React.FC = () => {
  const [prompts, setPrompts] = useState<PromptResponse[]>([]);
  const [selectedPrompt, setSelectedPrompt] = useState<PromptResponse | null>(null);
  const [editorContent, setEditorContent] = useState<string>("");
  const [previewContent, setPreviewContent] = useState<string>("");
  const [previewLoading, setPreviewLoading] = useState<boolean>(false);
  const [viewMode, setViewMode] = useState<CanvasViewMode>("editor");

  // 灰度百分比与目标版本
  const [canaryPercentage, setCanaryPercentage] = useState<number>(20);
  const [selectedCanaryVersionId, setSelectedCanaryVersionId] = useState<number | string>("");

  // 状态反馈与操作加载
  const [toastFeedback, setToastFeedback] = useState<{ message: string; type?: "success" | "error" } | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isUpdatingCanary, setIsUpdatingCanary] = useState<boolean>(false);
  const [isTogglingEnabled, setIsTogglingEnabled] = useState<boolean>(false);
  const [rollingBackVersionId, setRollingBackVersionId] = useState<number | null>(null);

  // 弹窗与面板控制
  const [isMetaModalOpen, setIsMetaModalOpen] = useState(false);
  const [diffModalVersion, setDiffModalVersion] = useState<PromptVersionVO | null>(null);
  const [isInspectorOpen, setIsInspectorOpen] = useState(true);

  // 防抖预览 Timer
  const previewTimerRef = useRef<number | null>(null);

  useEffect(() => {
    loadPrompts();
  }, []);

  const showToast = (message: string, type: "success" | "error" = "success") => {
    setToastFeedback({ message, type });
    setTimeout(() => {
      setToastFeedback(null);
    }, 3500);
  };

  const extractCurrentContent = (p: PromptResponse | null): string => {
    if (!p) return "";
    if (p.latestContent) return p.latestContent;
    if (p.versions && p.versions.length > 0) {
      return p.versions[0].content || "";
    }
    return "";
  };

  const loadPrompts = async (targetCode?: string) => {
    try {
      const data = await promptApi.listPrompts();
      setPrompts(data || []);
      if (data && data.length > 0) {
        const target = targetCode
          ? data.find((p) => p.promptCode === targetCode) || data[0]
          : data[0];
        await selectPrompt(target.promptCode);
      } else {
        setSelectedPrompt(null);
        setEditorContent("");
        setPreviewContent("");
      }
    } catch (err: any) {
      console.warn("加载 Prompt 列表失败:", err);
      showToast(err.message || "加载 Prompt 列表失败", "error");
    }
  };

  const selectPrompt = async (promptCode: string) => {
    try {
      const detail = await promptApi.getPrompt(promptCode);
      setSelectedPrompt(detail);
      const content = extractCurrentContent(detail);
      setEditorContent(content);

      // 解析当前生效的灰度规则
      const latestRelease = detail.releases && detail.releases.length > 0 ? detail.releases[0] : null;
      if (latestRelease?.canaryRule) {
        try {
          const rule = JSON.parse(latestRelease.canaryRule);
          if (rule && typeof rule.percentage === "number") {
            setCanaryPercentage(rule.percentage);
          }
        } catch {
          // 使用默认值
        }
      } else if (detail.canaryPercentage !== undefined) {
        setCanaryPercentage(detail.canaryPercentage);
      }

      if (latestRelease?.canaryVersionId) {
        setSelectedCanaryVersionId(latestRelease.canaryVersionId);
      } else if (detail.versions && detail.versions.length > 1) {
        setSelectedCanaryVersionId(detail.versions[1].versionId);
      } else {
        setSelectedCanaryVersionId("");
      }

      // 如果有内容，预先加载脱敏渲染
      if (content && content.trim()) {
        handlePreview(detail.promptCode, content);
      } else {
        setPreviewContent("");
      }
    } catch (err: any) {
      console.error(`加载 Prompt [${promptCode}] 详情失败:`, err);
      showToast(`加载 Prompt [${promptCode}] 失败`, "error");
    }
  };

  const handlePreview = async (code: string, content: string) => {
    if (!code || !content || !content.trim()) {
      setPreviewContent("");
      setPreviewLoading(false);
      return;
    }

    setPreviewLoading(true);
    try {
      const res = await promptApi.previewPrompt(code, content);
      setPreviewContent(res || "");
    } catch (err: any) {
      setPreviewContent(`[脱敏渲染失败]: ${err.message || "无法生成预览"}`);
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleContentChange = (val: string) => {
    setEditorContent(val);
    if (!selectedPrompt) return;

    if (previewTimerRef.current) {
      window.clearTimeout(previewTimerRef.current);
    }

    // 防抖 300ms 刷新预览
    previewTimerRef.current = window.setTimeout(() => {
      if (val && val.trim()) {
        handlePreview(selectedPrompt.promptCode, val);
      } else {
        setPreviewContent("");
      }
    }, 300);
  };

  const handleToggleEnabled = async () => {
    if (!selectedPrompt || isTogglingEnabled) return;
    const nextState = !selectedPrompt.enabled;
    setIsTogglingEnabled(true);
    try {
      await promptApi.updatePrompt(selectedPrompt.promptCode, {
        enabled: nextState,
      });
      setSelectedPrompt((prev) => (prev ? { ...prev, enabled: nextState } : null));
      setPrompts((prev) =>
        prev.map((p) =>
          p.promptCode === selectedPrompt.promptCode ? { ...p, enabled: nextState } : p
        )
      );
      showToast(nextState ? "Prompt 模板已成功启用" : "Prompt 模板已停用");
    } catch (err: any) {
      showToast(err.message || "更新启用状态失败", "error");
    } finally {
      setIsTogglingEnabled(false);
    }
  };

  const handleSubmitRelease = async () => {
    if (!selectedPrompt || !editorContent.trim()) {
      showToast("Prompt 模板正文不能为空", "error");
      return;
    }

    setIsSubmitting(true);
    try {
      await promptApi.submitPrompt(selectedPrompt.promptCode, editorContent);
      showToast("新版本已提交并发布为正式稳定版");
      await loadPrompts(selectedPrompt.promptCode);
      setViewMode("editor");
    } catch (err: any) {
      showToast(err.message || "提交发布新版本失败", "error");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResetContent = () => {
    if (!selectedPrompt) return;
    const original = extractCurrentContent(selectedPrompt);
    handleContentChange(original);
    showToast("已还原为当前版本内容");
  };

  const handleUpdateCanaryRelease = async () => {
    if (!selectedPrompt) return;
    const latestRelease =
      selectedPrompt.releases && selectedPrompt.releases.length > 0
        ? selectedPrompt.releases[0]
        : null;
    const stableVersionId =
      latestRelease?.stableVersionId ||
      (selectedPrompt.versions && selectedPrompt.versions[0]?.versionId) ||
      1;
    const canaryVerId = selectedCanaryVersionId ? Number(selectedCanaryVersionId) : undefined;

    setIsUpdatingCanary(true);
    try {
      await promptApi.releasePrompt(
        selectedPrompt.promptCode,
        stableVersionId,
        canaryVerId,
        canaryPercentage
      );
      showToast(`灰度流量切流已成功更新为 ${canaryPercentage}%`);
      await loadPrompts(selectedPrompt.promptCode);
    } catch (err: any) {
      showToast(err.message || "更新灰度切流失败", "error");
    } finally {
      setIsUpdatingCanary(false);
    }
  };

  const handleRollback = async (versionId: number) => {
    if (!selectedPrompt) return;
    setRollingBackVersionId(versionId);
    try {
      await promptApi.rollbackPrompt(selectedPrompt.promptCode, versionId);
      showToast(`已成功回滚至历史版本 #${versionId}`);
      await loadPrompts(selectedPrompt.promptCode);
    } catch (err: any) {
      showToast(err.message || "回滚版本失败", "error");
    } finally {
      setRollingBackVersionId(null);
    }
  };

  const currentStableVersionId = useMemo(() => {
    if (!selectedPrompt) return undefined;
    const release =
      selectedPrompt.releases && selectedPrompt.releases.length > 0
        ? selectedPrompt.releases[0]
        : null;
    return (
      release?.stableVersionId ||
      selectedPrompt.activeStableVersionId ||
      selectedPrompt.versions?.[0]?.versionId
    );
  }, [selectedPrompt]);

  const currentStableVersion = useMemo(() => {
    if (!selectedPrompt || !selectedPrompt.versions) return null;
    return (
      selectedPrompt.versions.find((v) => v.versionId === currentStableVersionId) ||
      selectedPrompt.versions[0] ||
      null
    );
  }, [selectedPrompt, currentStableVersionId]);

  const currentStableContent = useMemo(() => {
    return currentStableVersion?.content || extractCurrentContent(selectedPrompt);
  }, [currentStableVersion, selectedPrompt]);

  const currentCanaryVersionId = useMemo(() => {
    if (!selectedPrompt) return undefined;
    const release =
      selectedPrompt.releases && selectedPrompt.releases.length > 0
        ? selectedPrompt.releases[0]
        : null;
    return release?.canaryVersionId || selectedPrompt.activeCanaryVersionId;
  }, [selectedPrompt]);

  // 解析 Schema 声明的必填变量
  const schemaVariables = useMemo<string[]>(() => {
    if (!selectedPrompt?.variableSchema) return [];
    try {
      const parsed = JSON.parse(selectedPrompt.variableSchema);
      if (Array.isArray(parsed)) return parsed.map(String);
      if (parsed.required && Array.isArray(parsed.required)) return parsed.required.map(String);
      if (parsed.properties) return Object.keys(parsed.properties);
      return [];
    } catch {
      return [];
    }
  }, [selectedPrompt]);

  // 当前正文是否有未发布的变更
  const isDirty = useMemo(() => {
    if (!selectedPrompt) return false;
    return editorContent !== currentStableContent;
  }, [editorContent, currentStableContent, selectedPrompt]);

  return (
    <div
      style={{ fontFamily: FEISHU_FONT_FAMILY }}
      className="flex flex-col h-[calc(100vh-48px)] w-full bg-white select-none overflow-hidden"
    >
      {/* 1. 飞书标准纯白 Header 标题栏 */}
      <header className="flex items-center justify-between px-6 py-3 border-b border-[#EFF0F1] bg-white shrink-0 z-10">
        <div>
          <h1 className="text-[18px] font-semibold text-[#1F2329] tracking-tight leading-tight flex items-center gap-2">
            <Sliders className="size-4.5 text-[#3370FF]" />
            Prompt 在线工坊与治理中心
          </h1>
          <p className="mt-1 text-[13px] text-[#646A73]">
            集中管理 RAG 提示词模板契约、Handlebars 变量注入与网关动态灰度切流调度
          </p>
        </div>

        <div className="flex items-center gap-3">
          {/* 飞书标准 Toast 提示胶囊 */}
          {toastFeedback && (
            <div
              className={`inline-flex items-center gap-1.5 rounded-full px-3.5 py-1 text-[13px] font-medium animate-in fade-in duration-150 ${
                toastFeedback.type === "error"
                  ? "bg-[#FFF2F0] text-[#F53F3F]"
                  : "bg-[#E6F8F5] text-[#10A893]"
              }`}
            >
              {toastFeedback.type === "error" ? (
                <AlertCircle className="size-3.5" />
              ) : (
                <CheckCircle2 className="size-3.5" />
              )}
              {toastFeedback.message}
            </div>
          )}

          {/* 飞书次级线框按钮 */}
          <button
            type="button"
            onClick={() => loadPrompts(selectedPrompt?.promptCode)}
            className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] border border-[#DEE0E3] bg-white px-3.5 text-[14px] font-normal text-[#1F2329] hover:bg-[#F2F3F5] active:bg-[#E5E6EB] transition-colors shadow-none cursor-pointer"
          >
            <RefreshCw className="size-3.5 text-[#646A73]" />
            刷新数据
          </button>
        </div>
      </header>

      {/* 2. 三段式核心工程工作台 (全纯白底色) */}
      <div className="flex flex-1 min-h-0 overflow-hidden bg-white">
        {/* A. 左侧模板导航树 (纯白背景) */}
        <PromptSidebar
          prompts={prompts}
          selectedPrompt={selectedPrompt}
          onSelectPrompt={selectPrompt}
          className="w-[260px] shrink-0"
        />

        {/* B. 中央沉浸式编辑器画布 (纯白背景) */}
        {selectedPrompt ? (
          <PromptCanvas
            prompt={selectedPrompt}
            editorContent={editorContent}
            onContentChange={handleContentChange}
            previewContent={previewContent}
            previewLoading={previewLoading}
            onRefreshPreview={() => handlePreview(selectedPrompt.promptCode, editorContent)}
            viewMode={viewMode}
            onViewModeChange={setViewMode}
            onResetContent={handleResetContent}
            onSubmitRelease={handleSubmitRelease}
            isSubmitting={isSubmitting}
            isDirty={isDirty}
            onOpenMetaModal={() => setIsMetaModalOpen(true)}
            onToggleEnabled={handleToggleEnabled}
            isTogglingEnabled={isTogglingEnabled}
            stableContent={currentStableContent}
            schemaVariables={schemaVariables}
            className="flex-1 min-w-0 bg-white"
          />
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center p-12 bg-white">
            <FeishuEmptyState
              title="未选择 Prompt 模板"
              description="请在左侧列表中选择一个 Prompt 模板开始在线编辑与灰度调控"
            />
          </div>
        )}

        {/* C. 右侧发布与治理 Inspector (纯白背景) */}
        {selectedPrompt && (
          <PromptInspector
            prompt={selectedPrompt}
            currentStableVersionId={currentStableVersionId}
            currentCanaryVersionId={currentCanaryVersionId}
            canaryPercentage={canaryPercentage}
            onCanaryPercentageChange={setCanaryPercentage}
            selectedCanaryVersionId={selectedCanaryVersionId}
            onSelectedCanaryVersionIdChange={setSelectedCanaryVersionId}
            onUpdateCanaryRelease={handleUpdateCanaryRelease}
            isUpdatingCanary={isUpdatingCanary}
            onRollback={handleRollback}
            rollingBackVersionId={rollingBackVersionId}
            onLoadVersionToEditor={(c) => {
              handleContentChange(c);
              showToast("已将历史版本内容载入编辑器");
            }}
            onOpenDiffModal={setDiffModalVersion}
            schemaVariables={schemaVariables}
            isOpen={isInspectorOpen}
            onToggleOpen={() => setIsInspectorOpen((prev) => !prev)}
            className="bg-white"
          />
        )}
      </div>

      {/* 3. 元数据与契约编辑弹窗 */}
      <PromptMetaModal
        isOpen={isMetaModalOpen}
        onClose={() => setIsMetaModalOpen(false)}
        prompt={selectedPrompt}
        onSuccess={(updated) => {
          setSelectedPrompt((prev) => (prev ? { ...prev, ...updated } : null));
          setPrompts((prev) =>
            prev.map((p) => (p.promptCode === updated.promptCode ? { ...p, ...updated } : p))
          );
          showToast("Prompt 基础信息已成功更新");
        }}
      />

      {/* 4. 历史版本 Diff 模态弹窗 */}
      {diffModalVersion && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1F2329]/40 backdrop-blur-[1px] animate-in fade-in duration-100">
          <div className="w-full max-w-[880px] h-[80vh] bg-white rounded-[12px] border border-[#DEE0E3] shadow-[0_8px_32px_rgba(31,35,41,0.12)] p-6 flex flex-col animate-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between pb-4 border-b border-[#EFF0F1] shrink-0">
              <div>
                <h4 className="text-[16px] font-semibold text-[#1F2329]">
                  历史版本正文差异比对 (Diff)
                </h4>
                <p className="text-[12px] text-[#646A73] mt-0.5">
                  比较 Version #{diffModalVersion.versionId} 与 线上正式稳定版 #
                  {currentStableVersionId || "1"}
                </p>
              </div>

              <button
                type="button"
                onClick={() => setDiffModalVersion(null)}
                className="flex h-7 w-7 items-center justify-center rounded-[6px] text-[#8F959E] hover:bg-[#F2F3F5] hover:text-[#1F2329] transition-colors cursor-pointer"
              >
                <X className="size-4" />
              </button>
            </div>

            <div className="flex-1 min-h-0 pt-4">
              <PromptDiffViewer
                oldContent={currentStableContent}
                newContent={diffModalVersion.content}
                oldTitle={`线上稳定版 v${currentStableVersionId || 1}`}
                newTitle={`历史版本 v${diffModalVersion.versionId}`}
                className="h-full shadow-none border-[#DEE0E3]"
              />
            </div>

            <div className="pt-4 border-t border-[#EFF0F1] flex items-center justify-between shrink-0">
              <button
                type="button"
                onClick={() => {
                  handleContentChange(diffModalVersion.content);
                  setDiffModalVersion(null);
                  showToast(`已载入 Version #${diffModalVersion.versionId} 到编辑器`);
                }}
                className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] border border-[#DEE0E3] bg-white px-3.5 text-[14px] font-normal text-[#1F2329] hover:bg-[#F2F3F5] transition-colors cursor-pointer shadow-none"
              >
                载入此版本至编辑器
              </button>

              <button
                type="button"
                onClick={() => setDiffModalVersion(null)}
                className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] bg-[#3370FF] px-4 text-[14px] font-normal text-white hover:bg-[#2860E1] transition-colors cursor-pointer shadow-none"
              >
                完成
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

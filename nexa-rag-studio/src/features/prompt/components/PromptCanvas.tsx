import React, { useRef, useMemo } from "react";
import {
  FileCode,
  Eye,
  GitCompare,
  RotateCcw,
  Save,
  Loader2,
  Copy,
  Check,
  Code2,
  Sparkles,
  RefreshCw,
  Edit3,
} from "lucide-react";
import { PromptResponse } from "../../../types";
import { PromptDiffViewer } from "./PromptDiffViewer";
import { FeishuEmptyState } from "../../../components/ui/feishu-table/FeishuEmptyState";

export type CanvasViewMode = "editor" | "preview" | "diff";

interface PromptCanvasProps {
  prompt: PromptResponse;
  editorContent: string;
  onContentChange: (val: string) => void;
  previewContent: string;
  previewLoading: boolean;
  onRefreshPreview: () => void;
  viewMode: CanvasViewMode;
  onViewModeChange: (mode: CanvasViewMode) => void;
  onResetContent: () => void;
  onSubmitRelease: () => void;
  isSubmitting: boolean;
  isDirty: boolean;
  onOpenMetaModal: () => void;
  onToggleEnabled: () => void;
  isTogglingEnabled: boolean;
  stableContent: string;
  schemaVariables: string[];
  className?: string;
}

export const PromptCanvas: React.FC<PromptCanvasProps> = ({
  prompt,
  editorContent,
  onContentChange,
  previewContent,
  previewLoading,
  onRefreshPreview,
  viewMode,
  onViewModeChange,
  onResetContent,
  onSubmitRelease,
  isSubmitting,
  isDirty,
  onOpenMetaModal,
  onToggleEnabled,
  isTogglingEnabled,
  stableContent,
  schemaVariables,
  className = "",
}) => {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [copiedCode, setCopiedCode] = React.useState(false);
  const [copiedPreview, setCopiedPreview] = React.useState(false);

  const handleCopyCode = (e: React.MouseEvent) => {
    e.stopPropagation();
    navigator.clipboard.writeText(prompt.promptCode);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  const handleCopyPreview = () => {
    navigator.clipboard.writeText(previewContent);
    setCopiedPreview(true);
    setTimeout(() => setCopiedPreview(false), 2000);
  };

  // 从正文中提取变量
  const detectedVariables = useMemo(() => {
    if (!editorContent) return [];
    const regex = /\{\{\s*([a-zA-Z0-9_]+)\s*\}\}/g;
    const vars = new Set<string>();
    let match: RegExpExecArray | null;
    while ((match = regex.exec(editorContent)) !== null) {
      if (match[1]) vars.add(match[1]);
    }
    return Array.from(vars);
  }, [editorContent]);

  // 合并全部可用变量
  const allVariables = useMemo(() => {
    const combined = Array.from(new Set([...schemaVariables, ...detectedVariables]));
    return combined.length > 0 ? combined : ["context", "query", "history", "document_text"];
  }, [schemaVariables, detectedVariables]);

  // 插入变量至当前光标处
  const handleInsertVariable = (varName: string) => {
    const tag = `{{${varName}}}`;
    const textarea = textareaRef.current;
    if (!textarea) {
      onContentChange(editorContent + tag);
      return;
    }

    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const newContent =
      editorContent.substring(0, start) + tag + editorContent.substring(end);
    onContentChange(newContent);

    setTimeout(() => {
      textarea.focus();
      textarea.setSelectionRange(start + tag.length, start + tag.length);
    }, 0);
  };

  // 统计指标
  const lineCount = useMemo(() => {
    if (!editorContent) return 1;
    return editorContent.split("\n").length;
  }, [editorContent]);

  const estimatedTokens = useMemo(() => {
    if (!editorContent) return 0;
    return Math.ceil(editorContent.length / 2.5);
  }, [editorContent]);

  return (
    <main
      className={`flex flex-col h-full bg-white select-none overflow-hidden ${className}`}
    >
      {/* 1. 画布顶部 Header 栏 (纯白 + 极细分割线) */}
      <div className="flex flex-wrap items-center justify-between gap-3 px-6 py-2.5 border-b border-[#EFF0F1] bg-white shrink-0">
        {/* 左侧：Prompt 元信息 */}
        <div className="flex items-center gap-3 min-w-0">
          <div className="flex items-center gap-1.5 min-w-0">
            <h2
              className="text-[16px] font-semibold text-[#1F2329] truncate"
              title={prompt.name || prompt.promptName || prompt.promptCode}
            >
              {prompt.name || prompt.promptName || prompt.promptCode}
            </h2>

            {/* 幽灵图标按钮 */}
            <button
              type="button"
              onClick={onOpenMetaModal}
              title="编辑 Prompt 基础信息与变量契约"
              className="flex h-7 w-7 items-center justify-center rounded-[6px] text-[#8F959E] hover:bg-[#F2F3F5] hover:text-[#1F2329] transition-colors cursor-pointer"
            >
              <Edit3 className="size-3.5" />
            </button>
          </div>

          <span className="text-[12px] bg-[#E8F3FF] text-[#3370FF] px-2 py-0.5 rounded-[4px] border border-[#B3D4FF]/60 flex items-center gap-1 shrink-0">
            {prompt.promptCode}
            <button
              type="button"
              onClick={handleCopyCode}
              title="复制唯一编码"
              className="flex h-5 w-5 items-center justify-center rounded-[4px] hover:text-[#1F2329] transition-colors cursor-pointer"
            >
              {copiedCode ? (
                <Check className="size-3 text-[#00B42A]" />
              ) : (
                <Copy className="size-3" />
              )}
            </button>
          </span>

          {/* 启用 Switch */}
          <div className="flex items-center gap-1.5 pl-2 border-l border-[#EFF0F1] shrink-0">
            <button
              type="button"
              onClick={onToggleEnabled}
              disabled={isTogglingEnabled}
              title={prompt.enabled !== false ? "点击停用" : "点击启用"}
              className={`relative inline-flex h-4.5 w-8 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                prompt.enabled !== false ? "bg-[#3370FF]" : "bg-[#DEE0E3]"
              }`}
            >
              <span
                className={`pointer-events-none inline-block h-3.5 w-3.5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                  prompt.enabled !== false ? "translate-x-3.5" : "translate-x-0"
                }`}
              />
            </button>
            <span className="text-[12px] text-[#646A73]">
              {prompt.enabled !== false ? "已启用" : "已停用"}
            </span>
          </div>
        </div>

        {/* 右侧：纯白微框分段切换控制器 (Segmented View Controller) */}
        <div className="flex items-center bg-white border border-[#DEE0E3] p-0.5 rounded-[6px] shrink-0">
          <button
            type="button"
            onClick={() => onViewModeChange("editor")}
            className={`inline-flex items-center gap-1.5 px-3.5 py-1 rounded-[4px] text-[14px] transition-all cursor-pointer ${
              viewMode === "editor"
                ? "bg-[#E8F3FF] text-[#3370FF] font-medium"
                : "text-[#646A73] hover:text-[#1F2329] hover:bg-[#F2F3F5]"
            }`}
          >
            <FileCode className="size-3.5" /> 正文编辑
          </button>

          <button
            type="button"
            onClick={() => onViewModeChange("preview")}
            className={`inline-flex items-center gap-1.5 px-3.5 py-1 rounded-[4px] text-[14px] transition-all cursor-pointer ${
              viewMode === "preview"
                ? "bg-[#E8F3FF] text-[#3370FF] font-medium"
                : "text-[#646A73] hover:text-[#1F2329] hover:bg-[#F2F3F5]"
            }`}
          >
            <Eye className="size-3.5" /> 渲染预览
          </button>

          <button
            type="button"
            onClick={() => onViewModeChange("diff")}
            className={`inline-flex items-center gap-1.5 px-3.5 py-1 rounded-[4px] text-[14px] transition-all cursor-pointer ${
              viewMode === "diff"
                ? "bg-[#E8F3FF] text-[#3370FF] font-medium"
                : "text-[#646A73] hover:text-[#1F2329] hover:bg-[#F2F3F5]"
            }`}
          >
            <GitCompare className="size-3.5" /> 改动 Diff
            {isDirty && (
              <span className="size-1.5 rounded-full bg-[#FF7D00]" title="存在未发布的修改" />
            )}
          </button>
        </div>
      </div>

      {/* 2. 核心视图展示区 (全白底色) */}
      <div className="flex-1 flex flex-col min-h-0 bg-white overflow-hidden">
        {/* A. 模式 1: 沉浸式纯白全宽代码编辑器 */}
        {viewMode === "editor" && (
          <div className="flex-1 flex flex-col min-h-0 bg-white">
            {/* 变量药丸快捷栏 (纯白背景 + 纯净微边框) */}
            <div className="flex items-center gap-2 flex-wrap px-6 py-2 bg-white border-b border-[#EFF0F1] shrink-0">
              <span className="text-[13px] text-[#646A73] flex items-center gap-1 font-normal">
                <Code2 className="size-3.5 text-[#3370FF]" /> 点击插入变量：
              </span>
              {allVariables.map((v) => (
                <button
                  key={v}
                  type="button"
                  onClick={() => handleInsertVariable(v)}
                  title={`点击将 {{${v}}} 插入至光标位置`}
                  className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-[6px] bg-white border border-[#DEE0E3] text-[#3370FF] text-[12px] hover:bg-[#E8F3FF] hover:border-[#3370FF]/60 active:scale-95 transition-all cursor-pointer shadow-none"
                >
                  <span className="text-[#8F959E]">+</span>
                  <span>{"{{" + v + "}}"}</span>
                </button>
              ))}
            </div>

            {/* 编辑器主体（全宽沉浸编写） */}
            <div className="flex-1 relative flex min-h-0 p-0 overflow-hidden bg-white">
              <textarea
                ref={textareaRef}
                value={editorContent}
                onChange={(e) => onContentChange(e.target.value)}
                placeholder="请输入 Prompt 模板正文，支持 {{variable}} Handlebars 语法…"
                className="w-full h-full p-6 font-mono text-[13.5px] leading-relaxed border-none outline-none resize-none bg-white text-[#1F2329] selection:bg-[#B3D4FF] overflow-y-auto"
                spellCheck={false}
              />
            </div>

            {/* 底部状态与操作栏 (纯白背景 + 纯细分割线) */}
            <div className="flex items-center justify-between px-6 py-2.5 bg-white border-t border-[#EFF0F1] shrink-0">
              <div className="flex items-center gap-4 text-[13px] text-[#8F959E]">
                <span className="tabular-nums">{lineCount} 行</span>
                <span>·</span>
                <span className="tabular-nums">{editorContent.length} 字符</span>
                <span>·</span>
                <span className="tabular-nums">约 {estimatedTokens} Tokens</span>
                {isDirty ? (
                  <span className="inline-flex items-center gap-1 text-[#FF7D00] font-medium">
                    ● 有未发布的修改
                  </span>
                ) : (
                  <span className="text-[#00B42A]">✓ 与线上稳定版一致</span>
                )}
              </div>

              <div className="flex items-center gap-3">
                {/* 飞书次级线框按钮 */}
                <button
                  type="button"
                  onClick={onResetContent}
                  disabled={!isDirty}
                  className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] border border-[#DEE0E3] bg-white px-3.5 text-[14px] font-normal text-[#1F2329] hover:bg-[#F2F3F5] active:bg-[#E5E6EB] shadow-none transition-colors disabled:opacity-40 cursor-pointer"
                >
                  <RotateCcw className="size-3.5 text-[#646A73]" /> 还原修改
                </button>

                {/* 飞书主行动点按钮 */}
                <button
                  type="button"
                  onClick={onSubmitRelease}
                  disabled={isSubmitting || !editorContent.trim() || !isDirty}
                  className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] bg-[#3370FF] px-4 text-[14px] font-normal text-white hover:bg-[#2860E1] active:bg-[#1F4EC9] shadow-none transition-colors disabled:opacity-50 cursor-pointer"
                >
                  {isSubmitting ? (
                    <Loader2 className="size-3.5 animate-spin" />
                  ) : (
                    <Save className="size-3.5" />
                  )}
                  提交并发布为正式稳定版
                </button>
              </div>
            </div>
          </div>
        )}

        {/* B. 模式 2: 全宽脱敏渲染预览 (纯白通透) */}
        {viewMode === "preview" && (
          <div className="flex-1 flex flex-col min-h-0 bg-white">
            {/* 预览控制栏 */}
            <div className="flex items-center justify-between px-6 py-2.5 bg-white border-b border-[#EFF0F1] shrink-0">
              <div className="flex items-center gap-2 text-[14px] font-medium text-[#1F2329]">
                <Sparkles className="size-4 text-[#3370FF]" />
                脱敏变量渲染输出预览 (/preview)
              </div>

              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={onRefreshPreview}
                  disabled={previewLoading}
                  className="inline-flex h-[28px] items-center gap-1 px-2.5 rounded-[6px] border border-[#DEE0E3] bg-white text-[#646A73] hover:text-[#1F2329] hover:bg-[#F2F3F5] transition-colors text-[13px] cursor-pointer shadow-none"
                >
                  <RefreshCw className={`size-3 ${previewLoading ? "animate-spin" : ""}`} />
                  重新渲染
                </button>

                <button
                  type="button"
                  onClick={handleCopyPreview}
                  disabled={!previewContent}
                  className="inline-flex h-[28px] items-center gap-1 px-2.5 rounded-[6px] border border-[#DEE0E3] bg-white text-[#1F2329] hover:bg-[#F2F3F5] transition-colors text-[13px] cursor-pointer disabled:opacity-40 shadow-none"
                >
                  {copiedPreview ? (
                    <>
                      <Check className="size-3 text-[#00B42A]" /> 已复制
                    </>
                  ) : (
                    <>
                      <Copy className="size-3" /> 复制渲染正文
                    </>
                  )}
                </button>
              </div>
            </div>

            {/* 渲染正文主体 (纯白卡片) */}
            <div className="flex-1 p-6 overflow-y-auto bg-white">
              {previewLoading ? (
                <div className="h-full flex flex-col items-center justify-center text-[#3370FF] gap-2 py-20">
                  <Loader2 className="size-6 animate-spin" />
                  <span className="text-[14px] text-[#646A73]">正在调用脱敏渲染服务...</span>
                </div>
              ) : previewContent ? (
                <div className="max-w-4xl mx-auto bg-white p-6 rounded-[12px] border border-[#DEE0E3] shadow-2xs font-mono text-[13px] text-[#1F2329] leading-relaxed whitespace-pre-wrap">
                  {previewContent}
                </div>
              ) : (
                <FeishuEmptyState
                  title="暂无渲染预览"
                  description="请在编辑模式输入 Prompt 正文，系统将自动填充脱敏变量实时预览"
                />
              )}
            </div>

            {/* 底部提示 */}
            <div className="px-6 py-2 bg-white border-t border-[#EFF0F1] flex items-center justify-between text-[12px] text-[#8F959E] shrink-0">
              <span>已启用服务端内置脱敏字典替换保护机制</span>
              <span className="tabular-nums">状态: 200 OK</span>
            </div>
          </div>
        )}

        {/* C. 模式 3: 改动 Diff 视图 (纯白通透) */}
        {viewMode === "diff" && (
          <div className="flex-1 flex flex-col min-h-0 p-6 bg-white">
            <PromptDiffViewer
              oldContent={stableContent}
              newContent={editorContent}
              oldTitle={`线上正式稳定版`}
              newTitle={`当前编辑草稿`}
              className="flex-1 shadow-2xs"
            />
          </div>
        )}
      </div>
    </main>
  );
};

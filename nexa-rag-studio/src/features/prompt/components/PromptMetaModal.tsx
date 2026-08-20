import React, { useState, useEffect } from "react";
import { X, Loader2, Save, Code2 } from "lucide-react";
import { PromptResponse } from "../../../types";
import { promptApi } from "../../../lib/api";

interface PromptMetaModalProps {
  isOpen: boolean;
  onClose: () => void;
  prompt: PromptResponse | null;
  onSuccess: (updated: PromptResponse) => void;
}

export const PromptMetaModal: React.FC<PromptMetaModalProps> = ({
  isOpen,
  onClose,
  prompt,
  onSuccess,
}) => {
  const [name, setName] = useState("");
  const [variableSchema, setVariableSchema] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    if (prompt) {
      setName(prompt.name || prompt.promptName || "");
      setVariableSchema(prompt.variableSchema || "");
      setEnabled(prompt.enabled !== false);
      setErrorMsg(null);
    }
  }, [prompt, isOpen]);

  if (!isOpen || !prompt) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setErrorMsg("Prompt 别名不能为空");
      return;
    }

    // 校验 variableSchema 是否为合法 JSON
    if (variableSchema.trim()) {
      try {
        JSON.parse(variableSchema.trim());
      } catch (err: any) {
        setErrorMsg(`变量契约 Schema 格式须为合法 JSON: ${err.message}`);
        return;
      }
    }

    setIsSubmitting(true);
    setErrorMsg(null);

    try {
      const res = await promptApi.updatePrompt(prompt.promptCode, {
        name: name.trim(),
        variableSchema: variableSchema.trim() || undefined,
        enabled,
      });
      onSuccess(res);
      onClose();
    } catch (err: any) {
      setErrorMsg(err.message || "更新 Prompt 基础信息失败");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1F2329]/40 backdrop-blur-[1px] animate-in fade-in duration-100">
      <div className="w-full max-w-[520px] bg-white rounded-[12px] border border-[#DEE0E3] shadow-[0_8px_32px_rgba(31,35,41,0.12)] overflow-hidden animate-in zoom-in-95 duration-150">
        {/* 飞书 1:1 纯净标题栏 */}
        <div className="flex items-center justify-between px-6 pt-5 pb-4 border-b border-[#EFF0F1] bg-white">
          <div>
            <h3 className="text-[16px] font-semibold text-[#1F2329] leading-tight">
              编辑 Prompt 基础信息
            </h3>
            <p className="text-[12px] text-[#646A73] mt-1">
              编码: <span className="text-[#1F2329]">{prompt.promptCode}</span>
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="flex h-7 w-7 items-center justify-center rounded-[6px] text-[#8F959E] hover:bg-[#F2F3F5] hover:text-[#1F2329] transition-colors cursor-pointer"
          >
            <X className="size-4" />
          </button>
        </div>

        {/* 飞书标准表单 Body (纯白底色) */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4 bg-white">
          {errorMsg && (
            <div className="p-3 text-[13px] bg-[#FFF2F0] text-[#F53F3F] rounded-[6px]">
              {errorMsg}
            </div>
          )}

          {/* Prompt 别名 */}
          <div>
            <label className="block text-[14px] font-normal text-[#1F2329] mb-1.5">
              Prompt 展示名称 <span className="text-[#F53F3F]">*</span>
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="例如：RAG 核心检索问答提示词"
              className="w-full h-[36px] rounded-[6px] border border-[#DEE0E3] bg-white px-3 text-[14px] text-[#1F2329] focus:border-[#3370FF] focus:ring-2 focus:ring-[#3370FF]/15 transition-all outline-none"
            />
          </div>

          {/* 启用状态 (纯白背景 + 细边框) */}
          <div className="flex items-center justify-between p-3.5 rounded-[8px] bg-white border border-[#DEE0E3]">
            <div>
              <span className="text-[14px] font-normal text-[#1F2329] block">启用状态</span>
              <span className="text-[12px] text-[#646A73]">停用后网关调度将直接跳过该 Prompt</span>
            </div>
            <button
              type="button"
              onClick={() => setEnabled((prev) => !prev)}
              className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                enabled ? "bg-[#3370FF]" : "bg-[#DEE0E3]"
              }`}
            >
              <span
                className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                  enabled ? "translate-x-4" : "translate-x-0"
                }`}
              />
            </button>
          </div>

          {/* 变量契约 Schema (JSON) */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="text-[14px] font-normal text-[#1F2329] flex items-center gap-1.5">
                <Code2 className="size-3.5 text-[#3370FF]" /> 变量契约 Schema (JSON)
              </label>
              <span className="text-[12px] text-[#8F959E]">可选，定义入参约束</span>
            </div>
            <textarea
              rows={5}
              value={variableSchema}
              onChange={(e) => setVariableSchema(e.target.value)}
              placeholder={`{\n  "required": ["context", "query"],\n  "properties": {\n    "context": { "type": "string", "description": "召回的切片正文" },\n    "query": { "type": "string", "description": "用户原始提问" }\n  }\n}`}
              className="w-full rounded-[6px] border border-[#DEE0E3] bg-white p-3 font-mono text-[13px] text-[#1F2329] focus:border-[#3370FF] focus:ring-2 focus:ring-[#3370FF]/15 resize-none transition-all outline-none"
            />
          </div>

          {/* 飞书 1:1 标准 Footer */}
          <div className="pt-4 border-t border-[#EFF0F1] flex justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] border border-[#DEE0E3] bg-white px-4 text-[14px] font-normal text-[#1F2329] hover:bg-[#F2F3F5] active:bg-[#E5E6EB] transition-colors cursor-pointer shadow-none"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex h-[32px] items-center gap-1.5 rounded-[6px] bg-[#3370FF] px-4 text-[14px] font-normal text-white hover:bg-[#2860E1] active:bg-[#1F4EC9] transition-colors cursor-pointer disabled:opacity-50 shadow-none"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="size-3.5 animate-spin" /> 保存中...
                </>
              ) : (
                <>
                  <Save className="size-3.5" /> 保存配置
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

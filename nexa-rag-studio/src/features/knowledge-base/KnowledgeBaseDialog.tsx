import React, { useEffect, useState } from "react";
import { X } from "lucide-react";
import { KnowledgeBaseSummaryVO } from "../../types";
import { FEISHU_FONT_FAMILY } from "../../components/ui/feishu-table";

interface KnowledgeBaseDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  knowledgeBase?: KnowledgeBaseSummaryVO | null;
  onSubmit: (data: { name: string; description: string }) => Promise<void>;
}

export const KnowledgeBaseDialog: React.FC<KnowledgeBaseDialogProps> = ({
  open,
  onOpenChange,
  knowledgeBase,
  onSubmit,
}) => {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isEdit = Boolean(knowledgeBase);
  const isDefault = knowledgeBase?.isDefault === 1;

  useEffect(() => {
    if (open) {
      setName(knowledgeBase?.name || "");
      setDescription(knowledgeBase?.description || "");
      setError(null);
    }
  }, [open, knowledgeBase]);

  if (!open) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedName = name.trim();
    if (!trimmedName) {
      setError("请输入知识库名称");
      return;
    }
    if (trimmedName.length > 50) {
      setError("知识库名称长度不能超过 50 个字符");
      return;
    }
    if (description.length > 200) {
      setError("知识库描述长度不能超过 200 个字符");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      await onSubmit({ name: trimmedName, description: description.trim() });
      onOpenChange(false);
    } catch (err: any) {
      setError(err?.message || "提交失败，请重试");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* 遮罩背景 */}
      <div
        className="fixed inset-0 bg-[#1F2329]/40 backdrop-blur-[2px] transition-opacity animate-in fade-in"
        onClick={() => onOpenChange(false)}
      />

      {/* 模态卡片 */}
      <div
        style={{ fontFamily: FEISHU_FONT_FAMILY }}
        className="relative w-full max-w-[480px] bg-white rounded-[12px] border border-[#DEE0E3] shadow-[0_8px_32px_rgba(31,35,41,0.12)] overflow-hidden z-10 animate-in zoom-in-95 duration-150"
      >
        {/* 标题栏 (纯净飞书风格，移除多余色块图标) */}
        <div className="flex items-center justify-between px-6 pt-5 pb-4 border-b border-[#EFF0F1] bg-white">
          <h3 className="text-[16px] font-semibold text-[#1F2329]">
            {isEdit ? (isDefault ? "知识库属性" : "编辑知识库") : "新建知识库"}
          </h3>
          <button
            type="button"
            onClick={() => onOpenChange(false)}
            className="w-7 h-7 rounded-[6px] hover:bg-[#F2F3F5] flex items-center justify-center text-[#8F959E] hover:text-[#1F2329] transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="rounded-[6px] bg-[#FFF2F0] p-3 text-[13px] text-[#F53F3F] border border-[#F53F3F]/20">
              {error}
            </div>
          )}

          <div>
            <label className="block text-[14px] text-[#1F2329] font-normal mb-1.5">
              知识库名称 <span className="text-[#F53F3F]">*</span>
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={isDefault || loading}
              placeholder="请输入知识库名称，例如：产品技术白皮书、HR规章制度"
              maxLength={50}
              className="w-full h-[36px] rounded-[6px] border border-[#DEE0E3] bg-white px-3 text-[14px] text-[#1F2329] placeholder:text-[#8F959E] focus:outline-none focus:border-[#3370FF] focus:ring-2 focus:ring-[#3370FF]/15 transition-all disabled:bg-[#F2F3F5] disabled:text-[#8F959E]"
            />
            {isDefault && (
              <p className="mt-1.5 text-[12px] text-[#8F959E]">
                系统内置默认知识库不可修改名称
              </p>
            )}
          </div>

          <div>
            <label className="block text-[14px] text-[#1F2329] font-normal mb-1.5">
              描述说明
            </label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              disabled={loading}
              placeholder="描述此知识库收录的文档范围或用途..."
              rows={3}
              maxLength={200}
              className="w-full rounded-[6px] border border-[#DEE0E3] bg-white p-3 text-[14px] text-[#1F2329] placeholder:text-[#8F959E] focus:outline-none focus:border-[#3370FF] focus:ring-2 focus:ring-[#3370FF]/15 resize-none transition-all"
            />
            <div className="mt-1 flex justify-end text-[12px] text-[#8F959E]">
              {description.length} / 200
            </div>
          </div>

          <div className="mt-6 flex items-center justify-end gap-3 pt-4 border-t border-[#EFF0F1]">
            <button
              type="button"
              onClick={() => onOpenChange(false)}
              disabled={loading}
              className="h-[32px] rounded-[6px] border border-[#DEE0E3] bg-white px-3.5 text-[14px] text-[#1F2329] hover:bg-[#F2F3F5] active:bg-[#E5E6EB] transition-colors"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={loading}
              className="h-[32px] rounded-[6px] bg-[#3370FF] px-4 text-[14px] font-normal text-white hover:bg-[#2860E1] active:bg-[#1F4EC9] transition-colors disabled:opacity-50"
            >
              {loading ? "保存中..." : isEdit ? "保存修改" : "创建"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

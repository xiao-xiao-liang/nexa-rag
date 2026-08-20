import React, { useState } from "react";
import { X } from "lucide-react";
import { KnowledgeBaseSummaryVO } from "../../types";
import { FEISHU_FONT_FAMILY } from "../../components/ui/feishu-table";

interface DeleteKnowledgeBaseDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  knowledgeBase: KnowledgeBaseSummaryVO | null;
  onConfirm: (id: number | string) => Promise<void>;
  onNavigateToDocuments?: (id: number | string) => void;
}

export const DeleteKnowledgeBaseDialog: React.FC<DeleteKnowledgeBaseDialogProps> = ({
  open,
  onOpenChange,
  knowledgeBase,
  onConfirm,
  onNavigateToDocuments,
}) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open || !knowledgeBase) return null;

  const totalDocCount = knowledgeBase.statistics?.totalCount ?? 0;
  const isNonEmpty = totalDocCount > 0;

  const handleDelete = async () => {
    setLoading(true);
    setError(null);
    try {
      await onConfirm(knowledgeBase.knowledgeBaseId);
      onOpenChange(false);
    } catch (err: any) {
      setError(err?.message || "删除失败，请重试");
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
        className="relative w-full max-w-[460px] bg-white rounded-[12px] border border-[#DEE0E3] shadow-[0_8px_32px_rgba(31,35,41,0.12)] overflow-hidden z-10 animate-in zoom-in-95 duration-150"
      >
        {/* 标题栏 */}
        <div className="flex items-center justify-between px-6 pt-5 pb-4 border-b border-[#EFF0F1] bg-white">
          <h3 className="text-[16px] font-semibold text-[#1F2329]">
            {isNonEmpty ? "无法删除知识库" : "确认删除知识库"}
          </h3>
          <button
            type="button"
            onClick={() => onOpenChange(false)}
            className="w-7 h-7 rounded-[6px] hover:bg-[#F2F3F5] flex items-center justify-center text-[#8F959E] hover:text-[#1F2329] transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-6 space-y-3.5 text-[14px] leading-relaxed text-[#646A73]">
          {error && (
            <div className="rounded-[6px] bg-[#FFF2F0] p-3 text-[13px] text-[#F53F3F] border border-[#F53F3F]/20">
              {error}
            </div>
          )}

          {isNonEmpty ? (
            <>
              <div className="rounded-[8px] bg-[#FFF2F0]/60 border border-[#F53F3F]/20 p-3.5 space-y-1.5">
                <p className="font-semibold text-[#F53F3F]">安全限制触发</p>
                <p className="text-[#646A73]">
                  知识库 <strong className="text-[#1F2329]">“{knowledgeBase.name}”</strong> 当前包含{" "}
                  <strong className="text-[#F53F3F] font-bold">{totalDocCount}</strong> 篇文档。
                </p>
              </div>
              <p className="text-[13px] text-[#8F959E]">
                为防止误删导致向量索引与检索资产丢失，系统不允许直接删除非空知识库。请先进入该知识库清空全部文档后再执行删除。
              </p>
            </>
          ) : (
            <>
              <p>
                您即将删除知识库 <strong className="text-[#1F2329]">“{knowledgeBase.name}”</strong>。
              </p>
              <p className="text-[13px] text-[#8F959E]">此操作不可恢复，删除后该知识库的所有配置将永久移除。</p>
            </>
          )}

          <div className="mt-6 flex items-center justify-end gap-3 pt-4 border-t border-[#EFF0F1]">
            <button
              type="button"
              onClick={() => onOpenChange(false)}
              disabled={loading}
              className="h-[32px] rounded-[6px] border border-[#DEE0E3] bg-white px-3.5 text-[14px] text-[#1F2329] hover:bg-[#F2F3F5] active:bg-[#E5E6EB] transition-colors"
            >
              {isNonEmpty ? "知道了" : "取消"}
            </button>

            {isNonEmpty ? (
              <button
                type="button"
                onClick={() => {
                  onOpenChange(false);
                  onNavigateToDocuments?.(knowledgeBase.knowledgeBaseId);
                }}
                className="h-[32px] rounded-[6px] bg-[#3370FF] px-4 text-[14px] font-normal text-white hover:bg-[#2860E1] active:bg-[#1F4EC9] transition-colors"
              >
                前往文档管理
              </button>
            ) : (
              <button
                type="button"
                onClick={handleDelete}
                disabled={loading}
                className="h-[32px] rounded-[6px] bg-[#F53F3F] px-4 text-[14px] font-normal text-white hover:bg-[#E03434] active:bg-[#CC2828] transition-colors disabled:opacity-50"
              >
                {loading ? "删除中..." : "确认删除"}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

import React, { useState } from "react";
import { X, CheckCircle2, AlertCircle, Loader2, UploadCloud } from "lucide-react";
import { documentApi } from "../../../lib/api";
import { FEISHU_FONT_FAMILY, FeishuFileDragger } from "../../../components/ui/feishu-table";

interface DocumentVersionUploadModalProps {
  isOpen: boolean;
  documentId: string | number;
  documentTitle?: string;
  knowledgeBaseId?: number | string;
  onClose: () => void;
  onSuccess: () => void;
}

export const DocumentVersionUploadModal: React.FC<DocumentVersionUploadModalProps> = ({
  isOpen,
  documentId,
  documentTitle,
  knowledgeBaseId,
  onClose,
  onSuccess,
}) => {
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleFileSelect = (selectedFile: File) => {
    setFile(selectedFile);
    if (!title) {
      const name = selectedFile.name.replace(/\.[^/.]+$/, "");
      setTitle(name);
    }
    setErrorMessage(null);
  };

  const handleFileRemove = () => {
    setFile(null);
    setTitle("");
    setErrorMessage(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setErrorMessage("请选择要上传的新版本文件");
      return;
    }

    setErrorMessage(null);
    setIsSubmitting(true);

    try {
      await documentApi.uploadDocumentVersion(
        documentId,
        file,
        title.trim() ? { title: title.trim() } : undefined,
        knowledgeBaseId
      );
      onSuccess();
      onClose();
    } catch (err: any) {
      setErrorMessage(err?.message || "上传新版本失败，请检查文件或流水线状态");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* 遮罩背景 */}
      <div
        className="fixed inset-0 bg-[#1F2329]/40 backdrop-blur-[2px] transition-opacity animate-in fade-in"
        onClick={onClose}
      />

      {/* 飞书 Universe 模态卡片 */}
      <div
        style={{ fontFamily: FEISHU_FONT_FAMILY }}
        className="relative w-full max-w-[540px] bg-white rounded-[12px] border border-[#DEE0E3] shadow-[0_8px_32px_rgba(31,35,41,0.12)] overflow-hidden z-10 animate-in zoom-in-95 duration-150"
      >
        {/* 标题栏 */}
        <div className="flex items-center justify-between px-6 pt-5 pb-4 border-b border-[#EFF0F1] bg-white">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-[6px] bg-[#E8F3FF] text-[#3370FF] flex items-center justify-center shrink-0">
              <UploadCloud className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-[16px] font-semibold text-[#1F2329]">上传新版本</h3>
              <p className="text-[12px] text-[#8F959E] mt-0.5 truncate max-w-[380px]">
                目标文档: {documentTitle || `#${documentId}`}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-7 h-7 rounded-[6px] hover:bg-[#F2F3F5] flex items-center justify-center text-[#8F959E] hover:text-[#1F2329] transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {errorMessage && (
            <div className="p-3 bg-[#FFF2F0] border border-[#F53F3F]/20 rounded-[6px] text-[13px] text-[#F53F3F] flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{errorMessage}</span>
            </div>
          )}

          {/* 提示信息 */}
          <div className="p-3 bg-[#F8F9FA] rounded-[8px] border border-[#DEE0E3] text-[12px] text-[#646A75] leading-[18px]">
            💡 新文件上传后将作为文档的新 Revision 进入后台流水线独立解析与索引预热；在索引预热完成（<code>INDEX_READY</code>）前不会影响当前线上版本的检索生效。
          </div>

          {/* 飞书文件拖拽区 */}
          <FeishuFileDragger
            file={file}
            onFileSelect={handleFileSelect}
            onFileRemove={handleFileRemove}
            maxSizeMB={50}
            disabled={isSubmitting}
          />

          {/* 自定义标题 */}
          <div>
            <label className="block text-[14px] text-[#1F2329] font-normal mb-1.5">
              版本备注 / 显示名称 (可选)
            </label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="默认使用上传文件名"
              className="w-full h-[36px] px-3 rounded-[6px] border border-[#DEE0E3] bg-white text-[14px] text-[#1F2329] placeholder:text-[#8F959E] focus:outline-none focus:border-[#3370FF] focus:ring-2 focus:ring-[#3370FF]/15 transition-all"
            />
          </div>

          {/* 底部按钮栏 */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#EFF0F1]">
            <button
              type="button"
              onClick={onClose}
              className="h-[32px] px-3.5 rounded-[6px] border border-[#DEE0E3] bg-white hover:bg-[#F2F3F5] active:bg-[#E5E6EB] text-[14px] text-[#1F2329] transition-colors cursor-pointer"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={isSubmitting || !file}
              className="h-[32px] px-4 rounded-[6px] bg-[#3370FF] hover:bg-[#2860E1] active:bg-[#1F4EC9] text-white text-[14px] font-normal inline-flex items-center justify-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>上传处理中...</span>
                </>
              ) : (
                <>
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  <span>确认上传</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

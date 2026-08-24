import React, { useState } from "react";
import { X, FileText, Globe, CheckCircle2, AlertCircle, Loader2, Sparkles } from "lucide-react";
import { documentApi } from "../../../lib/api";
import { FEISHU_FONT_FAMILY, FeishuFileDragger } from "../../../components/ui/feishu-table";

interface DocumentUploadModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  knowledgeBaseId?: number | string;
}

type SourceType = "LOCAL" | "FEISHU" | "WEB";

// 飞书官方彩色 Logo
const FeishuLogo = () => (
  <img
    src="https://cdn.jsdelivr.net/gh/callback-io/allogo@main/public/logos/feishu/icon.svg"
    alt="飞书"
    className="w-5 h-5 object-contain shrink-0"
  />
);

// 语雀官方绿色 Logo
const YuqueLogo = () => (
  <img
    src="https://mdn.alipayobjects.com/huamei_0prmtq/afts/img/A*PXAJTYXseTsAAAAAAAAAAAAADvuFAQ/original"
    alt="语雀"
    className="w-5 h-5 object-contain shrink-0"
  />
);

export const DocumentUploadModal: React.FC<DocumentUploadModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  knowledgeBaseId,
}) => {
  const [sourceType, setSourceType] = useState<SourceType>("LOCAL");
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [externalUrl, setExternalUrl] = useState("");
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
    setErrorMessage(null);
    setIsSubmitting(true);

    try {
      if (sourceType === "LOCAL") {
        if (!file) {
          setErrorMessage("请选择要上传的文档文件");
          setIsSubmitting(false);
          return;
        }
        await documentApi.uploadDocument(
          file,
          {
            title: title.trim() || file.name,
          },
          knowledgeBaseId
        );
      } else if (sourceType === "FEISHU") {
        if (!externalUrl.trim()) {
          setErrorMessage("请输入飞书在线文档链接");
          setIsSubmitting(false);
          return;
        }
        await documentApi.submitExternalDocument(
          {
            sourceType: "FEISHU",
            sourceUrl: externalUrl.trim(),
            title: title.trim() || undefined,
          },
          knowledgeBaseId
        );
      } else {
        setErrorMessage("语雀 / 网页抓取管道正在集成中，敬请期待");
        setIsSubmitting(false);
        return;
      }

      onSuccess();
      onClose();
    } catch (err: any) {
      setErrorMessage(err?.message || "提交失败，请检查网络或后端流水线配置");
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
        className="relative w-full max-w-[560px] bg-white rounded-[12px] border border-[#DEE0E3] shadow-[0_8px_32px_rgba(31,35,41,0.12)] overflow-hidden z-10 animate-in zoom-in-95 duration-150"
      >
        {/* 标题栏 (纯净飞书风格) */}
        <div className="flex items-center justify-between px-6 pt-5 pb-4 border-b border-[#EFF0F1] bg-white">
          <div>
            <h3 className="text-[16px] font-semibold text-[#1F2329]">导入文档</h3>
            <p className="text-[12px] text-[#8F959E] mt-0.5">选择数据源导入文件或同步在线知识资产</p>
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

          {/* 1. 数据源卡片选择区 (LOGO + 标题 + 格式/说明) */}
          <div>
            <label className="block text-[13px] font-medium text-[#646A73] mb-2">
              选择接入数据源
            </label>
            <div className="grid grid-cols-3 gap-3">
              {/* 本地文件 */}
              <div
                onClick={() => {
                  setSourceType("LOCAL");
                  setErrorMessage(null);
                }}
                className={`p-3 rounded-[8px] border transition-all duration-200 ease-out active:scale-[0.98] cursor-pointer flex flex-col justify-between ${
                  sourceType === "LOCAL"
                    ? "border-[#3370FF] bg-[#E8F3FF]/30 ring-1 ring-[#3370FF]"
                    : "border-[#DEE0E3] hover:border-[#8F959E] bg-white"
                }`}
              >
                <div className="flex items-center gap-2">
                  <div className="w-5 h-5 rounded-[5px] bg-[#3370FF] text-white flex items-center justify-center shrink-0">
                    <FileText className="w-3.5 h-3.5" />
                  </div>
                  <span className="text-[13px] font-semibold text-[#1F2329]">本地文件</span>
                </div>
                <div className="text-[11px] text-[#8F959E] mt-2 leading-tight">
                  PDF / Word / MD / Excel
                </div>
              </div>

              {/* 飞书云文档 */}
              <div
                onClick={() => {
                  setSourceType("FEISHU");
                  setErrorMessage(null);
                }}
                className={`p-3 rounded-[8px] border transition-all duration-200 ease-out active:scale-[0.98] cursor-pointer flex flex-col justify-between ${
                  sourceType === "FEISHU"
                    ? "border-[#3370FF] bg-[#E8F3FF]/30 ring-1 ring-[#3370FF]"
                    : "border-[#DEE0E3] hover:border-[#8F959E] bg-white"
                }`}
              >
                <div className="flex items-center gap-2">
                  <FeishuLogo />
                  <span className="text-[13px] font-semibold text-[#1F2329]">飞书云文档</span>
                </div>
                <div className="text-[11px] text-[#8F959E] mt-2 leading-tight">
                  Wiki / 多维表格 / 云文档
                </div>
              </div>

              {/* 语雀知识库 (即将上线) */}
              <div
                onClick={() => {
                  setSourceType("WEB");
                  setErrorMessage(null);
                }}
                className={`p-3 rounded-[8px] border transition-all duration-200 ease-out active:scale-[0.98] cursor-pointer flex flex-col justify-between opacity-85 ${
                  sourceType === "WEB"
                    ? "border-[#3370FF] bg-[#E8F3FF]/30 ring-1 ring-[#3370FF]"
                    : "border-[#DEE0E3] hover:border-[#8F959E] bg-[#FAFAFA]"
                }`}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <YuqueLogo />
                    <span className="text-[13px] font-semibold text-[#1F2329]">语雀知识库</span>
                  </div>
                  <span className="text-[10px] bg-[#F2F3F5] text-[#8F959E] px-1 py-0.2 rounded font-normal">预告</span>
                </div>
                <div className="text-[11px] text-[#8F959E] mt-2 leading-tight">
                  团队与个人知识库
                </div>
              </div>
            </div>
          </div>

          {/* 2. 动态接入配置区 (带平滑淡入展开动效) */}
          <div key={sourceType} className="animate-in fade-in-50 slide-in-from-bottom-1 duration-200 ease-out">
            {sourceType === "LOCAL" && (
              <div className="space-y-3.5 pt-1">
                {/* 1:1 飞书标准多态文档拖拽与就绪组件 */}
                <FeishuFileDragger
                  file={file}
                  onFileSelect={handleFileSelect}
                  onFileRemove={handleFileRemove}
                  maxSizeMB={50}
                  disabled={isSubmitting}
                />

                {/* 文档自定义标题 */}
                <div>
                  <label className="block text-[14px] text-[#1F2329] font-normal mb-1.5">
                    文档显示名称
                  </label>
                  <input
                    type="text"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="若不填写则默认使用上传文件名"
                    className="w-full h-[36px] px-3 rounded-[6px] border border-[#DEE0E3] bg-white text-[14px] text-[#1F2329] placeholder:text-[#8F959E] focus:outline-none focus:border-[#3370FF] focus:ring-2 focus:ring-[#3370FF]/15 transition-all"
                  />
                </div>
              </div>
            )}

            {sourceType === "FEISHU" && (
              <div className="space-y-3.5 pt-1">
                <div>
                  <label className="block text-[14px] text-[#1F2329] font-normal mb-1.5">
                    飞书云文档 / 多维表格链接 <span className="text-[#F53F3F]">*</span>
                  </label>
                  <input
                    type="url"
                    value={externalUrl}
                    onChange={(e) => setExternalUrl(e.target.value)}
                    placeholder="https://xxx.feishu.cn/docx/... 或 https://xxx.feishu.cn/base/..."
                    className="w-full h-[36px] px-3 rounded-[6px] border border-[#DEE0E3] bg-white text-[14px] text-[#1F2329] placeholder:text-[#8F959E] focus:outline-none focus:border-[#3370FF] focus:ring-2 focus:ring-[#3370FF]/15 transition-all"
                  />
                  <p className="text-[12px] text-[#8F959E] mt-1.5">
                    💡 系统将通过 OpenAPI 安全管道自动拉取内容、结构化分块并触发向量索引
                  </p>
                </div>

                <div>
                  <label className="block text-[14px] text-[#1F2329] font-normal mb-1.5">
                    自定义文档标题 (可选)
                  </label>
                  <input
                    type="text"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="默认使用飞书云文档原标题"
                    className="w-full h-[36px] px-3 rounded-[6px] border border-[#DEE0E3] bg-white text-[14px] text-[#1F2329] placeholder:text-[#8F959E] focus:outline-none focus:border-[#3370FF] focus:ring-2 focus:ring-[#3370FF]/15 transition-all"
                  />
                </div>
              </div>
            )}

            {sourceType === "WEB" && (
              <div className="p-4 bg-[#F9FAFB] rounded-[8px] border border-[#EFF0F1] text-center py-6 space-y-2">
                <Sparkles className="w-6 h-6 text-[#3370FF] mx-auto" />
                <p className="text-[14px] font-medium text-[#1F2329]">网页 / 语雀知识库抓取管道</p>
                <p className="text-[12px] text-[#8F959E] max-w-sm mx-auto">
                  支持输入网页 URL 或语雀团队知识库，全自动解析正文结构并执行持续增量向量化同步。
                </p>
              </div>
            )}
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
              disabled={isSubmitting}
              className="h-[32px] px-4 rounded-[6px] bg-[#3370FF] hover:bg-[#2860E1] active:bg-[#1F4EC9] text-white text-[14px] font-normal inline-flex items-center justify-center gap-1.5 transition-colors cursor-pointer disabled:opacity-50"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>提交中...</span>
                </>
              ) : (
                <>
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  <span>开始导入</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

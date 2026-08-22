import React, { useState, useEffect, useRef } from "react";
import { ZoomIn, ZoomOut, ExternalLink, X, RotateCcw } from "lucide-react";
import { FeishuTooltip } from "../../ui/tooltip";

export interface FeishuMarkdownImageProps {
  src?: string;
  alt?: string;
  className?: string;
}

/** 1:1 飞书官方图片加载失败图标 */
const FeishuImageErrorIcon: React.FC<{ className?: string }> = ({
  className = "w-8 h-8",
}) => (
  <svg
    className={className}
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <rect
      x="2"
      y="3"
      width="20"
      height="18"
      rx="3"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <circle cx="8.5" cy="8.5" r="1.5" fill="currentColor" />
    <path
      d="M21 16L15.5 10.5L8 18"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M2 2L22 22"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
    />
  </svg>
);

/**
 * 1:1 飞书多维表格风格 Markdown 图片渲染器
 * 具备：智能缓存同步检测、骨架屏加载、1:1 错误态卡片、自适应高清展示与全屏灯箱放大预览
 */
export const FeishuMarkdownImage: React.FC<FeishuMarkdownImageProps> = ({
  src,
  alt = "",
  className = "",
}) => {
  const [loadStatus, setLoadStatus] = useState<"loading" | "loaded" | "error">(
    src ? "loading" : "error"
  );
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const [zoomScale, setZoomScale] = useState(1);
  const [retryKey, setRetryKey] = useState(0);
  const imgRef = useRef<HTMLImageElement | null>(null);

  // 1. 同步检测浏览器缓存及挂载态 (解决缓存图片不触发 onLoad 导致的无限转圈问题)
  useEffect(() => {
    if (!src) {
      setLoadStatus("error");
      return;
    }

    setLoadStatus("loading");

    // 检查 img 节点是否已由浏览器直接加载完成
    if (imgRef.current && imgRef.current.complete) {
      if (imgRef.current.naturalWidth > 0) {
        setLoadStatus("loaded");
        return;
      }
    }

    // 设置 15 秒超时防护，避免异常网络连接长期挂起
    const timeoutId = setTimeout(() => {
      setLoadStatus((prev) => (prev === "loading" ? "error" : prev));
    }, 15000);

    return () => clearTimeout(timeoutId);
  }, [src, retryKey]);

  // 2. 处理键盘 ESC 关闭大图预览
  useEffect(() => {
    if (!isPreviewOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setIsPreviewOpen(false);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isPreviewOpen]);

  const handleRetry = (e: React.MouseEvent) => {
    e.stopPropagation();
    setRetryKey((prev) => prev + 1);
  };

  const handleOpenOriginal = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (src) {
      window.open(src, "_blank", "noopener,noreferrer");
    }
  };

  return (
    <>
      <div className={`base-chatbot-maker-md-comp-image ${className}`}>
        {/* 1. 加载中骨架占位态 */}
        {loadStatus === "loading" && (
          <div className="relative inline-flex items-center justify-center w-[220px] h-[140px] rounded-[6px] border border-[#DEE0E3] bg-[#F5F6F7] animate-pulse my-1 overflow-hidden select-none">
            <div className="flex flex-col items-center gap-2 text-[#8F959E]">
              <div className="w-5 h-5 rounded-full border-2 border-[#8F959E] border-t-transparent animate-spin" />
              <span className="text-[12px] font-sans">图片加载中...</span>
            </div>
          </div>
        )}

        {/* 2. 1:1 飞书官方加载失败错误卡片 */}
        {loadStatus === "error" && (
          <div className="base-chatbot-maker-md-comp-image__error group relative my-1.5 cursor-default select-none">
            <div className="flex flex-col items-center justify-center text-center p-3">
              <span className="universe-icon text-[#BBBFC4] mb-2">
                <FeishuImageErrorIcon className="w-8 h-8" />
              </span>
              <span className="text-[12px] text-[#8F959E] line-clamp-1 max-w-[140px]" title={alt || "图片加载失败"}>
                {alt || "图片加载失败"}
              </span>

              {/* 悬停快捷重试 / 原始链接操作 */}
              <div className="flex items-center gap-2 mt-2 opacity-0 group-hover:opacity-100 transition-opacity duration-150">
                <FeishuTooltip title="重新加载" side="top">
                  <button
                    type="button"
                    onClick={handleRetry}
                    className="p-1 rounded-[4px] text-[#646A73] hover:bg-white hover:text-[#1F2329] hover:shadow-2xs transition-all cursor-pointer"
                  >
                    <RotateCcw className="w-3.5 h-3.5" />
                  </button>
                </FeishuTooltip>

                {src && (
                  <FeishuTooltip title="在新标签页打开" side="top">
                    <button
                      type="button"
                      onClick={handleOpenOriginal}
                      className="p-1 rounded-[4px] text-[#646A73] hover:bg-white hover:text-[#3370FF] hover:shadow-2xs transition-all cursor-pointer"
                    >
                      <ExternalLink className="w-3.5 h-3.5" />
                    </button>
                  </FeishuTooltip>
                )}
              </div>
            </div>
          </div>
        )}

        {/* 3. 真实图片元素 (移除了 display:none 与 lazy-loading 冲突，支持即时渲染) */}
        {src && (
          <img
            ref={imgRef}
            key={`${src}-${retryKey}`}
            src={src}
            alt={alt || ""}
            onLoad={() => setLoadStatus("loaded")}
            onError={() => setLoadStatus("error")}
            onClick={() => {
              if (loadStatus === "loaded") {
                setZoomScale(1);
                setIsPreviewOpen(true);
              }
            }}
            style={{
              display: loadStatus === "loaded" ? "block" : "none",
            }}
            className="cursor-zoom-in hover:brightness-98 hover:shadow-2xs transition-all"
          />
        )}
      </div>

      {/* 4. 1:1 高清大图预览灯箱 (Lightbox Modal) */}
      {isPreviewOpen && src && (
        <div
          onClick={() => setIsPreviewOpen(false)}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 backdrop-blur-[2px] p-4 animate-in fade-in duration-200 select-none"
        >
          {/* 顶部悬浮控制栏 */}
          <div
            onClick={(e) => e.stopPropagation()}
            className="absolute top-4 right-6 flex items-center gap-2 bg-[#1F2329]/80 backdrop-blur-md px-3 py-1.5 rounded-[8px] text-white shadow-lg z-10"
          >
            <span className="text-[12px] text-white/75 mr-1 font-mono">
              {Math.round(zoomScale * 100)}%
            </span>

            <FeishuTooltip title="缩小" side="bottom">
              <button
                type="button"
                onClick={() => setZoomScale((prev) => Math.max(0.5, prev - 0.25))}
                className="p-1.5 rounded-[4px] hover:bg-white/20 text-white/90 hover:text-white transition-colors cursor-pointer"
              >
                <ZoomOut className="w-4 h-4" />
              </button>
            </FeishuTooltip>

            <FeishuTooltip title="放大" side="bottom">
              <button
                type="button"
                onClick={() => setZoomScale((prev) => Math.min(3, prev + 0.25))}
                className="p-1.5 rounded-[4px] hover:bg-white/20 text-white/90 hover:text-white transition-colors cursor-pointer"
              >
                <ZoomIn className="w-4 h-4" />
              </button>
            </FeishuTooltip>

            <div className="w-[1px] h-4 bg-white/20 my-auto mx-1" />

            <FeishuTooltip title="在新标签页查看原图" side="bottom">
              <button
                type="button"
                onClick={handleOpenOriginal}
                className="p-1.5 rounded-[4px] hover:bg-white/20 text-white/90 hover:text-white transition-colors cursor-pointer"
              >
                <ExternalLink className="w-4 h-4" />
              </button>
            </FeishuTooltip>

            <FeishuTooltip title="关闭 (Esc)" side="bottom">
              <button
                type="button"
                onClick={() => setIsPreviewOpen(false)}
                className="p-1.5 rounded-[4px] hover:bg-white/20 text-white/90 hover:text-white transition-colors cursor-pointer ml-1"
              >
                <X className="w-4 h-4" />
              </button>
            </FeishuTooltip>
          </div>

          {/* 居中放大原图 */}
          <div
            onClick={(e) => e.stopPropagation()}
            className="max-w-[90vw] max-h-[85vh] overflow-auto flex items-center justify-center rounded-[8px]"
          >
            <img
              src={src}
              alt={alt || "图片预览"}
              style={{ transform: `scale(${zoomScale})`, transformOrigin: "center" }}
              className="max-w-full max-h-[80vh] object-contain rounded-[4px] shadow-2xl bg-white select-none transition-transform duration-150"
            />
          </div>
        </div>
      )}
    </>
  );
};

import React, { useState, useCallback } from "react";
import * as Popover from "@radix-ui/react-popover";
import { X, ExternalLink, AlertCircle } from "lucide-react";
import { chatApi } from "../../lib/api";
import { ChatCitationDetailVO } from "../../types";
import { cn } from "../../lib/utils";
import { FeishuMarkdown } from "./markdown/FeishuMarkdown";

export interface FeishuCitationPopoverProps {
  citationId: number;
  messageId?: string;
  onCitationClick?: (citationId: number) => void;
  onFetchCitation?: (citationId: number) => Promise<ChatCitationDetailVO>;
}

const FEISHU_FONT_FAMILY =
  'LarkHackSafariFont, LarkEmojiFont, LarkChineseQuote, -apple-system, BlinkMacSystemFont, "Helvetica Neue", Tahoma, "PingFang SC", "Microsoft Yahei", Arial, "Hiragino Sans GB", sans-serif';

/**
 * 1:1 飞书多维表格智能体分块引用 Popover 卡片组件
 * - 纯白圆角卡片背景（无灰色底框，对齐 CRM 及首页卡片规范）
 * - 移除分块标识行，留出更多正文可视空间
 * - 正文复用 FeishuMarkdown 渲染体系
 * - 鼠标悬浮显式变为手指（cursor: pointer），绝无下划线
 * - 锚定在引用数字下方，支持空间自适应自动翻转至上方且不溢出屏幕边界
 */
export const FeishuCitationPopover: React.FC<FeishuCitationPopoverProps> = ({
  citationId,
  messageId,
  onCitationClick,
  onFetchCitation,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<ChatCitationDetailVO | null>(null);

  const fetchDetail = useCallback(async () => {
    if (!messageId) {
      setError("未关联消息 ID，无法获取引用详情");
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const data = onFetchCitation
        ? await onFetchCitation(citationId)
        : await chatApi.getCitation(messageId, citationId);
      setDetail(data);
    } catch (err: any) {
      const errorMsg =
        err?.message ||
        err?.response?.data?.message ||
        "引用分块不存在或暂无访问权限";
      setError(errorMsg);
    } finally {
      setIsLoading(false);
    }
  }, [citationId, messageId, onFetchCitation]);

  const handleOpenChange = (open: boolean) => {
    setIsOpen(open);
    if (open) {
      onCitationClick?.(citationId);
      if (!detail && !isLoading) {
        fetchDetail();
      }
    }
  };

  const handleOpenDocument = () => {
    if (!detail) return;

    if (detail.sourceUrl) {
      window.open(detail.sourceUrl, "_blank", "noopener,noreferrer");
    } else if (detail.documentPath) {
      window.open(detail.documentPath, "_blank", "noopener,noreferrer");
    }
  };

  return (
    <Popover.Root open={isOpen} onOpenChange={handleOpenChange}>
      {/* 触发器：引用角标按钮 */}
      <Popover.Trigger asChild>
        <button
          type="button"
          aria-label={`查看引用 [${citationId}]`}
          className={cn(
            "inline-flex items-center justify-center align-baseline font-medium text-[13px] leading-tight px-1 py-0.5 mx-0.5 rounded-[4px] cursor-pointer no-underline select-none transition-colors outline-none",
            isOpen
              ? "bg-[#E1EBFD] text-[#1456F0]"
              : "text-[#3370FF] hover:text-[#1456F0] hover:bg-[#F0F4FF] active:bg-[#E1EBFD]"
          )}
        >
          [{citationId}]
        </button>
      </Popover.Trigger>

      {/* 弹出的飞书风格卡片（纯白背景、无灰色底框、圆角 12px 对齐 CRM/首页卡片） */}
      <Popover.Portal>
        <Popover.Content
          side="bottom"
          align="start"
          sideOffset={6}
          avoidCollisions={true}
          collisionPadding={12}
          style={{ fontFamily: FEISHU_FONT_FAMILY }}
          className={cn(
            "z-50 w-[420px] max-w-[calc(100vw-32px)] rounded-[12px] border border-[#DEE0E3] bg-white p-4 text-[#1F2329] shadow-[0_4px_24px_rgba(0,0,0,0.12)] outline-none select-none",
            "animate-in fade-in-0 zoom-in-95 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95 duration-150"
          )}
        >
          {/* 1. 卡片头部：引用序号徽标 + 文档标题 + 关闭叉号 */}
          <div className="flex items-center justify-between gap-2 min-w-0">
            <div className="flex items-center gap-2 min-w-0 flex-1">
              <span className="inline-flex items-center justify-center h-5 px-1.5 rounded-[4px] bg-[#F0F4FF] border border-[#DEE0E3]/70 text-[#3370FF] text-[12px] font-semibold shrink-0">
                [{citationId}]
              </span>
              <span
                className="text-[14px] font-medium text-[#1F2329] truncate leading-5"
                title={detail?.title || `引用 [${citationId}]`}
              >
                {detail?.title || (isLoading ? "正在加载引用详情..." : `引用 [${citationId}]`)}
              </span>
            </div>

            <Popover.Close asChild>
              <button
                type="button"
                className="w-6 h-6 rounded-[4px] flex items-center justify-center text-[#8F959E] hover:text-[#1F2329] hover:bg-[#EFF0F1] transition-colors cursor-pointer shrink-0 outline-none"
                aria-label="关闭"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </Popover.Close>
          </div>

          {/* 2. 核心内容区：纯白背景，复用 FeishuMarkdown 渲染体系 */}
          <div className="mt-3">
            {isLoading ? (
              <div className="space-y-2.5 py-1 animate-pulse">
                <div className="h-3.5 bg-[#F0F2F5] rounded w-full" />
                <div className="h-3.5 bg-[#F0F2F5] rounded w-4/5" />
                <div className="h-3.5 bg-[#F0F2F5] rounded w-3/5" />
              </div>
            ) : error ? (
              <div className="flex items-start gap-2 text-[13px] text-[#F54A45] py-1">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <span className="leading-snug">{error}</span>
              </div>
            ) : (
              <div className="max-h-[220px] overflow-y-auto feishu-dropdown-scrollbar select-text pr-1">
                <FeishuMarkdown content={detail?.content || "暂无分块内容"} />
              </div>
            )}
          </div>

          {/* 3. 底部操作区：打开原文 */}
          {!isLoading && !error && detail && (detail.sourceUrl || detail.documentPath) && (
            <div className="mt-3.5 pt-2.5 border-t border-[#F2F3F5] flex items-center justify-between">
              <button
                type="button"
                className="inline-flex items-center gap-1.5 text-[13px] font-medium text-[#3370FF] hover:text-[#1456F0] py-1 px-1.5 -ml-1.5 rounded-[4px] hover:bg-[#F0F4FF] transition-colors cursor-pointer outline-none"
                onClick={handleOpenDocument}
              >
                <span>打开原文</span>
                <ExternalLink className="w-3.5 h-3.5 shrink-0" />
              </button>
            </div>
          )}
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
};

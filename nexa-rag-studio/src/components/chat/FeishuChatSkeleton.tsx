import React from "react";
import { BaseAgentChatbotOutlinedIcon } from "./FeishuChatIcons";
import { FeishuShinyText } from "./FeishuShinyText";

export interface FeishuChatSkeletonProps {
  elapsedSeconds: number;
  showHeader?: boolean;
}

/** 单组飞书字段级骨架屏行列表 (用于无缝首尾拼接循环滚动) */
const FeishuSkeletonGroup: React.FC = () => (
  <>
    {/* 文本字段 1 */}
    <div className="bt-comp-web-skeleton-screen__row">
      <span className="bt-comp-web-skeleton-screen__icon font-mono">A=</span>
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-24" />
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-64" />
    </div>

    {/* 文本字段 2 */}
    <div className="bt-comp-web-skeleton-screen__row">
      <span className="bt-comp-web-skeleton-screen__icon font-mono">A=</span>
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-24" />
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-80" />
    </div>

    {/* 时间日期字段 */}
    <div className="bt-comp-web-skeleton-screen__row">
      <span className="bt-comp-web-skeleton-screen__icon">🕒</span>
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-24" />
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-48" />
    </div>

    {/* 附件/卡片字段 */}
    <div className="bt-comp-web-skeleton-screen__row bt-comp-web-skeleton-screen__row--start">
      <span className="bt-comp-web-skeleton-screen__icon mt-0.5">📎</span>
      <div className="space-y-2">
        <div className="bt-comp-web-skeleton-screen__block h-3.5 w-24" />
        <div className="bt-comp-web-skeleton-screen__block h-14 w-36 rounded-[6px]" />
      </div>
    </div>

    {/* 单选/选项字段 */}
    <div className="bt-comp-web-skeleton-screen__row pt-0.5">
      <span className="bt-comp-web-skeleton-screen__icon font-mono">≡:</span>
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-24" />
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-72" />
    </div>

    {/* 数字字段 */}
    <div className="bt-comp-web-skeleton-screen__row">
      <span className="bt-comp-web-skeleton-screen__icon font-mono">#</span>
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-24" />
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-44" />
    </div>

    {/* 人员字段 */}
    <div className="bt-comp-web-skeleton-screen__row">
      <span className="bt-comp-web-skeleton-screen__icon font-mono">@</span>
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-24" />
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-56" />
    </div>

    {/* 超链接字段 */}
    <div className="bt-comp-web-skeleton-screen__row">
      <span className="bt-comp-web-skeleton-screen__icon">🔗</span>
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-24" />
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-60" />
    </div>

    {/* 复选框字段 */}
    <div className="bt-comp-web-skeleton-screen__row">
      <span className="bt-comp-web-skeleton-screen__icon">✓</span>
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-24" />
      <div className="bt-comp-web-skeleton-screen__block h-3.5 w-36" />
    </div>
  </>
);

/**
 * 1:1 飞书原版动态滚动流式思考骨架屏
 * 包含：
 * 1. AI 扫光状态头 (FeishuShinyText，可通过 showHeader 控制)
 * 2. 带有上下羽化渐变遮罩 (Mask Image) 的视口窗口
 * 3. 向上无限循环平滑滚动的双重骨架屏数据流 (.bt-comp-web-skeleton-screen)
 */
export const FeishuChatSkeleton: React.FC<FeishuChatSkeletonProps> = ({
  elapsedSeconds,
  showHeader = true,
}) => {
  return (
    <div className="space-y-3 py-1 animate-in fade-in duration-200">
      {/* 顶部运行状态与文字流光 (仅在无外部状态头时渲染，避免双重头部) */}
      {showHeader && (
        <div className="flex items-center gap-2 text-[12px] text-[#8F959E]">
          <div className="w-6 h-6 rounded-[6px] border border-[#DEE0E3] bg-white flex items-center justify-center shrink-0 shadow-2xs">
            <BaseAgentChatbotOutlinedIcon className="w-3.5 h-3.5 text-[#8F959E]" />
          </div>
          <FeishuShinyText secondaryColor="#8F959E" contrastColor="#DEE0E3">
            已运行 {elapsedSeconds} 秒
          </FeishuShinyText>
        </div>
      )}

      {/* 1:1 飞书动态滚动骨架屏视口 (上下渐变羽化遮罩 + 持续向上滚动数据流) */}
      <div
        className="relative h-[190px] max-w-[628px] overflow-hidden select-none"
        style={{
          maskImage: "linear-gradient(to bottom, transparent 0%, rgba(0,0,0,1) 16%, rgba(0,0,0,1) 82%, transparent 100%)",
          WebkitMaskImage: "linear-gradient(to bottom, transparent 0%, rgba(0,0,0,1) 16%, rgba(0,0,0,1) 82%, transparent 100%)",
        }}
      >
        <div className="bt-comp-web-skeleton-screen">
          {/* 第 1 组骨架屏字段 */}
          <FeishuSkeletonGroup />

          {/* 第 2 组骨架屏字段 (用于首尾无缝衔接无限滚动) */}
          <FeishuSkeletonGroup />
        </div>
      </div>
    </div>
  );
};

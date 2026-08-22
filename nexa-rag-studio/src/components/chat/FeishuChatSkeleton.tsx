import React from "react";
import {
  BaseAgentChatbotOutlinedIcon,
  FieldTextAiIcon,
  FieldDateClockIcon,
  FieldAttachmentPaperclipIcon,
  FieldSingleSelectLinesIcon,
  FieldNumberPoundIcon,
  FieldCheckboxCheckedIcon,
} from "./FeishuChatIcons";
import { FeishuShinyText } from "./FeishuShinyText";

export interface FeishuChatSkeletonProps {
  elapsedSeconds: number;
  showHeader?: boolean;
}

/** 单组 1:1 飞书字段级骨架屏行列表 (用于无缝首尾拼接 -50% 循环滚动) */
const FeishuSkeletonGroup: React.FC = () => (
  <div className="bt-comp-web-skeleton-screen__group">
    {/* 1. 文本字段 (A✨ 胶囊标题 + 长内容胶囊) */}
    <div className="bt-comp-web-skeleton-screen__row">
      <div className="bt-comp-web-skeleton-screen__icon">
        <FieldTextAiIcon className="w-3.5 h-3.5 text-[#8F959E]" />
      </div>
      <div className="bt-comp-web-skeleton-screen__block w-24" />
      <div className="bt-comp-web-skeleton-screen__block w-64" />
    </div>

    {/* 2. 日期/时间字段 (🕒 胶囊标题 + 中长内容胶囊) */}
    <div className="bt-comp-web-skeleton-screen__row">
      <div className="bt-comp-web-skeleton-screen__icon">
        <FieldDateClockIcon className="w-3.5 h-3.5 text-[#8F959E]" />
      </div>
      <div className="bt-comp-web-skeleton-screen__block w-24" />
      <div className="bt-comp-web-skeleton-screen__block w-44" />
    </div>

    {/* 3. 附件字段 (📎 胶囊标题 + 下方 1:1 圆角卡片占位) */}
    <div className="bt-comp-web-skeleton-screen__row bt-comp-web-skeleton-screen__row--start">
      <div className="bt-comp-web-skeleton-screen__icon mt-0.5">
        <FieldAttachmentPaperclipIcon className="w-3.5 h-3.5 text-[#8F959E]" />
      </div>
      <div className="space-y-2">
        <div className="bt-comp-web-skeleton-screen__block w-24" />
        <div className="h-20 w-36 rounded-[10px] bg-[#F5F6F7] shrink-0" />
      </div>
    </div>

    {/* 4. 单选/选项字段 (≡: 胶囊标题 + 最长内容胶囊) */}
    <div className="bt-comp-web-skeleton-screen__row">
      <div className="bt-comp-web-skeleton-screen__icon">
        <FieldSingleSelectLinesIcon className="w-3.5 h-3.5 text-[#8F959E]" />
      </div>
      <div className="bt-comp-web-skeleton-screen__block w-24" />
      <div className="bt-comp-web-skeleton-screen__block w-72" />
    </div>

    {/* 5. 数字字段 (# 胶囊标题 + 中短内容胶囊) */}
    <div className="bt-comp-web-skeleton-screen__row">
      <div className="bt-comp-web-skeleton-screen__icon">
        <FieldNumberPoundIcon className="w-3.5 h-3.5 text-[#8F959E]" />
      </div>
      <div className="bt-comp-web-skeleton-screen__block w-24" />
      <div className="bt-comp-web-skeleton-screen__block w-40" />
    </div>

    {/* 6. 复选框字段 (☑ 胶囊标题 + 中长内容胶囊) */}
    <div className="bt-comp-web-skeleton-screen__row">
      <div className="bt-comp-web-skeleton-screen__icon">
        <FieldCheckboxCheckedIcon className="w-3.5 h-3.5 text-[#8F959E]" />
      </div>
      <div className="bt-comp-web-skeleton-screen__block w-24" />
      <div className="bt-comp-web-skeleton-screen__block w-52" />
    </div>
  </div>
);

/**
 * 1:1 飞书原版动态滚动流式思考骨架屏
 * 包含：
 * 1. AI 扫光状态头 (FeishuShinyText，可通过 showHeader 控制)
 * 2. 带有上下羽化渐变遮罩 (Mask Image) 的视口窗口
 * 3. 向上无限循环 100% 丝滑无抖动滚动的双重骨架屏数据流 (.bt-comp-web-skeleton-screen)
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
        className="relative h-[200px] max-w-[628px] overflow-hidden select-none"
        style={{
          maskImage:
            "linear-gradient(to bottom, transparent 0%, rgba(0,0,0,1) 16%, rgba(0,0,0,1) 82%, transparent 100%)",
          WebkitMaskImage:
            "linear-gradient(to bottom, transparent 0%, rgba(0,0,0,1) 16%, rgba(0,0,0,1) 82%, transparent 100%)",
        }}
      >
        <div className="bt-comp-web-skeleton-screen">
          {/* 第 1 组骨架屏字段 */}
          <FeishuSkeletonGroup />

          {/* 第 2 组骨架屏字段 (首尾无缝衔接 -50% 循环滚动，绝对无抽帧/断帧) */}
          <FeishuSkeletonGroup />
        </div>
      </div>
    </div>
  );
};

import React, { useRef, useEffect, useState } from "react";

export interface TabItem<T extends string = string> {
  key: T;
  label: string;
}

interface FeishuAuthTabsProps<T extends string = string> {
  tabs: TabItem<T>[];
  activeKey: T;
  onChange: (key: T) => void;
  className?: string;
}

/**
 * 飞书 1:1 Tab 切换栏 (.base-tabs-bar-container)
 *
 * 原版 CSS 规格：
 * - 高度：28px
 * - 行高：24px
 * - 字号：14px
 * - 下边距：margin-bottom: 12px
 * - 右侧外边距：32px
 * - 底部指示条：平滑滑动 (0.2s cubic-bezier(0.645, 0.045, 0.355, 1))
 */
export const FeishuAuthTabs = <T extends string = string>({
  tabs,
  activeKey,
  onChange,
  className = "",
}: FeishuAuthTabsProps<T>) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [indicatorStyle, setIndicatorStyle] = useState<{ left: number; width: number }>({
    left: 0,
    width: 0,
  });

  useEffect(() => {
    if (!containerRef.current) return;
    const activeEl = containerRef.current.querySelector<HTMLElement>(".base-tabs-bar-active");
    if (activeEl) {
      setIndicatorStyle({
        left: activeEl.offsetLeft,
        width: activeEl.offsetWidth,
      });
    }
  }, [activeKey, tabs]);

  return (
    <div
      ref={containerRef}
      className={`pp-base-tabs base-tabs-container relative flex items-center mb-[18px] ${className}`}
    >
      {tabs.map((tab) => {
        const isActive = tab.key === activeKey;
        return (
          <div
            key={tab.key}
            onClick={() => onChange(tab.key)}
            className={`base-tabs-bar relative h-[28px] leading-[24px] mr-[32px] text-[14px] cursor-pointer select-none transition-colors duration-200 ${
              isActive
                ? "base-tabs-bar-active text-[#3370ff] font-medium"
                : "text-[#646a73] hover:text-[#3370ff] font-normal"
            }`}
          >
            {tab.label}
          </div>
        );
      })}

      {/* 飞书 1:1 平滑滑动底部 2px 蓝色指示条 */}
      {indicatorStyle.width > 0 && (
        <span
          className="absolute bottom-0 h-[2px] bg-[#3370ff] rounded-t-[2px] transition-all duration-200 ease-[cubic-bezier(0.645,0.045,0.355,1)] pointer-events-none"
          style={{
            left: `${indicatorStyle.left}px`,
            width: `${indicatorStyle.width}px`,
          }}
        />
      )}
    </div>
  );
};

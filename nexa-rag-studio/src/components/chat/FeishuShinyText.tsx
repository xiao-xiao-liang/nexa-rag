import React from "react";

export interface FeishuShinyTextProps extends React.HTMLAttributes<HTMLSpanElement> {
  children: React.ReactNode;
  disabled?: boolean;
  secondaryColor?: string;
  contrastColor?: string;
  duration?: string;
  className?: string;
}

/**
 * 1:1 飞书原版 AI Shiny Text 文字流光组件
 * 对应飞书类名体系：bt-comp-web-ai-shiny-text 与 @keyframes shiny-text
 */
export const FeishuShinyText: React.FC<FeishuShinyTextProps> = ({
  children,
  disabled = false,
  secondaryColor,
  contrastColor,
  duration,
  className = "",
  style,
  ...restProps
}) => {
  const customStyles: React.CSSProperties = {
    ...(secondaryColor ? ({ "--shiny-text-secondary": secondaryColor } as React.CSSProperties) : {}),
    ...(contrastColor ? ({ "--shimmer-contrast": contrastColor } as React.CSSProperties) : {}),
    ...(duration ? ({ "--shiny-animation-duration": duration } as React.CSSProperties) : {}),
    ...style,
  };

  return (
    <span
      className={`bt-comp-web-ai-shiny-text ${disabled ? "bt-comp-web-ai-shiny-text--disable-shiny" : ""} ${className}`.trim()}
      style={customStyles}
      {...restProps}
    >
      <span className="bt-comp-web-ai-shiny-text__text">{children}</span>
    </span>
  );
};

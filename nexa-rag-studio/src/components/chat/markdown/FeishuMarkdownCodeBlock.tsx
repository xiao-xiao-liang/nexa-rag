import React, { useState } from "react";
import { FeishuCopyButton } from "../FeishuCopyButton";

export interface FeishuCodeBlockProps {
  inline?: boolean;
  className?: string;
  children?: React.ReactNode;
}

/**
 * 1:1 飞书代码块与行内代码组件
 */
export const FeishuCodeBlock: React.FC<FeishuCodeBlockProps> = ({
  inline,
  className = "",
  children,
}) => {
  const [isCopied, setIsCopied] = useState(false);

  // 提取语言名称 (例如 class "language-json" -> "JSON")
  const match = /language-(\w+)/.exec(className || "");
  const language = match ? match[1].toUpperCase() : "";

  // 提取纯文本内容
  const codeString = String(children || "").replace(/\n$/, "");

  // 行内代码渲染 (inline code)
  if (inline) {
    return (
      <code className="base-chatbot-maker-md-comp-inlineCode">
        {children}
      </code>
    );
  }

  // 代码块渲染 (fenced code block)
  const handleCopy = () => {
    navigator.clipboard.writeText(codeString);
    setIsCopied(true);
    setTimeout(() => setIsCopied(false), 2000);
  };

  return (
    <div className="base-chatbot-maker-md-comp-codeBlock-wrapper group my-2 select-text">
      {/* 顶部工具栏 (显示语言标识与复制按钮) */}
      <div className="base-chatbot-maker-md-comp-codeBlock-header">
        <span className="font-mono font-medium text-[11px] tracking-wide text-[#646A73]">
          {language || "CODE"}
        </span>
        <FeishuCopyButton
          onCopy={handleCopy}
          isCopied={isCopied}
          tooltipText="复制代码"
          copiedTooltipText="已复制"
        />
      </div>

      {/* 代码正文 */}
      <pre className="base-chatbot-maker-md-comp-codeBlock custom-scrollbar">
        <code>{children}</code>
      </pre>
    </div>
  );
};

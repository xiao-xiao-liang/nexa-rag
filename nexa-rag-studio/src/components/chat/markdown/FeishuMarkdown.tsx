import React, { useMemo } from "react";
import Markdown, { Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  FeishuTable,
  FeishuThead,
  FeishuTbody,
  FeishuTr,
  FeishuTh,
  FeishuTd,
} from "./FeishuMarkdownTable";
import { FeishuCodeBlock } from "./FeishuMarkdownCodeBlock";
import { parseFeishuMessageContent } from "./utils";

export interface FeishuMarkdownProps {
  content: string;
  isGenerating?: boolean;
  className?: string;
}

/**
 * 1:1 飞书多维表格智能体回答区 Markdown Component Renderer
 * 严格对齐 DESIGN.md, index.html 与 style.css 渲染规范
 */
export const FeishuMarkdown: React.FC<FeishuMarkdownProps> = ({
  content,
  isGenerating = false,
  className = "",
}) => {
  // 1. 智能解析消息文本（兼容纯 Markdown 与飞书 ops JSON 结构体）
  const parsedMessage = useMemo(() => {
    return parseFeishuMessageContent(content);
  }, [content]);

  // 2. 配置 AST 组件映射表 (Componentized Renderer)
  const components: Components = useMemo(
    () => ({
      // 段落
      p: ({ children }) => (
        <p className="base-chatbot-maker-md-comp-paragraph">{children}</p>
      ),

      // 标题体系归一化 (H1 / H2 / H3-H6)
      h1: ({ children }) => (
        <div className="base-chatbot-maker-md-comp-title base-chatbot-maker-md-comp-title--h1">
          {children}
        </div>
      ),
      h2: ({ children }) => (
        <div className="base-chatbot-maker-md-comp-title base-chatbot-maker-md-comp-title--h2">
          {children}
        </div>
      ),
      h3: ({ children }) => (
        <div className="base-chatbot-maker-md-comp-title base-chatbot-maker-md-comp-title--h3">
          {children}
        </div>
      ),
      h4: ({ children }) => (
        <div className="base-chatbot-maker-md-comp-title">{children}</div>
      ),
      h5: ({ children }) => (
        <div className="base-chatbot-maker-md-comp-title">{children}</div>
      ),
      h6: ({ children }) => (
        <div className="base-chatbot-maker-md-comp-title">{children}</div>
      ),

      // 分割线 (---)
      hr: () => (
        <div className="base-chatbot-maker-md-comp-divider base-chatbot-maker-message-container__specialRenderMessage" />
      ),

      // 列表体系
      ol: ({ children }) => (
        <ol className="base-chatbot-maker-md-comp-ordered-list">{children}</ol>
      ),
      ul: ({ children, className: ulClass }) => {
        // 如果是 Task List 列表容器
        return (
          <ul
            className={
              ulClass?.includes("contains-task-list")
                ? "base-chatbot-maker-md-comp-unordered-list contains-task-list"
                : "base-chatbot-maker-md-comp-unordered-list"
            }
          >
            {children}
          </ul>
        );
      },
      li: ({ children, className: liClass, node, ...props }) => {
        // 如果是 Task List 选项
        if (liClass?.includes("task-list-item")) {
          return <li className="task-list-item">{children}</li>;
        }
        // 如果是有序列表项 (ordered 为 true)
        const isOrdered = Boolean((props as { ordered?: boolean }).ordered);
        if (isOrdered) {
          return <li>{children}</li>;
        }
        return (
          <li className="base-chatbot-maker-md-comp-unordered-list-item">
            {children}
          </li>
        );
      },

      // 引用块
      blockquote: ({ children }) => (
        <blockquote className="base-chatbot-maker-md-comp-blockquote">
          {children}
        </blockquote>
      ),

      // 链接
      a: ({ href, children }) => (
        <a
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          className="base-chatbot-maker-md-comp-link"
        >
          {children}
        </a>
      ),

      // 图片
      img: ({ src, alt }) => (
        <div className="base-chatbot-maker-md-comp-image">
          <img src={src} alt={alt || ""} loading="lazy" />
        </div>
      ),

      // 代码（行内代码与代码块）
      code: ({ className: codeClass, children, ...rest }) => {
        const isFenced = Boolean(codeClass?.includes("language-"));
        const textContent = String(children || "");
        // 含有换行符或声明了语言属性视为代码块
        const isBlock = isFenced || textContent.includes("\n");

        return (
          <FeishuCodeBlock
            inline={!isBlock}
            className={codeClass}
            {...rest}
          >
            {children}
          </FeishuCodeBlock>
        );
      },

      // 1:1 飞书自增粘性序号表格
      table: FeishuTable,
      thead: FeishuThead,
      tbody: FeishuTbody,
      tr: FeishuTr,
      th: FeishuTh,
      td: FeishuTd,
    }),
    []
  );

  return (
    <div className={`base-chatbot-maker-md-root ${className}`}>
      <Markdown
        remarkPlugins={[remarkGfm]}
        components={components}
      >
        {parsedMessage.markdownContent}
      </Markdown>

      {/* 流式生成中的打字呼吸圆点光标 */}
      {isGenerating && (
        <span className="base-chatbot-maker-md-dotContainer">
          <span className="base-chatbot-maker-md-dotFlash" />
        </span>
      )}
    </div>
  );
};

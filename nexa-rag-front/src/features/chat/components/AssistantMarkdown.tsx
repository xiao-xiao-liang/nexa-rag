import { isValidElement, memo, useMemo, type ReactNode } from 'react'
import ReactMarkdown, { type Components } from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import rehypeKatex from 'rehype-katex'
import remarkGfm from 'remark-gfm'
import remarkMath from 'remark-math'
import type { MessageStatus } from '@/features/conversations/api/conversation-api'
import { MarkdownCodeBlock } from './MarkdownCodeBlock'
import { MermaidDiagram } from './MermaidDiagram'
import { normalizeMathDelimiters } from './markdown-normalizer'

interface AssistantMarkdownProps {
  content: string
  status: MessageStatus
}

/** 助手回答的安全 Markdown 渲染入口。 */
export const AssistantMarkdown = memo(function AssistantMarkdown({ content, status }: AssistantMarkdownProps) {
  const components = useMemo(() => createMarkdownComponents(status), [status])

  return <div className="assistant-markdown">
    <ReactMarkdown remarkPlugins={[remarkGfm, remarkMath]} rehypePlugins={[rehypeKatex, rehypeHighlight]} components={components}>
      {normalizeMathDelimiters(content)}
    </ReactMarkdown>
  </div>
})

function createMarkdownComponents(status: MessageStatus): Components {
  return {
  a: ({ children, href, ...props }) => <a {...props} href={href} target="_blank" rel="noreferrer noopener">{children}</a>,
  pre: ({ children }) => <>{children}</>,
  code: ({ children, className, node, ...props }) => {
    const language = /language-([\w-]+)/.exec(className || '')?.[1]
    const code = getTextContent(children).replace(/\n$/, '')
    const isBlock = node?.position?.start.line !== node?.position?.end.line
    if (!isBlock) {
      return <code {...props} className={className}>{children}</code>
    }
    if (language === 'mermaid') {
      return <MermaidDiagram code={code} status={status} />
    }
    return <MarkdownCodeBlock code={code} language={language}>{children}</MarkdownCodeBlock>
  },
  }
}

/** 提取高亮节点中的原始文本，供复制功能使用。 */
function getTextContent(node: ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') {
    return String(node)
  }
  if (Array.isArray(node)) {
    return node.map(getTextContent).join('')
  }
  if (isValidElement<{ children?: ReactNode }>(node)) {
    return getTextContent(node.props.children as ReactNode)
  }
  return ''
}

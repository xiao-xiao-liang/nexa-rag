import { useState, type ReactNode } from 'react'

interface MarkdownCodeBlockProps {
  code: string
  language?: string
  children: ReactNode
}

/** Markdown 普通代码块，提供语言标识和复制反馈。 */
export function MarkdownCodeBlock({ code, language, children }: MarkdownCodeBlockProps) {
  const [copyState, setCopyState] = useState<'idle' | 'success' | 'error'>('idle')

  const copyCode = async () => {
    // 1. 优先复制原始代码，避免高亮标签影响剪贴板内容。
    try {
      await navigator.clipboard.writeText(code)
      setCopyState('success')
    } catch {
      // 2. 浏览器未授权剪贴板时保留代码内容并提示用户。
      setCopyState('error')
    }
  }

  const copyLabel = copyState === 'success' ? '已复制' : copyState === 'error' ? '复制失败' : '复制'
  return <div className="my-3 overflow-hidden rounded-lg border border-[#373d4a] bg-[#1f2430] text-[#e6edf3]">
    <div className="flex items-center justify-between border-b border-[#373d4a] px-3 py-1.5 text-xs text-[#b8c0cc]">
      <span>{language || '文本'}</span>
      <button type="button" className="rounded px-1.5 py-0.5 hover:bg-white/10" onClick={() => void copyCode()}>{copyLabel}</button>
    </div>
    <pre className="overflow-x-auto p-3 text-xs leading-6"><code>{children}</code></pre>
  </div>
}

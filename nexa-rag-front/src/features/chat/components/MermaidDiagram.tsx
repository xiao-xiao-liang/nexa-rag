import { useEffect, useState } from 'react'
import mermaid from 'mermaid'
import type { MessageStatus } from '@/features/conversations/api/conversation-api'
import { MarkdownCodeBlock } from './MarkdownCodeBlock'

interface MermaidDiagramProps {
  code: string
  status: MessageStatus
}

/** Mermaid 图表渲染组件，失败时降级展示原始代码。 */
export function MermaidDiagram({ code, status }: MermaidDiagramProps) {
  const [svg, setSvg] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    // 1. 流式生成期间保留原始代码，避免每个 TOKEN 都重建图表。
    if (status !== 'COMPLETED') {
      setSvg(null)
      setFailed(false)
      return
    }

    let disposed = false
    setSvg(null)
    setFailed(false)
    mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: 'neutral' })

    // 2. Mermaid 的严格模式负责限制图表定义中的脚本与交互能力。
    void mermaid.render(`mermaid-${crypto.randomUUID()}`, code)
      .then(({ svg: renderedSvg }) => {
        if (!disposed) {
          setSvg(renderedSvg)
        }
      })
      .catch(() => {
        if (!disposed) {
          setFailed(true)
        }
      })

    return () => {
      disposed = true
    }
  }, [code, status])

  if (svg) {
    return <div className="my-3 overflow-x-auto rounded-lg border bg-white p-3" aria-label="Mermaid 图表" dangerouslySetInnerHTML={{ __html: svg }} />
  }

  return <div>
    {failed && <p className="mb-2 text-xs text-red-600">图表渲染失败</p>}
    <MarkdownCodeBlock code={code} language="mermaid">{code}</MarkdownCodeBlock>
  </div>
}

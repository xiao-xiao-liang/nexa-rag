import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AssistantMarkdown } from './AssistantMarkdown'

const mermaid = vi.hoisted(() => ({ initialize: vi.fn(), render: vi.fn() }))

vi.mock('mermaid', () => ({ default: mermaid }))

describe('AssistantMarkdown', () => {
  it('应渲染 GFM、公式和普通代码块', () => {
    render(<AssistantMarkdown status="COMPLETED" content={'## 标题\n\n| 参数 | 建议 |\n| --- | --- |\n| batch_size | 2 |\n\n\\(x^2\\)\n\n```ts\nconst value = 1\n```'} />)

    expect(screen.getByRole('heading', { name: '标题' })).toBeInTheDocument()
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(document.querySelector('.katex')).not.toBeNull()
    expect(document.querySelector('code')?.textContent).toContain('const value = 1')
    expect(screen.getByRole('button', { name: '复制' })).toBeInTheDocument()
  })

  it('不应执行模型返回的原始 HTML', () => {
    const { container } = render(<AssistantMarkdown status="COMPLETED" content={'<img src=x onerror=alert(1) />'} />)

    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText('<img src=x onerror=alert(1) />')).toBeInTheDocument()
  })

  it('生成中应展示 Mermaid 原始代码，完成后应渲染图表', async () => {
    mermaid.render.mockResolvedValue({ svg: '<svg><text>流程图</text></svg>' })
    const content = '```mermaid\ngraph TD; A-->B\n```'
    const { rerender } = render(<AssistantMarkdown status="GENERATING" content={content} />)

    expect(screen.getByText('graph TD; A-->B')).toBeInTheDocument()
    rerender(<AssistantMarkdown status="COMPLETED" content={content} />)

    expect(await screen.findByLabelText('Mermaid 图表')).toHaveTextContent('流程图')
    expect(mermaid.initialize).toHaveBeenCalledWith(expect.objectContaining({ securityLevel: 'strict' }))
  })

  it('Mermaid 渲染异常时应回退为原始代码并提示', async () => {
    mermaid.render.mockRejectedValueOnce(new Error('语法错误'))
    render(<AssistantMarkdown status="COMPLETED" content={'```mermaid\n错误图表\n```'} />)

    expect(await screen.findByText('图表渲染失败')).toBeInTheDocument()
    expect(screen.getByText('错误图表')).toBeInTheDocument()
  })
})

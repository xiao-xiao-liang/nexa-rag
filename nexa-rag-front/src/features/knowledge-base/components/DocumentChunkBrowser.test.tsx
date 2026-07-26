import { useState } from 'react'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { DocumentChunk } from '../api/document-api'
import { DocumentChunkBrowser } from './DocumentChunkBrowser'

const chunks: DocumentChunk[] = [
  { chunkId: 'c-1', documentId: 8, chunkOrder: 1, text: '第一段完整内容', status: 'INDEXED' },
  { chunkId: 'c-2', documentId: 8, chunkOrder: 2, text: '第二段完整内容', status: 'INDEXED' },
]

/** 分块浏览组件的交互测试宿主。 */
function ChunkBrowserHarness() {
  const [selectedChunk, setSelectedChunk] = useState<DocumentChunk | null>(null)
  return <DocumentChunkBrowser chunks={chunks} sourceFileName="员工手册.pdf" loading={false} error={null} selectedChunk={selectedChunk} pagination={null} onSelect={setSelectedChunk} onClose={() => setSelectedChunk(null)} onRetry={vi.fn()} />
}

/** 文本分块主从浏览组件测试。 */
describe('DocumentChunkBrowser', () => {
  afterEach(() => {
    cleanup()
  })

  it('点击分块卡片后展示完整内容，并可关闭阅读区', async () => {
    const user = userEvent.setup()
    render(<ChunkBrowserHarness />)

    expect(screen.queryByRole('region', { name: '分块完整内容' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '查看分块 2' }))
    expect(screen.getByRole('region', { name: '分块完整内容' })).toHaveTextContent('第二段完整内容')
    await user.click(screen.getByRole('button', { name: '关闭分块内容' }))
    expect(screen.queryByRole('region', { name: '分块完整内容' })).not.toBeInTheDocument()
  })

  it('选中卡片应暴露选中态，摘要最多占三行样式', async () => {
    const user = userEvent.setup()
    render(<ChunkBrowserHarness />)
    const card = screen.getByRole('button', { name: '查看分块 1' })

    expect(card).toHaveAttribute('aria-pressed', 'false')
    expect(card.querySelector('.line-clamp-3')).toHaveTextContent('第一段完整内容')
    await user.click(card)
    expect(card).toHaveAttribute('aria-pressed', 'true')
  })

  it('应以网格卡片展示来源文件，并在详情栏显示来源', async () => {
    const user = userEvent.setup()
    const { container } = render(<ChunkBrowserHarness />)

    expect(container.querySelector('.lg\\:grid-cols-3')).toBeInTheDocument()
    expect(screen.getAllByText('员工手册.pdf')).toHaveLength(2)
    await user.click(screen.getByRole('button', { name: '查看分块 1' }))
    expect(screen.getByRole('region', { name: '分块完整内容' })).toHaveTextContent('员工手册.pdf')
  })

  it('应提供知识块筛选，并保留固定的空白详情栏', async () => {
    const user = userEvent.setup()
    render(<ChunkBrowserHarness />)

    expect(screen.getByRole('complementary', { name: '分块详情栏' })).toBeEmptyDOMElement()
    await user.type(screen.getByRole('textbox', { name: '搜索文本分块' }), '第二段')
    expect(screen.queryByRole('button', { name: '查看分块 1' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '查看分块 2' })).toBeInTheDocument()
  })

  it('应在左侧列表显示加载、错误和空状态', () => {
    const { rerender } = render(<DocumentChunkBrowser chunks={[]} sourceFileName="员工手册.pdf" loading error={null} selectedChunk={null} pagination={null} onSelect={vi.fn()} onClose={vi.fn()} onRetry={vi.fn()} />)
    expect(screen.getByText('正在加载文本分块…')).toBeInTheDocument()

    rerender(<DocumentChunkBrowser chunks={[]} sourceFileName="员工手册.pdf" loading={false} error="加载失败" selectedChunk={null} pagination={null} onSelect={vi.fn()} onClose={vi.fn()} onRetry={vi.fn()} />)
    expect(screen.getByText('加载失败')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '重新加载' })).toBeInTheDocument()

    rerender(<DocumentChunkBrowser chunks={[]} sourceFileName="员工手册.pdf" loading={false} error={null} selectedChunk={null} pagination={null} onSelect={vi.fn()} onClose={vi.fn()} onRetry={vi.fn()} />)
    expect(screen.getByText('暂无可展示的文本分块。')).toBeInTheDocument()
  })
})

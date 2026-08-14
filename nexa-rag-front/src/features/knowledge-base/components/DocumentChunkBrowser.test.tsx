import { useState } from 'react'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { DocumentChunk } from '../api/document-api'
import { DocumentChunkBrowser, type ChunkViewMode } from './DocumentChunkBrowser'

const chunks: DocumentChunk[] = [
  { chunkId: 'c-1', documentId: 8, chunkOrder: 1, text: '第一段完整内容', status: 'INDEXED' },
  { chunkId: 'c-2', documentId: 8, chunkOrder: 2, text: '第二段完整内容', status: 'INDEXED' },
]

const baseProps = {
  chunks,
  total: 2,
  sourceFileName: '员工手册.md',
  fileDescription: '手工验收写入：真实 Markdown 文件切分结果',
  viewMode: 'preview' as ChunkViewMode,
  onViewModeChange: vi.fn(),
  loading: false,
  loadingMore: false,
  hasMore: false,
  error: null,
  selectedChunk: null,
  onSelect: vi.fn(),
  onRetry: vi.fn(),
  onRefresh: vi.fn(),
}

/** 分块浏览组件的交互测试宿主。 */
function ChunkBrowserHarness({ chunks: chunksOverride, total: totalOverride }: { chunks?: DocumentChunk[]; total?: number } = {}) {
  const [viewMode, setViewMode] = useState<ChunkViewMode>('preview')
  const [selectedChunk, setSelectedChunk] = useState<DocumentChunk | null>(null)
  return (
    <DocumentChunkBrowser
      {...baseProps}
      chunks={chunksOverride ?? baseProps.chunks}
      total={totalOverride ?? baseProps.total}
      viewMode={viewMode}
      onViewModeChange={setViewMode}
      selectedChunk={selectedChunk}
      onSelect={setSelectedChunk}
    />
  )
}

/** 知识块卡片网格组件测试（详情抽屉在页面层级测试）。 */
describe('DocumentChunkBrowser', () => {
  afterEach(() => {
    cleanup()
  })

  it('选中卡片应暴露选中态并应用浅蓝底样式，摘要最多占四行', async () => {
    const user = userEvent.setup()
    render(<ChunkBrowserHarness />)
    const card = screen.getByRole('button', { name: '查看分块 1' })

    expect(card).toHaveAttribute('aria-pressed', 'false')
    expect(card.querySelector('.line-clamp-4')).toHaveTextContent('第一段完整内容')
    await user.click(card)
    expect(card).toHaveAttribute('aria-pressed', 'true')
    expect(card).toHaveClass('bg-primary-light/40')
  })

  it('默认预览隐藏 Markdown 标题符号，切换原文后展示原文', async () => {
    const user = userEvent.setup()
    const markdownChunk: DocumentChunk = {
      chunkId: 'm-1',
      documentId: 8,
      chunkOrder: 1,
      text: '## 1. AppId（应用唯一标识）\n服务端颁发给调用方的高度机密字符串。',
      status: 'INDEXED',
    }
    render(<ChunkBrowserHarness chunks={[markdownChunk]} total={1} />)

    const card = screen.getByRole('button', { name: '查看分块 1' })
    expect(card).toHaveTextContent('1. AppId（应用唯一标识）')
    expect(card).not.toHaveTextContent('## 1.')

    await user.click(screen.getByRole('button', { name: '原文' }))
    expect(card).toHaveTextContent('## 1. AppId（应用唯一标识）')
  })

  it('应提供知识块筛选', async () => {
    const user = userEvent.setup()
    render(<ChunkBrowserHarness />)

    await user.type(screen.getByRole('textbox', { name: '搜索当前页分块' }), '第二段')
    expect(screen.queryByRole('button', { name: '查看分块 1' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '查看分块 2' })).toBeInTheDocument()
  })

  it('应展示分块总数与文件基础信息，并保留工具栏与展示模式切换', () => {
    render(<ChunkBrowserHarness />)

    expect(screen.getByText('共 2 个知识块')).toBeInTheDocument()
    expect(screen.getByText('手工验收写入：真实 Markdown 文件切分结果')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '搜索当前页分块' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '刷新分块' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '预览' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '原文' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: '文本分块工作区' })).toBeInTheDocument()
  })

  it('应显示加载、错误和空状态', () => {
    const { rerender } = render(<DocumentChunkBrowser {...baseProps} loading error={null} />)
    expect(screen.getByText('正在加载文本分块…')).toBeInTheDocument()

    rerender(<DocumentChunkBrowser {...baseProps} loading={false} error="加载失败" />)
    expect(screen.getByText('加载失败')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '重新加载' })).toBeInTheDocument()

    rerender(<DocumentChunkBrowser {...baseProps} chunks={[]} total={0} loading={false} error={null} />)
    expect(screen.getByText('暂无可展示的文本分块。')).toBeInTheDocument()
  })
})

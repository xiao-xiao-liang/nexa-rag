import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ConversationNavigationProvider } from '@/features/conversations/ConversationNavigationContext'
import ChatWorkspace from './ChatWorkspace'

describe('ChatWorkspace', () => {
  it('渲染欢迎态与组合器', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: '0', message: null, data: { records: [], total: 0, current: 1, size: 20, pages: 0 }, traceId: null,
    }))))
    render(
      <MemoryRouter initialEntries={['/chat']}>
        <ConversationNavigationProvider>
          <ChatWorkspace />
        </ConversationNavigationProvider>
      </MemoryRouter>,
    )
    expect(await screen.findByText('你好，今天想做什么？')).toBeInTheDocument()
    expect(screen.getByLabelText('消息内容')).toBeInTheDocument()
    vi.unstubAllGlobals()
  })
})

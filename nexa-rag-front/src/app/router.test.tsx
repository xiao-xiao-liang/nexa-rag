import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { routes } from './router'

describe('应用路由', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('根路径应重定向到对话路由', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: '0', message: null, data: { records: [], total: 0, current: 1, size: 20, pages: 0 }, traceId: null,
    }))))
    const router = createMemoryRouter(routes, { initialEntries: ['/'] })

    render(<RouterProvider router={router} />)

    await waitFor(() => expect(router.state.location.pathname).toBe('/chat'))
    expect(screen.getByRole('heading', { name: '你好，今天想做什么？' })).toBeInTheDocument()
  })

  it('知识库路由应显示知识库页面', async () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/knowledge-base'] })

    render(<RouterProvider router={router} />)

    expect(await screen.findByRole('heading', { name: '知识库' })).toBeInTheDocument()
  })

  it('路由管理路由应显示路由管理页面', async () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/models/routes'] })

    render(<RouterProvider router={router} />)

    expect(await screen.findByRole('heading', { name: '路由管理' })).toBeInTheDocument()
  })

  it('设置路由应显示设置占位页', async () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/settings'] })

    render(<RouterProvider router={router} />)

    expect(await screen.findByRole('heading', { name: '设置' })).toBeInTheDocument()
  })
})

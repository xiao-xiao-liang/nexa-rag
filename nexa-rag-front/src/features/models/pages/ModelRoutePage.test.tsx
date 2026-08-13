import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import ModelRoutePage from './ModelRoutePage'

vi.mock('../api/model-api', () => ({
  getModelRoutes: vi.fn().mockResolvedValue([
    { routeId: 1, routeKey: 'DEFAULT_LLM', modelType: 'CHAT', strategy: 'FAILOVER', enabled: true },
  ]),
  getModelRouteConfigs: vi.fn().mockResolvedValue([]),
  getModelConfigs: vi.fn().mockResolvedValue([]),
  deleteModelRoute: vi.fn(),
}))

describe('ModelRoutePage', () => {
  it('渲染路由表格', async () => {
    render(
      <MemoryRouter>
        <ModelRoutePage />
      </MemoryRouter>,
    )
    expect((await screen.findAllByText('DEFAULT_LLM')).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: /新建路由/ })).toBeInTheDocument()
  })
})

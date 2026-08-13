import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Tabs } from './tabs'

describe('Tabs', () => {
  it('下划线指示当前 Tab 并触发切换', async () => {
    const onChange = vi.fn()
    render(
      <Tabs
        items={[{ value: 'all', label: '全部' }, { value: 'done', label: '已完成' }]}
        value="all"
        onChange={onChange}
      />,
    )
    const active = screen.getByRole('button', { name: '全部' })
    expect(active.className).toContain('text-primary')
    await userEvent.click(screen.getByRole('button', { name: '已完成' }))
    expect(onChange).toHaveBeenCalledWith('done')
  })
})

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Pagination } from './pagination'

describe('Pagination', () => {
  it('翻页回调与禁用边界', async () => {
    const onPageChange = vi.fn()
    render(<Pagination total={42} current={1} totalPages={3} onPageChange={onPageChange} />)
    expect(screen.getByText('共 42 条')).toBeInTheDocument()
    expect(screen.getByText('第 1 / 3 页')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: '下一页' }))
    expect(onPageChange).toHaveBeenCalledWith(2)
  })
})

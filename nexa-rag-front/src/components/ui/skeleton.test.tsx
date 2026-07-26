import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Skeleton } from './skeleton'

describe('Skeleton', () => {
  it('应渲染带无障碍标记的加载占位元素', () => {
    render(<Skeleton data-testid="加载占位" />)

    expect(screen.getByTestId('加载占位')).toHaveAttribute('aria-hidden', 'true')
  })
})

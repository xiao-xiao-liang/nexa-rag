import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Tag } from './tag'

describe('Tag', () => {
  it('按变体渲染状态色', () => {
    render(<Tag variant="success">已索引</Tag>)
    const tag = screen.getByText('已索引')
    expect(tag.className).toContain('bg-success-light')
    expect(tag.className).toContain('text-success')
  })
})

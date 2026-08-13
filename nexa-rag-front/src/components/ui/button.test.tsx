import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Button } from './button'

describe('Button', () => {
  it('渲染 danger 变体与图标尺寸', () => {
    render(<Button variant="danger" size="icon" aria-label="删除">×</Button>)
    const button = screen.getByRole('button', { name: '删除' })
    expect(button.className).toContain('bg-danger')
    expect(button.className).toContain('size-8')
  })
})

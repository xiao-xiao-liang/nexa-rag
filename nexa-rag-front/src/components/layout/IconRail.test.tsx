import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { IconRail } from './IconRail'

describe('IconRail', () => {
  it('高亮当前模块', () => {
    render(
      <MemoryRouter initialEntries={['/chat']}>
        <IconRail activeKey="chat" />
      </MemoryRouter>,
    )
    const chatLink = screen.getByRole('link', { name: /对话/ })
    expect(chatLink.className).toContain('bg-primary-light')
    expect(screen.getByRole('link', { name: /知识库/ })).toBeInTheDocument()
  })
})

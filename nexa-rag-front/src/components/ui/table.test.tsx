import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from './table'

describe('Table', () => {
  it('渲染表头与单元格', () => {
    const { container } = render(
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>文档</TableHead>
            <TableHead>状态</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow>
            <TableCell>Q3 财报.pdf</TableCell>
            <TableCell>已索引</TableCell>
          </TableRow>
        </TableBody>
      </Table>,
    )
    expect(screen.getByText('Q3 财报.pdf')).toBeInTheDocument()
    expect(container.querySelector('thead')?.className).toContain('text-secondary')
  })
})

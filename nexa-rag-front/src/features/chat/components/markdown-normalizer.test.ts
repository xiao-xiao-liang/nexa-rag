import { describe, expect, it } from 'vitest'
import { normalizeMathDelimiters } from './markdown-normalizer'

describe('normalizeMathDelimiters', () => {
  it('应将反斜杠公式定界符转换为美元定界符', () => {
    expect(normalizeMathDelimiters('行内 \\(x^2\\)\n\\[a+b\\]'))
      .toBe('行内 $x^2$\n$$a+b$$')
  })

  it('不应修改围栏代码块和行内代码', () => {
    expect(normalizeMathDelimiters('`\\(code\\)`\n```tex\n\\[block\\]\n```'))
      .toBe('`\\(code\\)`\n```tex\n\\[block\\]\n```')
  })
})

const CODE_FENCE = '```'

/**
 * 规范化模型回答中的 LaTeX 公式定界符，供 Markdown 数学插件统一解析。
 */
export function normalizeMathDelimiters(markdown: string): string {
  let result = ''
  let cursor = 0

  while (cursor < markdown.length) {
    // 1. 完整或尚未闭合的围栏代码块均原样保留，避免改写代码示例。
    if (markdown.startsWith(CODE_FENCE, cursor)) {
      const closing = markdown.indexOf(CODE_FENCE, cursor + CODE_FENCE.length)
      if (closing < 0) {
        return result + markdown.slice(cursor)
      }
      result += markdown.slice(cursor, closing + CODE_FENCE.length)
      cursor = closing + CODE_FENCE.length
      continue
    }

    // 2. 行内代码原样保留，避免公式示例被误识别。
    if (markdown[cursor] === '`') {
      const closing = markdown.indexOf('`', cursor + 1)
      if (closing < 0) {
        return result + markdown.slice(cursor)
      }
      result += markdown.slice(cursor, closing + 1)
      cursor = closing + 1
      continue
    }

    const closingDelimiter = markdown.startsWith('\\[', cursor) ? '\\]'
      : markdown.startsWith('\\(', cursor) ? '\\)' : null
    if (closingDelimiter) {
      const closing = markdown.indexOf(closingDelimiter, cursor + 2)
      if (closing >= 0) {
        const formula = markdown.slice(cursor + 2, closing)
        result += closingDelimiter === '\\]' ? `$$${formula}$$` : `$${formula}$`
        cursor = closing + closingDelimiter.length
        continue
      }
    }

    result += markdown[cursor]
    cursor += 1
  }

  return result
}

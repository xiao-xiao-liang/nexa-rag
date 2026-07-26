import { describe, expect, it } from 'vitest'
import { isProcessingStatus, isTerminalStatus, statusLabel } from './document-status'

describe('文档状态工具', () => {
  it('应识别处理中和终态文档', () => {
    expect(isProcessingStatus('PARSING')).toBe(true)
    expect(isProcessingStatus('INDEXED')).toBe(false)
    expect(isTerminalStatus('INDEXED')).toBe(true)
    expect(isTerminalStatus('FAILED')).toBe(true)
    expect(isTerminalStatus('CHUNKING')).toBe(false)
  })

  it('应提供可展示的中文状态文案', () => {
    expect(statusLabel('FAILED')).toBe('处理失败')
    expect(statusLabel('INDEXED')).toBe('已索引')
  })
})

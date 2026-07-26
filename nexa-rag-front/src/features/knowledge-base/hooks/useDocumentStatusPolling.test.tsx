import { renderHook } from '@testing-library/react'
import { act } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { getDocumentProcessStatus } from '../api/document-api'
import type { DocumentStatus } from '../document-status'
import { useDocumentStatusPolling } from './useDocumentStatusPolling'

vi.mock('../api/document-api', () => ({ getDocumentProcessStatus: vi.fn() }))

describe('useDocumentStatusPolling', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  it('处理中应立即查询、每五秒轮询且进入终态后停止', async () => {
    vi.useFakeTimers()
    const onStatus = vi.fn()
    const onError = vi.fn()
    vi.mocked(getDocumentProcessStatus).mockResolvedValue({ documentId: 8, processId: 'p-8', status: 'PARSING', messageStatus: null, consumedTimes: 1, failureStage: null, failureReason: null })
    const { rerender } = renderHook(({ status }) => useDocumentStatusPolling(8, status, onStatus, onError), { initialProps: { status: 'PARSING' as DocumentStatus } })

    expect(getDocumentProcessStatus).toHaveBeenCalledTimes(1)
    await act(async () => { await vi.advanceTimersByTimeAsync(5_000) })
    expect(getDocumentProcessStatus).toHaveBeenCalledTimes(2)

    rerender({ status: 'INDEXED' })
    await act(async () => { await vi.advanceTimersByTimeAsync(10_000) })
    expect(getDocumentProcessStatus).toHaveBeenCalledTimes(2)
  })

  it('卸载后应中止请求并清理轮询定时器', async () => {
    vi.useFakeTimers()
    const abort = vi.spyOn(AbortController.prototype, 'abort')
    vi.mocked(getDocumentProcessStatus).mockResolvedValue({ documentId: 8, processId: 'p-8', status: 'PARSING', messageStatus: null, consumedTimes: 1, failureStage: null, failureReason: null })
    const { unmount } = renderHook(() => useDocumentStatusPolling(8, 'PARSING', vi.fn(), vi.fn()))

    expect(getDocumentProcessStatus).toHaveBeenCalledTimes(1)
    unmount()
    await act(async () => { await vi.advanceTimersByTimeAsync(10_000) })

    expect(abort).toHaveBeenCalled()
    expect(getDocumentProcessStatus).toHaveBeenCalledTimes(1)
    abort.mockRestore()
  })
})

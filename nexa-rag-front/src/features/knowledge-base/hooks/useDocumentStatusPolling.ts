import { useEffect } from 'react'
import { getDocumentProcessStatus, type DocumentProcessStatus } from '../api/document-api'
import { isProcessingStatus, type DocumentStatus } from '../document-status'

/** 在文档处于处理中时每五秒刷新一次服务端处理状态。 */
export function useDocumentStatusPolling(
  documentId: number | null,
  status: DocumentStatus | null,
  onStatus: (value: DocumentProcessStatus) => void,
  onError: (error: Error) => void,
) {
  useEffect(() => {
    if (documentId === null || status === null || !isProcessingStatus(status)) {
      return
    }

    const controller = new AbortController()
    const poll = async () => {
      try {
        // 1. 使用本次 Effect 专属的取消信号，离开详情页后不再写入状态。
        const response = await getDocumentProcessStatus(documentId, controller.signal)
        onStatus(response)
      } catch (pollError) {
        // 2. 取消请求是页面切换的正常控制流，不向用户展示错误。
        if ((pollError as { name?: string }).name === 'AbortError') {
          return
        }
        onError(pollError instanceof Error ? pollError : new Error('状态查询失败，请稍后重试'))
      }
    }

    // 3. 进入处理中先立即查询，随后按固定间隔继续刷新。
    void poll()
    const timer = window.setInterval(() => void poll(), 5_000)
    return () => {
      controller.abort()
      window.clearInterval(timer)
    }
  }, [documentId, onError, onStatus, status])
}

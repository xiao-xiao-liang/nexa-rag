import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  deleteDocument, getDocument, getDocumentChunks, getDocumentProcessStatus,
  listDocuments, processDocument, retryDocument, uploadDocument,
} from './document-api'

describe('文档接口客户端', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('应按服务端分页参数查询文档列表', async () => {
    const fetchMock = vi.fn().mockResolvedValue(success({ records: [], total: 0, current: 2, size: 20, pages: 0 }))
    vi.stubGlobal('fetch', fetchMock)

    await listDocuments(2, 20)

    expect(fetchMock).toHaveBeenCalledWith('/api/documents?pageNum=2&pageSize=20', undefined)
  })

  it('上传时应只提交文件、标题和描述', async () => {
    const fetchMock = vi.fn().mockResolvedValue(success({ documentId: 8, processId: 'p-8', status: 'QUEUED' }))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['文档内容'], '员工手册.pdf', { type: 'application/pdf' })

    await uploadDocument({ file, title: '员工手册', description: '内部制度' })

    const init = fetchMock.mock.calls[0][1] as RequestInit
    const formData = init.body as FormData
    expect(init.method).toBe('POST')
    expect(new Headers(init.headers).has('Content-Type')).toBe(false)
    expect(formData.get('file')).toBe(file)
    expect(formData.get('request')).toBeInstanceOf(Blob)
    expect(await readBlob(formData.get('request') as Blob)).toBe(JSON.stringify({ title: '员工手册', description: '内部制度' }))
  })

  it('应使用文档标识请求详情、状态、分块和处理操作', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(success({})))
    vi.stubGlobal('fetch', fetchMock)

    await getDocument(8)
    await getDocumentProcessStatus(8)
    await getDocumentChunks(8, 2, 20)
    await processDocument(8)
    await retryDocument(8)
    await deleteDocument(8)

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/documents/8', undefined)
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/documents/8/process-status', undefined)
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/documents/8/chunks?pageNum=2&pageSize=20', undefined)
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/documents/8/process', { method: 'POST' })
    expect(fetchMock).toHaveBeenNthCalledWith(5, '/api/documents/8/retry', { method: 'POST' })
    expect(fetchMock).toHaveBeenNthCalledWith(6, '/api/documents/8', { method: 'DELETE' })
  })
})

function success(data: unknown) {
  return new Response(JSON.stringify({ code: '0', message: null, data, traceId: null }))
}

/** 将测试环境中的二进制请求体转换为文本。 */
function readBlob(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.addEventListener('load', () => resolve(String(reader.result)))
    reader.addEventListener('error', () => reject(reader.error))
    reader.readAsText(blob)
  })
}

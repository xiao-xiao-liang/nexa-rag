/** 后端统一响应结构。 */
export interface Result<T> {
  code: string
  message: string | null
  data: T
  traceId: string | null
}

/** 页码分页响应结构。 */
export interface PageVO<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/** 游标分页响应结构。 */
export interface CursorPageVO<T> {
  records: T[]
  hasMore: boolean
  nextBeforeSequence: number | null
}

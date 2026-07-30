import { useRouteError, isRouteErrorResponse, useNavigate } from 'react-router-dom'
import { Sparkles, Home, ArrowLeft } from 'lucide-react'
import { Button } from '@/components/ui/button'

/** 应用全局错误边界与 404 页面组件。 */
export function GlobalErrorPage() {
  const error = useRouteError()
  const navigate = useNavigate()

  let errorMessage = '发生未知错误，请稍后再试。'
  let is404 = false

  if (isRouteErrorResponse(error)) {
    if (error.status === 404) {
      is404 = true
      errorMessage = '您访问的页面不存在或已被移除。'
    } else {
      errorMessage = error.statusText || error.data?.message || errorMessage
    }
  } else if (error instanceof Error) {
    errorMessage = error.message
  }

  return (
    <div className="flex h-dvh min-h-[500px] w-full flex-col items-center justify-center bg-[#f8f8fb] px-4 text-center">
      <div className="mx-auto flex size-14 items-center justify-center rounded-2xl bg-gradient-to-br from-[#7166f7] to-[#9b8cff] text-white shadow-md">
        <Sparkles className="size-7" />
      </div>

      <h1 className="mt-6 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
        {is404 ? '404 - 页面未找到' : '应用遇到了一个小问题'}
      </h1>

      <p className="mt-3 max-w-md text-sm text-slate-500 leading-relaxed">
        {errorMessage}
      </p>

      <div className="mt-8 flex items-center justify-center gap-3">
        <Button
          variant="outline"
          size="sm"
          onClick={() => navigate(-1)}
          className="gap-2 text-xs"
        >
          <ArrowLeft className="size-3.5" />
          返回上一页
        </Button>
        <Button
          size="sm"
          onClick={() => navigate('/chat')}
          className="gap-2 bg-[#6f62e8] text-xs hover:bg-[#5f52d9]"
        >
          <Home className="size-3.5" />
          回到对话工作台
        </Button>
      </div>
    </div>
  )
}

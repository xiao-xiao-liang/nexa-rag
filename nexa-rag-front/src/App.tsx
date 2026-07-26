import { Sparkles } from 'lucide-react'

/**
 * 前端应用入口占位页，后续工作台将在此基础上组合页面模块。
 */
export default function App() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-6 text-foreground">
      <section className="flex items-center gap-3 rounded-2xl border border-border bg-card px-5 py-4 shadow-sm">
        <Sparkles aria-hidden="true" className="size-5 text-primary" />
        <p className="text-sm font-medium">Nexa RAG 前端工程已就绪</p>
      </section>
    </main>
  )
}

/** 飞书风格顶部 Toast 展示组件。 */
export function Toast({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <div className="fixed right-6 top-4 z-50" role="status">
      <div className="rounded-md bg-foreground px-4 py-2.5 text-xs font-medium text-card shadow-lg">
        {message}
      </div>
    </div>
  )
}

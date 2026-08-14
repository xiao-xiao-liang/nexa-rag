import { useEffect, useState, type PointerEvent, type ReactNode } from 'react'

const PANEL_WIDTH_KEY = 'nexa-rag.panel-width'
const MIN_WIDTH = 180
const MAX_WIDTH = 420

interface ResizablePanelProps {
  children: ReactNode
  defaultWidth?: number
}

/** 可拖拽调整宽度的左侧面板容器，宽度持久化到本地存储。 */
export function ResizablePanel({ children, defaultWidth = 232 }: ResizablePanelProps) {
  const [width, setWidth] = useState(() => readSavedWidth(defaultWidth))

  useEffect(() => {
    localStorage.setItem(PANEL_WIDTH_KEY, String(width))
  }, [width])

  const handlePointerDown = (event: PointerEvent<HTMLDivElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  const handlePointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (!event.currentTarget.hasPointerCapture(event.pointerId)) return
    setWidth(Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, event.clientX)))
  }

  return (
    <div className="flex h-full min-h-0">
      <div style={{ width }} className="shrink-0">
        <div className="h-full w-full">{children}</div>
      </div>
      <div
        role="separator"
        aria-orientation="vertical"
        aria-label="调整面板宽度"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        className="w-1 shrink-0 cursor-col-resize bg-transparent transition-colors hover:bg-primary/40"
      />
    </div>
  )
}

function readSavedWidth(defaultWidth: number): number {
  try {
    const value = Number(localStorage.getItem(PANEL_WIDTH_KEY))
    return Number.isFinite(value) && value >= MIN_WIDTH && value <= MAX_WIDTH ? value : defaultWidth
  } catch {
    return defaultWidth
  }
}

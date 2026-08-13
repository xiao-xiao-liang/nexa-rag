import { Settings } from 'lucide-react'

/** 设置占位页：后端能力未接入。 */
export function SettingsPage() {
  return (
    <div className="flex h-full min-h-0 flex-1 items-center justify-center bg-background">
      <div className="text-center">
        <Settings className="mx-auto size-8 text-tertiary" aria-hidden="true" />
        <h1 className="mt-3 text-base font-semibold text-foreground">设置</h1>
        <p className="mt-1 text-xs text-tertiary">个人偏好与系统设置将在后续阶段接入。</p>
      </div>
    </div>
  )
}

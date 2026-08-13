import { File, FileCode2, FileSpreadsheet, FileText, Presentation, type LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

type FileTypeIconSize = 'sm' | 'md' | 'lg'

interface FileTypeIconProps {
  fileName?: string | null
  fileType?: string | null
  size?: FileTypeIconSize
  className?: string
}

const SIZE_CLASS: Record<FileTypeIconSize, string> = {
  sm: 'size-4',
  md: 'size-[18px]',
  lg: 'size-5',
}

/** 按文件后缀展示对应类型的 SVG 图标（沿用 lucide 图标集，避免引入第三方图标资源）。 */
export function FileTypeIcon({ fileName, fileType, size = 'sm', className }: FileTypeIconProps) {
  const style = resolveFileStyle(fileName, fileType)
  const Icon = style.icon
  return (
    <span title={style.label} aria-label={`${style.label} 文件`} className={cn('inline-flex shrink-0 items-center justify-center', className)}>
      <Icon aria-hidden="true" className={cn(SIZE_CLASS[size], style.iconClass)} strokeWidth={1.9} />
    </span>
  )
}

function resolveFileStyle(fileName?: string | null, fileType?: string | null): { label: string; icon: LucideIcon; iconClass: string } {
  const extension = resolveExtension(fileName, fileType)
  if (extension === 'PDF') return { label: 'PDF', icon: FileText, iconClass: 'text-danger' }
  if (['DOC', 'DOCX'].includes(extension)) return { label: 'Word', icon: FileText, iconClass: 'text-primary' }
  if (['XLS', 'XLSX', 'CSV'].includes(extension)) return { label: 'Excel', icon: FileSpreadsheet, iconClass: 'text-success' }
  if (['PPT', 'PPTX'].includes(extension)) return { label: 'PowerPoint', icon: Presentation, iconClass: 'text-warning' }
  if (['MD', 'MARKDOWN'].includes(extension)) return { label: 'Markdown', icon: FileCode2, iconClass: 'text-primary' }
  if (['TXT', 'TEXT'].includes(extension)) return { label: '文本', icon: FileText, iconClass: 'text-secondary' }
  return { label: '文件', icon: File, iconClass: 'text-secondary' }
}

function resolveExtension(fileName?: string | null, fileType?: string | null): string {
  const normalizedName = fileName?.trim()
  const fileExtension = normalizedName?.includes('.') ? normalizedName.slice(normalizedName.lastIndexOf('.') + 1) : ''
  return (fileExtension || fileType || '').trim().replace(/^\./, '').toLocaleUpperCase()
}

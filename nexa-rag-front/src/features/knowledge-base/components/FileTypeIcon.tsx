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
  sm: 'size-8 rounded-lg',
  md: 'size-9 rounded-[10px]',
  lg: 'size-10 rounded-xl',
}

/** 根据文件后缀展示对应的 SVG 文件图标。 */
export function FileTypeIcon({ fileName, fileType, size = 'sm', className }: FileTypeIconProps) {
  const style = resolveFileStyle(fileName, fileType)
  const Icon = style.icon
  return <span title={style.label} aria-label={`${style.label} 文件`} className={cn('inline-flex shrink-0 items-center justify-center', SIZE_CLASS[size], style.surfaceClass, className)}><Icon aria-hidden="true" className={cn(style.iconClass, size === 'sm' ? 'size-4' : 'size-[18px]')} strokeWidth={1.9} /></span>
}

function resolveFileStyle(fileName?: string | null, fileType?: string | null): { label: string; icon: LucideIcon; surfaceClass: string; iconClass: string } {
  const extension = resolveExtension(fileName, fileType)
  if (extension === 'PDF') return { label: 'PDF', icon: FileText, surfaceClass: 'bg-[#fff0ef]', iconClass: 'text-[#d66362]' }
  if (['DOC', 'DOCX'].includes(extension)) return { label: 'Word', icon: FileText, surfaceClass: 'bg-[#edf4ff]', iconClass: 'text-[#3d73c9]' }
  if (['XLS', 'XLSX', 'CSV'].includes(extension)) return { label: 'Excel', icon: FileSpreadsheet, surfaceClass: 'bg-[#ecf8f0]', iconClass: 'text-[#2f9362]' }
  if (['PPT', 'PPTX'].includes(extension)) return { label: 'PowerPoint', icon: Presentation, surfaceClass: 'bg-[#fff2eb]', iconClass: 'text-[#d97745]' }
  if (['MD', 'MARKDOWN'].includes(extension)) return { label: 'Markdown', icon: FileCode2, surfaceClass: 'bg-[#f1efff]', iconClass: 'text-[#7066bd]' }
  if (['TXT', 'TEXT'].includes(extension)) return { label: '文本', icon: FileText, surfaceClass: 'bg-[#f2f4f7]', iconClass: 'text-[#6f7c8f]' }
  return { label: '文件', icon: File, surfaceClass: 'bg-[#f2f4f7]', iconClass: 'text-[#6f7c8f]' }
}

function resolveExtension(fileName?: string | null, fileType?: string | null): string {
  const normalizedName = fileName?.trim()
  const fileExtension = normalizedName?.includes('.') ? normalizedName.slice(normalizedName.lastIndexOf('.') + 1) : ''
  return (fileExtension || fileType || '').trim().replace(/^\./, '').toLocaleUpperCase()
}

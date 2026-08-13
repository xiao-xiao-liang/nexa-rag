import { useRef, useState, type ChangeEvent, type DragEvent, type KeyboardEvent } from 'react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { formatFileSize, getFileExtension, getFileTypeLabel } from '../file-upload'
import { FileTypeIcon } from './FileTypeIcon'

interface FileDropzoneProps {
  file: File | null
  disabled: boolean
  error: string | null
  onFileChange: (file: File) => void
  onRemove: () => void
}

const ACCEPTED_FILE_TYPES = '.pdf,.doc,.docx,.xls,.xlsx,.csv,.ppt,.pptx,.md,.markdown,.txt'

/** 知识库单文件选择、拖拽与文件卡片组件（飞书风格紧凑型）。 */
export function FileDropzone({ file, disabled, error, onFileChange, onRemove }: FileDropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)

  const openFilePicker = () => {
    if (!disabled) inputRef.current?.click()
  }

  const handleInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const selectedFile = event.target.files?.[0]
    if (selectedFile) onFileChange(selectedFile)
    event.target.value = ''
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (!disabled && (event.key === 'Enter' || event.key === ' ')) {
      event.preventDefault()
      openFilePicker()
    }
  }

  const handleDragEnter = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault()
    if (!disabled) setDragging(true)
  }

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault()
    setDragging(false)
    const selectedFile = event.dataTransfer.files?.[0]
    if (!disabled && selectedFile) onFileChange(selectedFile)
  }

  return (
    <div className="grid gap-2">
      <input
        ref={inputRef}
        aria-label="选择本地文件"
        type="file"
        accept={ACCEPTED_FILE_TYPES}
        className="sr-only"
        disabled={disabled}
        onChange={handleInputChange}
      />

      {!file ? (
        <div
          role="button"
          tabIndex={disabled ? -1 : 0}
          aria-label="选择要上传的知识库文件"
          aria-disabled={disabled}
          data-dragging={dragging || undefined}
          className={cn(
            'flex min-h-[76px] cursor-pointer items-center justify-center rounded-md border border-dashed border-input bg-muted/40 px-5 py-3 text-center outline-none transition-colors hover:border-primary hover:bg-primary-light/40 focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-ring/30',
            dragging && 'border-primary bg-primary-light/60',
            disabled && 'cursor-not-allowed opacity-60'
          )}
          onClick={openFilePicker}
          onKeyDown={handleKeyDown}
          onDragEnter={handleDragEnter}
          onDragOver={(event) => event.preventDefault()}
          onDragLeave={() => setDragging(false)}
          onDrop={handleDrop}
        >
          <div className="text-center">
            <p className="text-xs font-medium text-secondary">
              拖拽文件到此处，或 <span className="text-primary">点击选择</span>
            </p>
            <p className="mt-1 text-[11px] text-tertiary">
              支持 PDF、Word、Excel/CSV、PPT、Markdown、TXT（最大 100MB）
            </p>
          </div>
        </div>
      ) : (
        <div className="flex items-center gap-3 rounded-md border border-border bg-card px-3 py-2.5">
          <span className={cn('flex size-9 shrink-0 items-center justify-center rounded-md', resolveFileBoxClass(file.name))}>
            <FileTypeIcon fileName={file.name} size="md" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-xs font-medium text-foreground">{file.name}</p>
            <p className="mt-0.5 text-[11px] text-tertiary">
              {getFileTypeLabel(file.name)} · {formatFileSize(file.size)}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-1.5">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={disabled}
              onClick={openFilePicker}
              className="h-7 rounded-md px-2.5 text-xs"
            >
              更换文件
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label={`移除 ${file.name}`}
              disabled={disabled}
              onClick={onRemove}
              className="size-7 rounded-md text-tertiary hover:bg-danger-light hover:text-danger"
            >
              <X className="size-3.5" />
            </Button>
          </div>
        </div>
      )}

      {error && (
        <p role="alert" className="rounded-md border border-danger-light bg-danger-light px-3 py-2 text-xs font-medium text-danger">
          {error}
        </p>
      )}
    </div>
  )
}

/** 根据文件类型选择与图标颜色匹配的浅色底。 */
function resolveFileBoxClass(fileName: string): string {
  const extension = getFileExtension(fileName)
  if (extension === 'pdf' || extension === 'doc' || extension === 'docx') return 'bg-danger-light'
  if (extension === 'xls' || extension === 'xlsx' || extension === 'csv') return 'bg-success-light'
  if (extension === 'ppt' || extension === 'pptx') return 'bg-warning-light'
  if (extension === 'md' || extension === 'markdown' || extension === 'txt') return 'bg-primary-light'
  return 'bg-muted'
}

import { useRef, useState, type ChangeEvent, type DragEvent, type KeyboardEvent } from 'react'
import { FileSpreadsheet, FileText, FileType2, Presentation, UploadCloud, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { formatFileSize, getFileExtension, getFileTypeLabel } from '../file-upload'

interface FileDropzoneProps {
  file: File | null
  disabled: boolean
  error: string | null
  onFileChange: (file: File) => void
  onRemove: () => void
}

const ACCEPTED_FILE_TYPES = '.pdf,.doc,.docx,.xls,.xlsx,.csv,.ppt,.pptx,.md,.markdown,.txt'

/** 知识库单文件选择、拖拽与文件卡片组件（清晰大字号型）。 */
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
            'group flex min-h-[96px] cursor-pointer items-center justify-center rounded-2xl border-2 border-dashed border-slate-200 bg-slate-50/60 px-5 py-3.5 text-center outline-none transition-all duration-200 hover:border-indigo-400 hover:bg-indigo-50/30 focus-visible:border-indigo-500 focus-visible:ring-2 focus-visible:ring-indigo-500/20',
            dragging && 'border-indigo-500 bg-indigo-50/60 shadow-md shadow-indigo-100',
            disabled && 'cursor-not-allowed opacity-60'
          )}
          onClick={openFilePicker}
          onKeyDown={handleKeyDown}
          onDragEnter={handleDragEnter}
          onDragOver={(event) => event.preventDefault()}
          onDragLeave={() => setDragging(false)}
          onDrop={handleDrop}
        >
          <div className="flex items-center gap-3.5">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-indigo-100/80 text-indigo-600 shadow-sm transition-transform duration-200 group-hover:scale-105">
              <UploadCloud className="size-5" />
            </span>
            <div className="text-left">
              <p className="text-sm font-bold text-slate-800">
                拖拽文档文件到这里，或 <span className="text-indigo-600 underline underline-offset-2">点击选择</span>
              </p>
              <p className="text-xs text-slate-500 mt-0.5">
                支持 PDF、Word、Excel/CSV、PPT、Markdown、TXT (最大 100MB)
              </p>
            </div>
          </div>
        </div>
      ) : (
        <div className="flex items-center gap-3.5 rounded-2xl border border-indigo-100 bg-gradient-to-r from-indigo-50/60 to-slate-50 px-4 py-3 shadow-sm">
          <FileTypeIcon fileName={file.name} />
          <div className="min-w-0 flex-1">
            <p className="truncate font-bold text-sm text-slate-800">{file.name}</p>
            <p className="mt-0.5 text-xs font-medium text-slate-500">
              {getFileTypeLabel(file.name)} · {formatFileSize(file.size)}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-1.5">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              disabled={disabled}
              onClick={openFilePicker}
              className="h-8 rounded-xl text-xs font-semibold text-indigo-600 hover:bg-indigo-100/60"
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
              className="size-8 rounded-xl text-slate-400 hover:bg-rose-50 hover:text-rose-600"
            >
              <X className="size-4" />
            </Button>
          </div>
        </div>
      )}

      {error && (
        <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-3.5 py-2 text-xs font-medium text-rose-700">
          {error}
        </p>
      )}
    </div>
  )
}

/** 根据文件类型渲染可辨识的卡片图标。 */
function FileTypeIcon({ fileName }: { fileName: string }) {
  const extension = getFileExtension(fileName)
  const Icon =
    extension === 'xls' || extension === 'xlsx' || extension === 'csv'
      ? FileSpreadsheet
      : extension === 'ppt' || extension === 'pptx'
        ? Presentation
        : extension === 'doc' || extension === 'docx'
          ? FileType2
          : FileText

  return (
    <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-white text-indigo-600 shadow-sm border border-slate-100">
      <Icon className="size-4.5" />
    </span>
  )
}

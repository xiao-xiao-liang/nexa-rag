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

/** 知识库单文件选择、拖拽与文件卡片组件。 */
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
    <div className="grid gap-3">
      <input ref={inputRef} aria-label="选择要上传的知识库文件" type="file" accept={ACCEPTED_FILE_TYPES} className="sr-only" disabled={disabled} onChange={handleInputChange} />
      {!file ? <div role="button" tabIndex={disabled ? -1 : 0} aria-label="选择要上传的知识库文件" aria-disabled={disabled} data-dragging={dragging || undefined} className={cn('grid min-h-48 place-items-center rounded-2xl border-2 border-dashed border-slate-200 bg-slate-50 px-6 py-8 text-center outline-none transition-colors focus-visible:border-blue-500 focus-visible:ring-2 focus-visible:ring-blue-200', dragging && 'border-blue-500 bg-blue-50', disabled && 'cursor-not-allowed opacity-60')} onClick={openFilePicker} onKeyDown={handleKeyDown} onDragEnter={handleDragEnter} onDragOver={(event) => event.preventDefault()} onDragLeave={() => setDragging(false)} onDrop={handleDrop}>
        <div className="grid justify-items-center gap-2"><span className="flex size-11 items-center justify-center rounded-xl bg-blue-100 text-blue-600"><UploadCloud className="size-5" /></span><p className="font-medium text-slate-800">拖拽文件到这里</p><p className="text-sm text-slate-500">或点击选择文件</p><p className="pt-2 text-xs text-slate-400">支持 PDF、Word、Excel/CSV、PPT、Markdown、TXT，最大 100MB</p></div>
      </div> : <div className="flex items-center gap-3 rounded-2xl border border-blue-100 bg-blue-50/50 p-4"><FileTypeIcon fileName={file.name} /><div className="min-w-0 flex-1"><p className="truncate font-medium text-slate-800">{file.name}</p><p className="mt-1 text-sm text-slate-500">{getFileTypeLabel(file.name)} · {formatFileSize(file.size)}</p></div><div className="flex shrink-0 items-center gap-1"><Button type="button" variant="ghost" size="sm" disabled={disabled} onClick={openFilePicker}>更换文件</Button><Button type="button" variant="ghost" size="icon" aria-label={`移除 ${file.name}`} disabled={disabled} onClick={onRemove}><X className="size-4" /></Button></div></div>}
      {error && <p role="alert" className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
    </div>
  )
}

/** 根据文件类型渲染可辨识的卡片图标。 */
function FileTypeIcon({ fileName }: { fileName: string }) {
  const extension = getFileExtension(fileName)
  const Icon = extension === 'xls' || extension === 'xlsx' || extension === 'csv' ? FileSpreadsheet
    : extension === 'ppt' || extension === 'pptx' ? Presentation
      : extension === 'doc' || extension === 'docx' ? FileType2 : FileText
  return <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-white text-blue-600"><Icon className="size-5" /></span>
}

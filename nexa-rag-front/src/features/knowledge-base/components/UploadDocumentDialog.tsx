import { useState, type FormEvent } from 'react'
import { AlignLeft, FileCode, SlidersHorizontal, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { uploadDocument, type SplitStrategy, type UploadDocumentInput } from '../api/document-api'
import { deriveDocumentTitle, validateUploadFile } from '../file-upload'
import { FileDropzone } from './FileDropzone'

interface UploadDocumentDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onUploaded: (documentId: number) => void
}

/** 上传知识库文档的单文件表单，大屏宽与精炼切分设置。 */
export function UploadDocumentDialog({ open, onOpenChange, onUploaded }: UploadDocumentDialogProps) {
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [descriptionExpanded, setDescriptionExpanded] = useState(false)
  const [splitStrategy, setSplitStrategy] = useState<SplitStrategy>('CHARACTER')
  const [chunkSize, setChunkSize] = useState<number>(500)
  const [chunkOverlap, setChunkOverlap] = useState<number>(50)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const resetForm = () => {
    setFile(null)
    setTitle('')
    setDescription('')
    setDescriptionExpanded(false)
    setSplitStrategy('CHARACTER')
    setChunkSize(500)
    setChunkOverlap(50)
    setError(null)
  }

  const handleOpenChange = (nextOpen: boolean) => {
    if (submitting && !nextOpen) return
    if (!nextOpen) resetForm()
    onOpenChange(nextOpen)
  }

  const handleFileChange = (nextFile: File) => {
    const validationError = validateUploadFile(nextFile)
    if (validationError) {
      setFile(null)
      setTitle('')
      setError(validationError)
      return
    }

    setFile(nextFile)
    setTitle(deriveDocumentTitle(nextFile.name))

    if (nextFile.name.endsWith('.md') || nextFile.name.endsWith('.markdown')) {
      setSplitStrategy('MARKDOWN')
    } else {
      setSplitStrategy('CHARACTER')
    }
    setError(null)
  }

  const handleFileRemove = () => {
    setFile(null)
    setTitle('')
    setError(null)
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!file || submitting) return

    if (chunkOverlap >= chunkSize) {
      setError('片段重叠大小必须小于片段大小')
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      const payload: UploadDocumentInput = {
        file,
        title,
        description,
        splitConfig: {
          splitStrategy,
          chunkSize,
          chunkOverlap,
        },
      }

      const response = await uploadDocument(payload)
      onUploaded(response.documentId)
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : '上传失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-3xl overflow-hidden rounded-2xl p-0 shadow-2xl border-none">
        {/* Header */}
        <DialogHeader className="bg-gradient-to-r from-slate-50 via-slate-50 to-indigo-50/40 px-6 py-4 border-b border-slate-100">
          <div className="flex items-center gap-1.5 text-indigo-600 font-semibold text-xs mb-1">
            <Sparkles className="size-4" />
            <span>RAG 知识库导入工作台</span>
          </div>
          <DialogTitle className="text-lg font-bold tracking-tight text-slate-900">上传文档</DialogTitle>
          <DialogDescription className="text-xs text-slate-500">
            配置文档的解析切分策略与切块大小，向量化索引完成后将服务于 AI 检索增强问答。
          </DialogDescription>
        </DialogHeader>

        <form className="p-6 space-y-4" noValidate onSubmit={handleSubmit}>
          {/* 1. 文件拖拽上传区域 */}
          <FileDropzone
            file={file}
            disabled={submitting}
            error={error}
            onFileChange={handleFileChange}
            onRemove={handleFileRemove}
          />

          {/* 2 & 3. 左右双列并行布局 (Grid 2 Columns 宽敞型) */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {/* 左列：文档元数据设置 */}
            <section className="flex flex-col justify-between space-y-3 rounded-2xl border border-slate-200/80 bg-white p-4 shadow-sm">
              <div className="space-y-3">
                <span className="block text-sm font-bold text-slate-800">文档基础元信息</span>

                <label className="grid gap-1.5 text-xs font-semibold text-slate-700">
                  文档标题
                  <Input
                    aria-label="文档标题"
                    maxLength={256}
                    disabled={submitting}
                    placeholder={file ? '请输入文档标题' : '选择文件后自动提取标题'}
                    value={title}
                    onChange={(event) => setTitle(event.target.value)}
                    className="h-9 rounded-xl border-slate-200 text-xs text-slate-800 shadow-none focus-visible:ring-indigo-500/20"
                  />
                </label>

                {descriptionExpanded ? (
                  <label className="grid gap-1.5 text-xs font-semibold text-slate-700">
                    文档描述（可选）
                    <Textarea
                      aria-label="文档描述"
                      maxLength={1024}
                      disabled={submitting}
                      placeholder="可输入补充说明…"
                      value={description}
                      onChange={(event) => setDescription(event.target.value)}
                      className="min-h-[76px] resize-none rounded-xl border-slate-200 text-xs text-slate-800 focus-visible:ring-indigo-500/20"
                    />
                  </label>
                ) : (
                  <Button
                    className="h-auto p-0 text-xs text-slate-500 hover:bg-transparent hover:text-indigo-600"
                    type="button"
                    variant="ghost"
                    disabled={submitting}
                    onClick={() => setDescriptionExpanded(true)}
                  >
                    添加描述（可选）
                  </Button>
                )}
              </div>
            </section>

            {/* 右列：RAG 文本切分与处理参数设置 */}
            <section className="space-y-3 rounded-2xl border border-slate-200/80 bg-slate-50/60 p-4 shadow-sm">
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-1.5 text-xs font-bold text-slate-800">
                  <SlidersHorizontal className="size-4 text-indigo-600" />
                  RAG 切分参数设置
                </span>
                <span className="text-[11px] text-slate-400">常驻调优</span>
              </div>

              {/* 切分策略两列按纽 */}
              <div className="grid grid-cols-2 gap-2.5">
                <button
                  type="button"
                  disabled={submitting}
                  onClick={() => setSplitStrategy('CHARACTER')}
                  className={`flex flex-col justify-center rounded-xl border px-3 py-2 text-left transition-all ${
                    splitStrategy === 'CHARACTER'
                      ? 'border-indigo-500 bg-white ring-2 ring-indigo-500/20 shadow-sm'
                      : 'border-slate-200 bg-white/70 hover:border-slate-300'
                  }`}
                >
                  <span className="flex items-center gap-1.5">
                    <AlignLeft className={`size-3.5 ${splitStrategy === 'CHARACTER' ? 'text-indigo-600' : 'text-slate-400'}`} />
                    <b className={`text-xs ${splitStrategy === 'CHARACTER' ? 'text-indigo-900 font-bold' : 'text-slate-700'}`}>
                      智能段落
                    </b>
                  </span>
                  <span className="text-[10px] text-slate-400 truncate mt-0.5">普通文本/PDF/Word</span>
                </button>

                <button
                  type="button"
                  disabled={submitting}
                  onClick={() => setSplitStrategy('MARKDOWN')}
                  className={`flex flex-col justify-center rounded-xl border px-3 py-2 text-left transition-all ${
                    splitStrategy === 'MARKDOWN'
                      ? 'border-indigo-500 bg-white ring-2 ring-indigo-500/20 shadow-sm'
                      : 'border-slate-200 bg-white/70 hover:border-slate-300'
                  }`}
                >
                  <span className="flex items-center gap-1.5">
                    <FileCode className={`size-3.5 ${splitStrategy === 'MARKDOWN' ? 'text-indigo-600' : 'text-slate-400'}`} />
                    <b className={`text-xs ${splitStrategy === 'MARKDOWN' ? 'text-indigo-900 font-bold' : 'text-slate-700'}`}>
                      Markdown 标题
                    </b>
                  </span>
                  <span className="text-[10px] text-slate-400 truncate mt-0.5">按 # 层级切分</span>
                </button>
              </div>

              {/* 切分粒度调节器 */}
              <div className="grid grid-cols-2 gap-3 pt-1">
                <div className="space-y-1.5">
                  <label className="flex items-center justify-between text-xs font-semibold text-slate-700">
                    <span>Chunk Size</span>
                    <span className="font-mono text-indigo-600 font-bold">{chunkSize} 字符</span>
                  </label>
                  <Input
                    type="number"
                    min={50}
                    max={20000}
                    disabled={submitting}
                    value={chunkSize}
                    onChange={(e) => setChunkSize(Number(e.target.value) || 500)}
                    className="h-9 rounded-xl border-slate-200 bg-white text-xs font-mono px-3"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="flex items-center justify-between text-xs font-semibold text-slate-700">
                    <span>Chunk Overlap</span>
                    <span className="font-mono text-indigo-600 font-bold">{chunkOverlap} 字符</span>
                  </label>
                  <Input
                    type="number"
                    min={0}
                    max={5000}
                    disabled={submitting}
                    value={chunkOverlap}
                    onChange={(e) => setChunkOverlap(Number(e.target.value) || 0)}
                    className="h-9 rounded-xl border-slate-200 bg-white text-xs font-mono px-3"
                  />
                </div>
              </div>
            </section>
          </div>

          {/* Dialog Footer Actions */}
          <div className="flex items-center justify-end gap-2.5 border-t border-slate-100 pt-3">
            <Button
              type="button"
              variant="outline"
              onClick={() => handleOpenChange(false)}
              disabled={submitting}
              className="h-9 rounded-xl border-slate-200 text-xs font-semibold text-slate-600"
            >
              取消
            </Button>
            <Button
              type="submit"
              disabled={!file || submitting}
              className="h-9 rounded-xl bg-gradient-to-r from-indigo-600 to-violet-600 px-5 text-xs font-semibold text-white shadow-md shadow-indigo-200 transition-all hover:from-indigo-500 hover:to-violet-500 disabled:opacity-50"
            >
              {submitting ? '正在提交并创建处理任务' : '开始上传'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}

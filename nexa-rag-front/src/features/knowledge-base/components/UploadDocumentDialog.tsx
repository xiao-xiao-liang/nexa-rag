import { useState, type FormEvent } from 'react'
import { AlignLeft, BookOpen, FileCode, Globe, SlidersHorizontal, Sparkles, UploadCloud } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import {
  submitExternalDocument,
  uploadDocument,
  type ExternalDocumentSourceType,
  type SplitStrategy,
  type UploadDocumentInput,
} from '../api/document-api'
import { deriveDocumentTitle, validateUploadFile } from '../file-upload'
import { FileDropzone } from './FileDropzone'

interface UploadDocumentDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onUploaded: (documentId: number | string) => void
}

/** 上传知识库文档的表单，支持本地文件、飞书和语雀多来源导入与 Markdown 层级切分。 */
export function UploadDocumentDialog({ open, onOpenChange, onUploaded }: UploadDocumentDialogProps) {
  const [sourceType, setSourceType] = useState<ExternalDocumentSourceType>('LOCAL')
  const [sourceUrl, setSourceUrl] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [descriptionExpanded, setDescriptionExpanded] = useState(false)
  const [splitStrategy, setSplitStrategy] = useState<SplitStrategy>('PARENT_MARKDOWN')
  const [chunkSize, setChunkSize] = useState<number>(500)
  const [chunkOverlap, setChunkOverlap] = useState<number>(50)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const resetForm = () => {
    setSourceType('LOCAL')
    setSourceUrl('')
    setFile(null)
    setTitle('')
    setDescription('')
    setDescriptionExpanded(false)
    setSplitStrategy('PARENT_MARKDOWN')
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
      setSplitStrategy('PARENT_MARKDOWN')
    } else {
      setSplitStrategy('PARENT_MARKDOWN')
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
    if (submitting) return

    if (chunkOverlap >= chunkSize) {
      setError('片段重叠大小必须小于片段大小')
      return
    }

    if (sourceType === 'LOCAL') {
      if (!file) {
        setError('请选择需要上传的本地文件')
        return
      }
    } else {
      if (!sourceUrl.trim()) {
        setError(sourceType === 'FEISHU' ? '请输入飞书文档 URL' : '请输入语雀文档 URL')
        return
      }
    }

    setSubmitting(true)
    setError(null)
    try {
      if (sourceType === 'LOCAL') {
        const payload: UploadDocumentInput = {
          file: file!,
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
      } else {
        const response = await submitExternalDocument({
          sourceType,
          sourceUrl: sourceUrl.trim(),
          title: title.trim() || undefined,
          description: description.trim() || undefined,
          splitConfig: {
            splitStrategy,
            chunkSize,
            chunkOverlap,
          },
        })
        onUploaded(response.documentId)
      }
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : '提交失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  const isSubmitDisabled = submitting || (sourceType === 'LOCAL' ? !file : !sourceUrl.trim())

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-3xl overflow-hidden rounded-2xl p-0 shadow-2xl border-none">
        {/* Header */}
        <DialogHeader className="bg-gradient-to-r from-slate-50 via-slate-50 to-indigo-50/40 px-6 py-4 border-b border-slate-100">
          <div className="flex items-center gap-1.5 text-indigo-600 font-semibold text-xs mb-1">
            <Sparkles className="size-4" />
            <span>RAG 知识库导入工作台</span>
          </div>
          <DialogTitle className="text-lg font-bold tracking-tight text-slate-900">导入文档</DialogTitle>
          <DialogDescription className="text-xs text-slate-500">
            支持本地文件、飞书及语雀在线文档，默认采用 Markdown 层级切分策略。
          </DialogDescription>
        </DialogHeader>

        <form className="p-6 space-y-4" noValidate onSubmit={handleSubmit}>
          {/* 1. 文档来源选择器 */}
          <div className="space-y-1.5">
            <label className="text-xs font-bold text-slate-800">文档来源</label>
            <div className="grid grid-cols-3 gap-2.5">
              <button
                type="button"
                disabled={submitting}
                onClick={() => {
                  setSourceType('LOCAL')
                  setError(null)
                }}
                className={`flex items-center justify-center gap-2 rounded-xl border px-3 py-2 text-xs font-semibold transition-all ${
                  sourceType === 'LOCAL'
                    ? 'border-indigo-500 bg-indigo-50/50 text-indigo-900 ring-2 ring-indigo-500/20 shadow-sm'
                    : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50'
                }`}
              >
                <UploadCloud className={`size-4 ${sourceType === 'LOCAL' ? 'text-indigo-600' : 'text-slate-400'}`} />
                <span>本地文件</span>
              </button>
              <button
                type="button"
                disabled={submitting}
                onClick={() => {
                  setSourceType('FEISHU')
                  setError(null)
                }}
                className={`flex items-center justify-center gap-2 rounded-xl border px-3 py-2 text-xs font-semibold transition-all ${
                  sourceType === 'FEISHU'
                    ? 'border-indigo-500 bg-indigo-50/50 text-indigo-900 ring-2 ring-indigo-500/20 shadow-sm'
                    : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50'
                }`}
              >
                <Globe className={`size-4 ${sourceType === 'FEISHU' ? 'text-indigo-600' : 'text-slate-400'}`} />
                <span>飞书文档</span>
              </button>
              <button
                type="button"
                disabled={submitting}
                onClick={() => {
                  setSourceType('YUQUE')
                  setError(null)
                }}
                className={`flex items-center justify-center gap-2 rounded-xl border px-3 py-2 text-xs font-semibold transition-all ${
                  sourceType === 'YUQUE'
                    ? 'border-indigo-500 bg-indigo-50/50 text-indigo-900 ring-2 ring-indigo-500/20 shadow-sm'
                    : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50'
                }`}
              >
                <BookOpen className={`size-4 ${sourceType === 'YUQUE' ? 'text-indigo-600' : 'text-slate-400'}`} />
                <span>语雀文档</span>
              </button>
            </div>
          </div>

          {/* 2. 本地文件拖拽区域 或 在线文档 URL 输入 */}
          {sourceType === 'LOCAL' ? (
            <FileDropzone
              file={file}
              disabled={submitting}
              error={error}
              onFileChange={handleFileChange}
              onRemove={handleFileRemove}
            />
          ) : (
            <div className="space-y-1.5 rounded-2xl border border-slate-200/80 bg-white p-4 shadow-sm">
              <label className="block text-xs font-bold text-slate-800">
                {sourceType === 'FEISHU' ? '飞书文档 URL' : '语雀文档 URL'}
                <span className="text-red-500 ml-1">*</span>
              </label>
              <Input
                aria-label={sourceType === 'FEISHU' ? '飞书文档 URL' : '语雀文档 URL'}
                maxLength={1024}
                disabled={submitting}
                placeholder={
                  sourceType === 'FEISHU'
                    ? '请输入飞书 Docx 或 Wiki 节点链接，如 https://xxx.feishu.cn/docx/...'
                    : '请输入语雀单篇文档链接，如 https://www.yuque.com/org/repo/doc-slug'
                }
                value={sourceUrl}
                onChange={(e) => setSourceUrl(e.target.value)}
                className="h-9 rounded-xl border-slate-200 text-xs text-slate-800 focus-visible:ring-indigo-500/20"
              />
              {error && (
                <p role="alert" className="text-xs text-red-500 mt-1">{error}</p>
              )}
            </div>
          )}

          {/* 3. 左右双列并行布局 */}
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
                    placeholder={
                      sourceType === 'LOCAL'
                        ? file ? '请输入文档标题' : '选择文件后自动提取标题'
                        : '可选，不填将自动提取在线文档标题'
                    }
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

              {/* 切分策略两列按钮 */}
              <div className="grid grid-cols-2 gap-2.5">
                <button
                  type="button"
                  disabled={submitting}
                  onClick={() => setSplitStrategy('PARENT_MARKDOWN')}
                  className={`flex flex-col justify-center rounded-xl border px-3 py-2 text-left transition-all ${
                    splitStrategy === 'PARENT_MARKDOWN'
                      ? 'border-indigo-500 bg-white ring-2 ring-indigo-500/20 shadow-sm'
                      : 'border-slate-200 bg-white/70 hover:border-slate-300'
                  }`}
                >
                  <span className="flex items-center gap-1.5">
                    <FileCode className={`size-3.5 ${splitStrategy === 'PARENT_MARKDOWN' ? 'text-indigo-600' : 'text-slate-400'}`} />
                    <b className={`text-xs ${splitStrategy === 'PARENT_MARKDOWN' ? 'text-indigo-900 font-bold' : 'text-slate-700'}`}>
                      Markdown 标题
                    </b>
                  </span>
                  <span className="text-[10px] text-slate-400 truncate mt-0.5">按 # 层级切分（默认）</span>
                </button>

                <button
                  type="button"
                  disabled={submitting}
                  onClick={() => setSplitStrategy('REGEX_TEXT')}
                  className={`flex flex-col justify-center rounded-xl border px-3 py-2 text-left transition-all ${
                    splitStrategy === 'REGEX_TEXT'
                      ? 'border-indigo-500 bg-white ring-2 ring-indigo-500/20 shadow-sm'
                      : 'border-slate-200 bg-white/70 hover:border-slate-300'
                  }`}
                >
                  <span className="flex items-center gap-1.5">
                    <AlignLeft className={`size-3.5 ${splitStrategy === 'REGEX_TEXT' ? 'text-indigo-600' : 'text-slate-400'}`} />
                    <b className={`text-xs ${splitStrategy === 'REGEX_TEXT' ? 'text-indigo-900 font-bold' : 'text-slate-700'}`}>
                      智能段落
                    </b>
                  </span>
                  <span className="text-[10px] text-slate-400 truncate mt-0.5">普通文本/PDF/Word</span>
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
              disabled={isSubmitDisabled}
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

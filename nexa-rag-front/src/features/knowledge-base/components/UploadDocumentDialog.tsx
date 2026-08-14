import { useEffect, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import { submitExternalDocument, uploadDocument, type ExternalDocumentSourceType } from '../api/document-api'
import { deriveDocumentTitle, validateUploadFile } from '../file-upload'
import { FileDropzone } from './FileDropzone'

interface UploadDocumentDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onUploaded: (documentId: number | string) => void
  initialDescription?: string
}

const SOURCE_OPTIONS: { value: ExternalDocumentSourceType; label: string }[] = [
  { value: 'LOCAL', label: '本地文件' },
  { value: 'FEISHU', label: '飞书文档' },
  { value: 'YUQUE', label: '语雀文档' },
]

/** 上传知识库文档的表单：用户只负责来源、上传与基础信息，处理配置由后端按文档类型自动兜底。 */
export function UploadDocumentDialog({ open, onOpenChange, onUploaded, initialDescription }: UploadDocumentDialogProps) {
  const [sourceType, setSourceType] = useState<ExternalDocumentSourceType>('LOCAL')
  const [sourceUrl, setSourceUrl] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [fileError, setFileError] = useState<string | null>(null)
  const [urlError, setUrlError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)

  // 外部（如 AI 创建入口）传入的初始描述在弹窗打开时写入表单。
  useEffect(() => {
    if (open && initialDescription) setDescription(initialDescription)
  }, [open, initialDescription])

  const resetForm = () => {
    setSourceType('LOCAL')
    setSourceUrl('')
    setFile(null)
    setTitle('')
    setDescription('')
    setFileError(null)
    setUrlError(null)
    setSubmitError(null)
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
      setFileError(validationError)
      return
    }

    setFile(nextFile)
    setTitle(deriveDocumentTitle(nextFile.name))
    setFileError(null)
  }

  const handleFileRemove = () => {
    setFile(null)
    setTitle('')
    setFileError(null)
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitting) return
    setSubmitError(null)

    if (sourceType === 'LOCAL') {
      if (!file) {
        setFileError('请选择需要上传的本地文件')
        return
      }
    } else if (!sourceUrl.trim()) {
      setUrlError(sourceType === 'FEISHU' ? '请输入飞书文档 URL' : '请输入语雀文档 URL')
      return
    }

    setFileError(null)
    setUrlError(null)
    setSubmitting(true)
    try {
      if (sourceType === 'LOCAL') {
        const response = await uploadDocument({ file: file!, title, description })
        onUploaded(response.documentId)
      } else {
        const response = await submitExternalDocument({
          sourceType,
          sourceUrl: sourceUrl.trim(),
          title: title.trim() || undefined,
          description: description.trim() || undefined,
        })
        onUploaded(response.documentId)
      }
    } catch (uploadError) {
      setSubmitError(uploadError instanceof Error ? uploadError.message : '提交失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  const isSubmitDisabled = submitting || (sourceType === 'LOCAL' ? !file : !sourceUrl.trim())

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="flex max-h-[calc(100dvh-4rem)] max-w-2xl flex-col overflow-hidden rounded-md bg-card p-0">
        {/* 弹窗头部 */}
        <DialogHeader className="border-b border-border px-5 py-4">
          <DialogTitle className="text-base font-semibold text-foreground">导入文档</DialogTitle>
          <DialogDescription className="mt-0.5 text-xs text-secondary">
            支持本地文件、飞书 / 语雀在线文档，提交后自动创建文档处理任务。
          </DialogDescription>
        </DialogHeader>

        <form id="upload-document-form" className="min-h-0 flex-1 space-y-4 overflow-y-auto px-5 py-4" noValidate onSubmit={handleSubmit}>
          {/* 1. 文档来源（分段 Tab） */}
          <div>
            <span className="mb-1.5 block text-xs font-semibold text-secondary">文档来源</span>
            <div className="flex w-[264px] gap-0.5 rounded bg-muted p-0.5">
              {SOURCE_OPTIONS.map((option) => {
                const isActive = sourceType === option.value
                return (
                  <button
                    key={option.value}
                    type="button"
                    disabled={submitting}
                    onClick={() => {
                      setSourceType(option.value)
                      setFileError(null)
                      setUrlError(null)
                    }}
                    className={cn(
                      'h-6 flex-1 rounded-[3px] text-center text-xs transition-colors',
                      isActive
                        ? 'border border-border bg-card font-medium text-primary'
                        : 'text-secondary hover:text-foreground'
                    )}
                  >
                    {option.label}
                  </button>
                )
              })}
            </div>
          </div>

          {/* 2. 上传 / 链接输入 */}
          <div>
            <span className="mb-1.5 block text-xs font-semibold text-secondary">
              {sourceType === 'LOCAL' ? '上传内容' : sourceType === 'FEISHU' ? '飞书文档链接' : '语雀文档链接'}
            </span>
            {sourceType === 'LOCAL' ? (
              <FileDropzone
                file={file}
                disabled={submitting}
                error={fileError}
                onFileChange={handleFileChange}
                onRemove={handleFileRemove}
              />
            ) : (
              <div className="space-y-1.5">
                <Input
                  aria-label={sourceType === 'FEISHU' ? '飞书文档 URL' : '语雀文档 URL'}
                  maxLength={1024}
                  disabled={submitting}
                  placeholder={
                    sourceType === 'FEISHU'
                      ? 'https://xxx.feishu.cn/docx/... 或 wiki/...'
                      : 'https://www.yuque.com/org/repo/doc-slug'
                  }
                  value={sourceUrl}
                  onChange={(event) => {
                    setSourceUrl(event.target.value)
                    setUrlError(null)
                  }}
                  className="h-7 rounded-md text-xs"
                />
                <p className="text-[11px] leading-relaxed text-tertiary">
                  {sourceType === 'FEISHU'
                    ? '粘贴飞书 Docx 或 Wiki 节点链接，提交后自动拉取并解析文档内容。'
                    : '粘贴语雀单篇文档链接，提交后自动拉取并解析文档内容。'}
                </p>
                {urlError && (
                  <p role="alert" className="text-xs font-medium text-danger">{urlError}</p>
                )}
              </div>
            )}
          </div>

          {/* 3. 文档信息 */}
          <div>
            <span className="mb-1.5 block text-xs font-semibold text-secondary">文档信息</span>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="grid gap-1.5">
                <span className="text-[11px] text-secondary">文档标题</span>
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
                  className="h-7 rounded-md text-xs"
                />
              </label>
              <label className="grid gap-1.5">
                <span className="text-[11px] text-secondary">文档描述（可选）</span>
                <Textarea
                  aria-label="文档描述"
                  maxLength={1024}
                  disabled={submitting}
                  placeholder="可输入补充说明…"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  className="min-h-[60px] resize-none rounded-md text-xs"
                />
              </label>
            </div>
          </div>

          {submitError && (
            <p role="alert" className="rounded-md border border-danger-light bg-danger-light px-3 py-2 text-xs font-medium text-danger">
              {submitError}
            </p>
          )}
        </form>

        {/* 弹窗底部 */}
        <div className="flex items-center justify-between border-t border-border px-5 py-3">
          <span className="text-[11px] text-tertiary">提交后将自动进入解析 → 切分 → 索引流水线。</span>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => handleOpenChange(false)}
              disabled={submitting}
              className="h-7 rounded-md text-xs"
            >
              取消
            </Button>
            <Button
              type="submit"
              form="upload-document-form"
              disabled={isSubmitDisabled}
              className="h-7 rounded-md bg-primary px-4 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {submitting ? '正在提交并创建处理任务' : '开始上传'}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { uploadDocument } from '../api/document-api'

interface UploadDocumentDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onUploaded: (documentId: number) => void
}

/** 上传知识库文档的轻量表单，仅提交文件、标题和描述。 */
export function UploadDocumentDialog({ open, onOpenChange, onUploaded }: UploadDocumentDialogProps) {
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleOpenChange = (nextOpen: boolean) => {
    // 1. 关闭表单时清理本次临时输入，下一次打开从空白状态开始。
    if (!nextOpen) {
      setFile(null)
      setTitle('')
      setDescription('')
      setError(null)
    }
    // 2. 将受控开关状态交回页面，保持弹窗与路由切换一致。
    onOpenChange(nextOpen)
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!file || submitting) {
      return
    }

    // 1. 锁定重复提交并清空旧错误，避免重复创建处理任务。
    setSubmitting(true)
    setError(null)
    try {
      // 2. 调用上传接口，后端使用默认处理配置。
      const response = await uploadDocument({ file, title, description })
      // 3. 成功后由父页面跳转详情页，便于观察处理状态。
      onUploaded(response.documentId)
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : '上传失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>上传文档</DialogTitle>
          <DialogDescription>上传后将按后端默认配置完成解析、切分与索引。</DialogDescription>
        </DialogHeader>
        <form className="grid gap-4" noValidate onSubmit={handleSubmit}>
          <label className="grid gap-2 text-sm font-medium">
            文件
            <input aria-label="选择文件" type="file" required onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
          </label>
          <label className="grid gap-2 text-sm font-medium">
            文档标题
            <Input aria-label="文档标题" maxLength={256} value={title} onChange={(event) => setTitle(event.target.value)} />
          </label>
          <label className="grid gap-2 text-sm font-medium">
            文档描述
            <Textarea aria-label="文档描述" maxLength={1024} value={description} onChange={(event) => setDescription(event.target.value)} />
          </label>
          {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)} disabled={submitting}>取消</Button>
            <Button type="submit" disabled={!file || submitting}>{submitting ? '上传中…' : '开始上传'}</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}

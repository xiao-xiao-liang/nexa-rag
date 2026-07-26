import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { uploadDocument } from '../api/document-api'
import { deriveDocumentTitle, validateUploadFile } from '../file-upload'
import { FileDropzone } from './FileDropzone'

interface UploadDocumentDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onUploaded: (documentId: number) => void
}

/** 上传知识库文档的单文件表单，负责文件校验、元数据补充和提交状态管理。 */
export function UploadDocumentDialog({ open, onOpenChange, onUploaded }: UploadDocumentDialogProps) {
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [descriptionExpanded, setDescriptionExpanded] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const resetForm = () => {
    // 1. 清理本次临时输入，下一次打开从空白状态开始。
    setFile(null)
    setTitle('')
    setDescription('')
    setDescriptionExpanded(false)
    setError(null)
  }

  const handleOpenChange = (nextOpen: boolean) => {
    // 1. 提交期间阻止关闭，避免用户误以为请求已取消。
    if (submitting && !nextOpen) return
    // 2. 正常关闭时清理临时输入，再将受控状态交回页面。
    if (!nextOpen) resetForm()
    onOpenChange(nextOpen)
  }

  const handleFileChange = (nextFile: File) => {
    // 1. 在发起请求前完成格式和大小校验，缩短无效操作的反馈路径。
    const validationError = validateUploadFile(nextFile)
    if (validationError) {
      setFile(null)
      setTitle('')
      setError(validationError)
      return
    }
    // 2. 使用文件名生成可编辑标题，并清理旧错误。
    setFile(nextFile)
    setTitle(deriveDocumentTitle(nextFile.name))
    setError(null)
  }

  const handleFileRemove = () => {
    // 1. 移除文件时同步清理派生标题和本次错误。
    setFile(null)
    setTitle('')
    setError(null)
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!file || submitting) return

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
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>上传文档</DialogTitle>
          <DialogDescription>上传后将按后端默认配置完成解析、切分与索引。</DialogDescription>
        </DialogHeader>
        <form className="grid gap-5" noValidate onSubmit={handleSubmit}>
          <FileDropzone file={file} disabled={submitting} error={error} onFileChange={handleFileChange} onRemove={handleFileRemove} />
          {file && <label className="grid gap-2 text-sm font-medium">
            文档标题
            <Input aria-label="文档标题" maxLength={256} disabled={submitting} value={title} onChange={(event) => setTitle(event.target.value)} />
          </label>}
          {file && (descriptionExpanded ? <label className="grid gap-2 text-sm font-medium">
            文档描述（可选）
            <Textarea aria-label="文档描述" maxLength={1024} disabled={submitting} value={description} onChange={(event) => setDescription(event.target.value)} />
          </label> : <Button className="w-fit px-0 text-muted-foreground hover:bg-transparent hover:text-foreground" type="button" variant="ghost" disabled={submitting} onClick={() => setDescriptionExpanded(true)}>添加描述（可选）</Button>)}
          <div className="flex justify-end gap-2 border-t pt-4">
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)} disabled={submitting}>取消</Button>
            <Button type="submit" disabled={!file || submitting}>{submitting ? '正在提交并创建处理任务' : '开始上传'}</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}

import { useState, type FormEvent } from 'react'
import { BookOpen, ChevronDown, FileCode2, FileSpreadsheet, Globe, ListTree, SlidersHorizontal, TextQuote, UploadCloud } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { CustomSelect, type SelectOption } from '@/components/ui/select'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import {
  submitExternalDocument,
  uploadDocument,
  type ExcelSplitOptionsInput,
  type ExternalDocumentSourceType,
  type IndexConfigInput,
  type MarkdownSplitOptionsInput,
  type ParseConfigInput,
  type RegexSplitOptionsInput,
  type SplitConfigInput,
  type SplitStrategy,
  type UploadDocumentInput,
} from '../api/document-api'
import { deriveDocumentTitle, getFileExtension, validateUploadFile } from '../file-upload'
import { FileDropzone } from './FileDropzone'

interface UploadDocumentDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onUploaded: (documentId: number | string) => void
}

const EXCEL_MODE_OPTIONS: SelectOption[] = [
  { value: 'KEY_VALUE', label: '键值对' },
  { value: 'HTML_TABLE', label: 'HTML 表格' },
]

const SOURCE_OPTIONS: { value: ExternalDocumentSourceType; label: string; icon: typeof UploadCloud }[] = [
  { value: 'LOCAL', label: '本地文件', icon: UploadCloud },
  { value: 'FEISHU', label: '飞书文档', icon: Globe },
  { value: 'YUQUE', label: '语雀文档', icon: BookOpen },
]

const STRATEGY_OPTIONS: { value: SplitStrategy; label: string; description: string; icon: typeof FileCode2 }[] = [
  { value: 'PARENT_MARKDOWN', label: '父子 Markdown', description: '按标题层级切分，保留章节上下文（默认）', icon: FileCode2 },
  { value: 'BROTHER_MARKDOWN', label: '同级 Markdown', description: '同级标题各自独立成块', icon: ListTree },
  { value: 'REGEX_TEXT', label: '正则文本', description: '按分隔符 / 正则切分普通文本', icon: TextQuote },
  { value: 'EXCEL', label: '表格', description: '按行切分 Excel / CSV', icon: FileSpreadsheet },
]

const MARKDOWN_DEFAULTS: MarkdownSplitOptionsInput = { titleLevel: 3, stripHeaders: false, preserveCodeBlock: true, createParentForOversized: true }

/** 上传知识库文档的表单：本地文件 / 飞书 / 语雀多来源，含切分、解析与索引配置。 */
export function UploadDocumentDialog({ open, onOpenChange, onUploaded }: UploadDocumentDialogProps) {
  const [sourceType, setSourceType] = useState<ExternalDocumentSourceType>('LOCAL')
  const [sourceUrl, setSourceUrl] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [splitStrategy, setSplitStrategy] = useState<SplitStrategy>('PARENT_MARKDOWN')
  const [chunkSize, setChunkSize] = useState<number>(500)
  const [chunkOverlap, setChunkOverlap] = useState<number>(50)
  const [markdownOptions, setMarkdownOptions] = useState<MarkdownSplitOptionsInput>(MARKDOWN_DEFAULTS)
  const [regexOptions, setRegexOptions] = useState({ separator: '', regex: '', keepSeparator: false })
  const [excelOptions, setExcelOptions] = useState({ mode: 'KEY_VALUE' as 'KEY_VALUE' | 'HTML_TABLE', firstRowAsHeader: true, charset: '', maxRowsPerChunk: '' })
  const [parseConfig, setParseConfig] = useState<ParseConfigInput>({ enableOcr: false, enableImageDescription: false })
  const [indexConfig, setIndexConfig] = useState<IndexConfigInput>({ enabled: true, vectorEnabled: true, keywordEnabled: true })
  const [advancedExpanded, setAdvancedExpanded] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [fileError, setFileError] = useState<string | null>(null)
  const [urlError, setUrlError] = useState<string | null>(null)
  const [paramError, setParamError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const resetForm = () => {
    setSourceType('LOCAL')
    setSourceUrl('')
    setFile(null)
    setTitle('')
    setDescription('')
    setSplitStrategy('PARENT_MARKDOWN')
    setChunkSize(500)
    setChunkOverlap(50)
    setMarkdownOptions(MARKDOWN_DEFAULTS)
    setRegexOptions({ separator: '', regex: '', keepSeparator: false })
    setExcelOptions({ mode: 'KEY_VALUE', firstRowAsHeader: true, charset: '', maxRowsPerChunk: '' })
    setParseConfig({ enableOcr: false, enableImageDescription: false })
    setIndexConfig({ enabled: true, vectorEnabled: true, keywordEnabled: true })
    setAdvancedExpanded(true)
    setFileError(null)
    setUrlError(null)
    setParamError(null)
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
    // 按文件类型给出与后端默认一致的切分策略与 OCR 解析默认值。
    const extension = getFileExtension(nextFile.name)
    if (['xls', 'xlsx', 'csv'].includes(extension)) {
      setSplitStrategy('EXCEL')
    } else if (['ppt', 'pptx', 'txt'].includes(extension)) {
      setSplitStrategy('REGEX_TEXT')
    } else {
      setSplitStrategy('PARENT_MARKDOWN')
    }
    setParseConfig({ enableOcr: ['pdf', 'doc', 'docx'].includes(extension), enableImageDescription: false })
    setFileError(null)
  }

  const handleFileRemove = () => {
    setFile(null)
    setTitle('')
    setFileError(null)
  }

  const buildSplitConfig = (): SplitConfigInput => {
    const base: SplitConfigInput = { splitStrategy, chunkSize, chunkOverlap }
    if (splitStrategy === 'PARENT_MARKDOWN' || splitStrategy === 'BROTHER_MARKDOWN') {
      base.markdown = { ...markdownOptions }
    } else if (splitStrategy === 'REGEX_TEXT') {
      const regex: RegexSplitOptionsInput = { keepSeparator: regexOptions.keepSeparator }
      if (regexOptions.separator.trim()) regex.separator = regexOptions.separator.trim()
      if (regexOptions.regex.trim()) regex.regex = regexOptions.regex.trim()
      base.regex = regex
    } else {
      const excel: ExcelSplitOptionsInput = { mode: excelOptions.mode, firstRowAsHeader: excelOptions.firstRowAsHeader }
      if (excelOptions.charset.trim()) excel.charset = excelOptions.charset.trim()
      const rows = Number(excelOptions.maxRowsPerChunk)
      if (excelOptions.maxRowsPerChunk.trim() && rows > 0) excel.maxRowsPerChunk = rows
      base.excel = excel
    }
    return base
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitting) return
    setSubmitError(null)

    if (chunkOverlap >= chunkSize) {
      setParamError('片段重叠大小必须小于片段大小')
      return
    }

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
    setParamError(null)
    setSubmitting(true)
    try {
      const splitConfig = buildSplitConfig()
      if (sourceType === 'LOCAL') {
        const payload: UploadDocumentInput = {
          file: file!,
          title,
          description,
          splitConfig,
          parseConfig,
          indexConfig,
        }
        const response = await uploadDocument(payload)
        onUploaded(response.documentId)
      } else {
        const response = await submitExternalDocument({
          sourceType,
          sourceUrl: sourceUrl.trim(),
          title: title.trim() || undefined,
          description: description.trim() || undefined,
          splitConfig,
          parseConfig,
          indexConfig,
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
      <DialogContent className="flex max-h-[calc(100dvh-4rem)] max-w-3xl flex-col overflow-hidden rounded-lg bg-card p-0">
        {/* 弹窗头部 */}
        <DialogHeader className="border-b border-border px-6 py-4">
          <DialogTitle className="text-base font-semibold text-foreground">导入文档</DialogTitle>
          <DialogDescription className="text-xs text-secondary">
            支持本地文件、飞书 / 语雀在线文档，提交后自动创建文档处理任务。
          </DialogDescription>
        </DialogHeader>

        <form id="upload-document-form" className="min-h-0 flex-1 space-y-4 overflow-y-auto px-6 py-4" noValidate onSubmit={handleSubmit}>
          {/* 1. 文档来源（分段控件） */}
          <div>
            <span className="mb-2 block text-xs font-medium text-secondary">文档来源</span>
            <div className="flex w-fit gap-1 rounded-md bg-muted p-1">
              {SOURCE_OPTIONS.map((option) => {
                const isActive = sourceType === option.value
                const Icon = option.icon
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
                      'flex h-7 items-center gap-1.5 rounded-sm px-3 text-xs transition-colors',
                      isActive
                        ? 'border border-border bg-card font-medium text-primary'
                        : 'text-secondary hover:text-foreground'
                    )}
                  >
                    <Icon className="size-3.5" />
                    {option.label}
                  </button>
                )
              })}
            </div>
          </div>

          {/* 2. 上传 / 链接输入 + 文档信息 */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-[minmax(0,1fr)_260px]">
            <div className="min-w-0">
              <span className="mb-2 block text-xs font-medium text-secondary">
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
                    className="h-8 rounded-md text-xs"
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

            {/* 文档信息 */}
            <div className="min-w-0">
              <span className="mb-2 block text-xs font-medium text-secondary">文档信息</span>
              <div className="space-y-3">
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
                    className="h-8 rounded-md text-xs"
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
                    className="min-h-[88px] resize-none rounded-md text-xs"
                  />
                </label>
              </div>
            </div>
          </div>

          {/* 3. 切分策略 */}
          <div className="border-t border-border pt-4">
            <span className="mb-2 block text-xs font-medium text-secondary">切分策略</span>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              {STRATEGY_OPTIONS.map((option) => {
                const isActive = splitStrategy === option.value
                const Icon = option.icon
                return (
                  <button
                    key={option.value}
                    type="button"
                    disabled={submitting}
                    onClick={() => {
                      setSplitStrategy(option.value)
                      setParamError(null)
                    }}
                    className={cn(
                      'flex min-h-[64px] flex-col items-start justify-center rounded-md border p-2.5 text-left transition-colors',
                      isActive
                        ? 'border-primary bg-primary-light text-primary'
                        : 'border-border bg-card text-foreground hover:border-primary/60'
                    )}
                  >
                    <span className="flex items-center gap-1.5 text-xs font-medium">
                      <Icon className="size-3.5" />
                      {option.label}
                    </span>
                    <span className={cn('mt-0.5 text-[11px] leading-snug', isActive ? 'text-primary' : 'text-tertiary')}>
                      {option.description}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>

          {/* 4. 切分参数 + 策略专属参数 */}
          <div className="border-t border-border pt-4">
            <span className="mb-2 block text-xs font-medium text-secondary">切分参数</span>
            <div className="flex flex-wrap items-start gap-4">
              <label className="grid gap-1.5">
                <span className="text-[11px] text-secondary">片段大小（字符）</span>
                  <Input
                    type="number"
                    aria-label="片段大小"
                    min={1}
                    max={20000}
                    disabled={submitting}
                    value={chunkSize}
                    onChange={(event) => {
                      setChunkSize(Number(event.target.value) || 0)
                      setParamError(null)
                    }}
                  className="h-8 w-32 rounded-md text-right font-mono text-xs"
                />
                <span className="text-[11px] text-tertiary">范围 1–20000</span>
              </label>
              <label className="grid gap-1.5">
                <span className="text-[11px] text-secondary">重叠大小（字符）</span>
                  <Input
                    type="number"
                    aria-label="重叠大小"
                    min={0}
                    max={5000}
                    disabled={submitting}
                    value={chunkOverlap}
                    onChange={(event) => {
                      setChunkOverlap(Math.max(0, Number(event.target.value) || 0))
                      setParamError(null)
                    }}
                  className="h-8 w-32 rounded-md text-right font-mono text-xs"
                />
                <span className="text-[11px] text-tertiary">范围 0–5000，需小于片段大小</span>
              </label>
            </div>

            {paramError && (
              <p role="alert" className="mt-2 text-xs font-medium text-danger">{paramError}</p>
            )}

            {/* 策略专属参数 */}
            <div className="mt-3 rounded-md border border-border bg-muted/50 p-3">
              {(splitStrategy === 'PARENT_MARKDOWN' || splitStrategy === 'BROTHER_MARKDOWN') && (
                <MarkdownOptionsPanel
                  options={markdownOptions}
                  disabled={submitting}
                  onChange={setMarkdownOptions}
                />
              )}
              {splitStrategy === 'REGEX_TEXT' && (
                <RegexOptionsPanel
                  options={regexOptions}
                  disabled={submitting}
                  onChange={setRegexOptions}
                />
              )}
              {splitStrategy === 'EXCEL' && (
                <ExcelOptionsPanel
                  options={excelOptions}
                  disabled={submitting}
                  onChange={setExcelOptions}
                />
              )}
            </div>
          </div>

          {/* 5. 更多处理设置 */}
          <div className="border-t border-border pt-4">
            <button
              type="button"
              disabled={submitting}
              onClick={() => setAdvancedExpanded((value) => !value)}
              className="flex w-full items-center justify-between text-xs font-medium text-secondary transition-colors hover:text-foreground"
            >
              <span className="flex items-center gap-1.5">
                <SlidersHorizontal className="size-3.5" />
                更多处理设置
              </span>
              <ChevronDown className={cn('size-4 text-tertiary transition-transform', advancedExpanded && 'rotate-180')} />
            </button>

            {advancedExpanded && (
              <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
                {/* 解析配置 */}
                <div className="rounded-md border border-border bg-card p-3">
                  <div className="mb-2.5 text-[11px] font-medium text-secondary">解析配置</div>
                  <div className="flex flex-col gap-2.5">
                    <Switch
                      checked={Boolean(parseConfig.enableOcr)}
                      disabled={submitting}
                      label="启用 OCR"
                      hint="扫描件 / 图片型 PDF 的文字识别"
                      onChange={(checked) => setParseConfig((current) => ({ ...current, enableOcr: checked }))}
                    />
                    <Switch
                      checked={Boolean(parseConfig.enableImageDescription)}
                      disabled={submitting}
                      label="生成图片描述"
                      hint="为文档内图片生成文字描述"
                      onChange={(checked) => setParseConfig((current) => ({ ...current, enableImageDescription: checked }))}
                    />
                  </div>
                </div>

                {/* 索引配置 */}
                <div className="rounded-md border border-border bg-card p-3">
                  <div className="mb-2.5 text-[11px] font-medium text-secondary">索引配置</div>
                  <div className="flex flex-col gap-2.5">
                    <Switch
                      checked={Boolean(indexConfig.vectorEnabled)}
                      disabled={submitting}
                      label="向量索引"
                      hint="写入语义检索索引，用于相似度召回"
                      onChange={(checked) => setIndexConfig((current) => ({ ...current, vectorEnabled: checked }))}
                    />
                    <Switch
                      checked={Boolean(indexConfig.keywordEnabled)}
                      disabled={submitting}
                      label="关键词索引"
                      hint="写入关键词索引，用于精确匹配召回"
                      onChange={(checked) => setIndexConfig((current) => ({ ...current, keywordEnabled: checked }))}
                    />
                  </div>
                </div>
              </div>
            )}
          </div>

          {submitError && (
            <p role="alert" className="rounded-md border border-danger-light bg-danger-light px-3 py-2 text-xs font-medium text-danger">
              {submitError}
            </p>
          )}
        </form>

        {/* 弹窗底部 */}
        <div className="flex items-center justify-between border-t border-border px-6 py-3.5">
          <span className="text-[11px] text-tertiary">提交后将自动进入解析 → 切分 → 索引流水线。</span>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => handleOpenChange(false)}
              disabled={submitting}
              className="h-8 rounded-md text-xs"
            >
              取消
            </Button>
            <Button
              type="submit"
              form="upload-document-form"
              disabled={isSubmitDisabled}
              className="h-8 rounded-md bg-primary px-4 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {submitting ? '正在提交并创建处理任务' : '开始上传'}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

/** Markdown 策略专属参数面板。 */
function MarkdownOptionsPanel({
  options,
  disabled,
  onChange,
}: {
  options: MarkdownSplitOptionsInput
  disabled: boolean
  onChange: (options: MarkdownSplitOptionsInput) => void
}) {
  return (
    <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
      <label className="grid gap-1">
        <span className="text-[11px] text-secondary">最大标题层级</span>
        <Input
          type="number"
          aria-label="最大标题层级"
          min={1}
          max={6}
          disabled={disabled}
          value={options.titleLevel ?? 3}
          onChange={(event) => onChange({ ...options, titleLevel: Math.min(6, Math.max(1, Number(event.target.value) || 3)) })}
          className="h-7 w-20 rounded-md text-center font-mono text-xs"
        />
      </label>
      <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
        <Switch
          checked={Boolean(options.stripHeaders)}
          disabled={disabled}
          label="从正文移除标题行"
          onChange={(checked) => onChange({ ...options, stripHeaders: checked })}
        />
        <Switch
          checked={Boolean(options.preserveCodeBlock)}
          disabled={disabled}
          label="保护代码块"
          onChange={(checked) => onChange({ ...options, preserveCodeBlock: checked })}
        />
        <Switch
          checked={Boolean(options.createParentForOversized)}
          disabled={disabled}
          label="超长片段创建父片段"
          onChange={(checked) => onChange({ ...options, createParentForOversized: checked })}
        />
      </div>
    </div>
  )
}

/** 正则文本策略专属参数面板。 */
function RegexOptionsPanel({
  options,
  disabled,
  onChange,
}: {
  options: { separator: string; regex: string; keepSeparator: boolean }
  disabled: boolean
  onChange: (options: { separator: string; regex: string; keepSeparator: boolean }) => void
}) {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
      <label className="grid gap-1">
        <span className="text-[11px] text-secondary">分隔符</span>
        <Input
          aria-label="分隔符"
          maxLength={128}
          disabled={disabled}
          placeholder="留空使用默认（空行）"
          value={options.separator}
          onChange={(event) => onChange({ ...options, separator: event.target.value })}
          className="h-7 w-44 rounded-md text-xs"
        />
      </label>
      <label className="grid gap-1">
        <span className="text-[11px] text-secondary">正则表达式（可选）</span>
        <Input
          aria-label="正则表达式"
          maxLength={256}
          disabled={disabled}
          placeholder="如：\n{2,}"
          value={options.regex}
          onChange={(event) => onChange({ ...options, regex: event.target.value })}
          className="h-7 w-52 rounded-md font-mono text-xs"
        />
      </label>
      <Switch
        checked={options.keepSeparator}
        disabled={disabled}
        label="保留分隔符"
        onChange={(checked) => onChange({ ...options, keepSeparator: checked })}
      />
    </div>
  )
}

/** 表格策略专属参数面板。 */
function ExcelOptionsPanel({
  options,
  disabled,
  onChange,
}: {
  options: { mode: 'KEY_VALUE' | 'HTML_TABLE'; firstRowAsHeader: boolean; charset: string; maxRowsPerChunk: string }
  disabled: boolean
  onChange: (options: { mode: 'KEY_VALUE' | 'HTML_TABLE'; firstRowAsHeader: boolean; charset: string; maxRowsPerChunk: string }) => void
}) {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
      <label className="grid gap-1">
        <span className="text-[11px] text-secondary">渲染模式</span>
        <CustomSelect
          value={options.mode}
          onChange={(value) => onChange({ ...options, mode: value as 'KEY_VALUE' | 'HTML_TABLE' })}
          options={EXCEL_MODE_OPTIONS}
          disabled={disabled}
          triggerClassName="h-7 rounded-md px-2.5 text-xs"
          className="w-32"
        />
      </label>
      <label className="grid gap-1">
        <span className="text-[11px] text-secondary">每个片段最大行数（可选）</span>
        <Input
          type="number"
          aria-label="每个片段最大行数"
          min={1}
          max={10000}
          disabled={disabled}
          placeholder="不限制"
          value={options.maxRowsPerChunk}
          onChange={(event) => onChange({ ...options, maxRowsPerChunk: event.target.value })}
          className="h-7 w-28 rounded-md text-right font-mono text-xs"
        />
      </label>
      <label className="grid gap-1">
        <span className="text-[11px] text-secondary">字符集（可选）</span>
        <Input
          aria-label="字符集"
          maxLength={32}
          disabled={disabled}
          placeholder="自动识别"
          value={options.charset}
          onChange={(event) => onChange({ ...options, charset: event.target.value })}
          className="h-7 w-32 rounded-md text-xs"
        />
      </label>
      <Switch
        checked={options.firstRowAsHeader}
        disabled={disabled}
        label="首行作为表头"
        onChange={(checked) => onChange({ ...options, firstRowAsHeader: checked })}
      />
    </div>
  )
}

/** 飞书风格开关。 */
function Switch({
  checked,
  disabled,
  label,
  hint,
  onChange,
}: {
  checked: boolean
  disabled: boolean
  label: string
  hint?: string
  onChange: (checked: boolean) => void
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className="group flex items-center gap-2 text-left disabled:cursor-not-allowed disabled:opacity-50"
    >
      <span
        className={cn(
          'relative h-4 w-8 shrink-0 rounded-full transition-colors',
          checked ? 'bg-primary' : 'border border-input bg-muted'
        )}
      >
        <span
          className={cn(
            'absolute top-1/2 size-3 -translate-y-1/2 rounded-full bg-white shadow-sm transition-all',
            checked ? 'left-[18px]' : 'left-0.5'
          )}
        />
      </span>
      <span className="min-w-0">
        <span className="block text-xs text-secondary group-hover:text-foreground">{label}</span>
        {hint && <span className="block text-[11px] text-tertiary">{hint}</span>}
      </span>
    </button>
  )
}

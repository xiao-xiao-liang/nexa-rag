import { useEffect, useMemo, useRef, useState } from 'react'
import {
  AlertCircle,
  ArrowLeftRight,
  CheckCircle2,
  Code2,
  Copy,
  Edit3,
  Eye,
  GitBranch,
  History,
  Layers,
  Loader2,
  Moon,
  RotateCcw,
  Search,
  Send,
  Settings2,
  SlidersHorizontal,
  Sparkles,
  Sun,
  Terminal,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { CustomSelect, type SelectOption } from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import {
  getPrompt,
  getPrompts,
  previewPrompt,
  releasePrompt,
  rollbackPrompt,
  submitPrompt,
  updatePrompt,
  type PromptItem,
  type PromptReleaseItem,
  type PromptVersionItem,
} from '../api/prompt-api'

export default function PromptManagementPage() {
  const [prompts, setPrompts] = useState<PromptItem[]>([])
  const [selectedCode, setSelectedCode] = useState<string>('')
  const [currentPrompt, setCurrentPrompt] = useState<PromptItem | null>(null)
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')

  // 编辑器主题模式: 'light' | 'dark'
  const [editorTheme, setEditorTheme] = useState<'light' | 'dark'>('light')

  // 右侧 Inspector 面板选中的 Tab: 'release' | 'history'
  const [inspectorTab, setInspectorTab] = useState<'release' | 'history'>('release')

  // 编辑器正文状态
  const [editorContent, setEditorContent] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  // Line Number Textarea 同步 Scroll 引用
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const lineNumbersRef = useRef<HTMLDivElement>(null)

  // Modals & Dialogs
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewing, setPreviewing] = useState(false)
  const [previewResult, setPreviewResult] = useState<string | null>(null)

  const [releaseModalOpen, setReleaseModalOpen] = useState(false)
  const [releasing, setReleasing] = useState(false)
  const [selectedStableVersionId, setSelectedStableVersionId] = useState<number | string>('')
  const [enableCanary, setEnableCanary] = useState(false)
  const [selectedCanaryVersionId, setSelectedCanaryVersionId] = useState<number | string>('')
  const [canaryPercentage, setCanaryPercentage] = useState<number>(20)

  const [rollbackModalOpen, setRollbackModalOpen] = useState(false)
  const [rollbackTargetVersion, setRollbackTargetVersion] = useState<PromptVersionItem | null>(null)
  const [rollingBack, setRollingBack] = useState(false)

  const [diffModalOpen, setDiffModalOpen] = useState(false)
  const [diffVersion, setDiffVersion] = useState<PromptVersionItem | null>(null)

  // 定义基础信息编辑 Dialog 状态
  const [editDefinitionModalOpen, setEditDefinitionModalOpen] = useState(false)
  const [editPromptName, setEditPromptName] = useState('')
  const [editVariableSchema, setEditVariableSchema] = useState('')
  const [updatingDefinition, setUpdatingDefinition] = useState(false)

  const showToast = (msg: string) => {
    setToastMessage(msg)
    setTimeout(() => setToastMessage(null), 3000)
  }

  // 同步行号滚动位置
  const handleScrollEditor = () => {
    if (textareaRef.current && lineNumbersRef.current) {
      lineNumbersRef.current.scrollTop = textareaRef.current.scrollTop
    }
  }

  // 加载 Prompt 列表
  const loadPrompts = async (autoSelectCode?: string) => {
    setLoading(true)
    setError(null)
    try {
      const list = await getPrompts()
      setPrompts(list || [])
      const targetCode = autoSelectCode || (list.length > 0 ? list[0].promptCode : '')
      if (targetCode) {
        setSelectedCode(targetCode)
        await loadPromptDetail(targetCode)
      }
    } catch (err: any) {
      console.error('加载 Prompt 列表失败:', err)
      setError(err?.message || '无法连接至后端 Prompt 管理服务')
    } finally {
      setLoading(false)
    }
  }

  // 加载 Prompt 详情
  const loadPromptDetail = async (code: string) => {
    if (!code) return
    setDetailLoading(true)
    try {
      const detail = await getPrompt(code)
      setCurrentPrompt(detail)
      // 设置编辑器文本为最新 release/version 正文
      const latestVersion = detail.versions && detail.versions.length > 0 ? detail.versions[0] : null
      setEditorContent(latestVersion?.content || '')
    } catch (err: any) {
      console.error(`加载 Prompt [${code}] 详情失败:`, err)
      showToast(`加载 Prompt 详情失败: ${err?.message || '未知错误'}`)
    } finally {
      setDetailLoading(false)
    }
  }

  useEffect(() => {
    loadPrompts()
  }, [])

  const handleSelectPrompt = (code: string) => {
    if (code === selectedCode) return
    setSelectedCode(code)
    loadPromptDetail(code)
  }

  // 过滤提示词列表
  const filteredPrompts = useMemo(() => {
    if (!search.trim()) return prompts
    const kw = search.trim().toLowerCase()
    return prompts.filter(
      (p) => p.name.toLowerCase().includes(kw) || p.promptCode.toLowerCase().includes(kw),
    )
  }, [prompts, search])

  // 计算行数
  const lineCount = useMemo(() => {
    if (!editorContent) return 1
    return editorContent.split('\n').length
  }, [editorContent])

  // 自动从当前编辑正文提取 {{var}} 模板变量
  const detectedVariables = useMemo(() => {
    if (!editorContent) return []
    const regex = /\{\{\s*([a-zA-Z0-9_]+)\s*\}\}/g
    const vars = new Set<string>()
    let match: RegExpExecArray | null
    while ((match = regex.exec(editorContent)) !== null) {
      if (match[1]) {
        vars.add(match[1])
      }
    }
    return Array.from(vars)
  }, [editorContent])

  // 解析后端 Schema 要求必填的变量
  const requiredSchemaVariables = useMemo(() => {
    if (!currentPrompt?.variableSchema) return []
    try {
      const parsed = JSON.parse(currentPrompt.variableSchema)
      return parsed.required || []
    } catch {
      return []
    }
  }, [currentPrompt])

  // 当前正文是否有未提交变更
  const isDirty = useMemo(() => {
    if (!currentPrompt?.versions || currentPrompt.versions.length === 0) return false
    const activeVersionContent = currentPrompt.versions[0].content
    return editorContent !== activeVersionContent
  }, [editorContent, currentPrompt])

  // 复制提示词编码
  const handleCopyCode = (code: string) => {
    navigator.clipboard.writeText(code)
    showToast(`已复制 Prompt 唯一编码: [${code}]`)
  }

  // 在编辑器光标处插入变量
  const handleInsertVariable = (varName: string) => {
    const varTag = `{{${varName}}}`
    setEditorContent((prev) => prev + varTag)
    showToast(`已插入变量 ${varTag}`)
  }

  // 切换 Enabled 状态
  const handleToggleEnabled = async () => {
    if (!currentPrompt || !selectedCode || updatingDefinition) return
    const nextState = !currentPrompt.enabled
    setUpdatingDefinition(true)
    try {
      await updatePrompt(selectedCode, { enabled: nextState })
      showToast(`已${nextState ? '启用' : '禁用'} Prompt [${currentPrompt.name}]`)
      await loadPromptDetail(selectedCode)
      await loadPrompts(selectedCode)
    } catch (err: any) {
      showToast(`更新启用状态失败: ${err?.message || '服务器拒绝'}`)
    } finally {
      setUpdatingDefinition(false)
    }
  }

  // 打开编辑 Prompt 定义信息 Dialog
  const handleOpenEditDefinition = () => {
    if (!currentPrompt) return
    setEditPromptName(currentPrompt.name || '')
    setEditVariableSchema(currentPrompt.variableSchema || '{"required":[]}')
    setEditDefinitionModalOpen(true)
  }

  // 保存基础定义（名称、变量契约 JSON）
  const handleSaveDefinition = async () => {
    if (!selectedCode || !editPromptName.trim() || updatingDefinition) return
    setUpdatingDefinition(true)
    try {
      // 校验 JSON 格式
      if (editVariableSchema.trim()) {
        JSON.parse(editVariableSchema.trim())
      }
      await updatePrompt(selectedCode, {
        name: editPromptName.trim(),
        variableSchema: editVariableSchema.trim() || undefined,
      })
      showToast(`提示词基础定义与契约修改成功！`)
      setEditDefinitionModalOpen(false)
      await loadPromptDetail(selectedCode)
      await loadPrompts(selectedCode)
    } catch (err: any) {
      showToast(`保存失败: ${err?.message || 'JSON 格式不合法或服务器校验未通过'}`)
    } finally {
      setUpdatingDefinition(false)
    }
  }

  // 触发脱敏预览
  const handleOpenPreview = async () => {
    if (!selectedCode || !editorContent.trim()) return
    setPreviewOpen(true)
    setPreviewing(true)
    setPreviewResult(null)
    try {
      const res = await previewPrompt(selectedCode, editorContent)
      setPreviewResult(res.content || '')
    } catch (err: any) {
      setPreviewResult(`[预览失败]: ${err?.message || '模板渲染服务无响应'}`)
    } finally {
      setPreviewing(false)
    }
  }

  // 提交并立即发布
  const handleSubmitPromptContent = async () => {
    if (!selectedCode || !editorContent.trim() || isSubmitting) return
    setIsSubmitting(true)
    try {
      const res = await submitPrompt(selectedCode, editorContent)
      showToast(`成功提交并发布新版本 (Rev: ${res.releaseRevision}, Version ID: ${res.versionId})`)
      await loadPromptDetail(selectedCode)
      await loadPrompts(selectedCode)
    } catch (err: any) {
      showToast(`提交失败: ${err?.message || '发布拒绝'}`)
    } finally {
      setIsSubmitting(false)
    }
  }

  // 打开发布配置弹窗
  const handleOpenReleaseModal = () => {
    if (!currentPrompt) return
    const currentRelease = currentPrompt.releases && currentPrompt.releases.length > 0 ? currentPrompt.releases[0] : null
    const latestVersion = currentPrompt.versions && currentPrompt.versions.length > 0 ? currentPrompt.versions[0] : null

    setSelectedStableVersionId(currentRelease?.stableVersionId || latestVersion?.versionId || '')
    setSelectedCanaryVersionId(currentRelease?.canaryVersionId || '')
    setEnableCanary(!!currentRelease?.canaryVersionId)

    // 解析灰度百分比
    let pct = 20
    if (currentRelease?.canaryRule) {
      try {
        const ruleObj = typeof currentRelease.canaryRule === 'string' ? JSON.parse(currentRelease.canaryRule) : currentRelease.canaryRule
        if (ruleObj?.percentage) pct = ruleObj.percentage
      } catch {
        pct = 20
      }
    }
    setCanaryPercentage(pct)
    setReleaseModalOpen(true)
  }

  // 执行正式/灰度发布
  const handleConfirmRelease = async () => {
    if (!selectedCode || !selectedStableVersionId || releasing) return
    setReleasing(true)
    try {
      const res = await releasePrompt(selectedCode, {
        stableVersionId: selectedStableVersionId,
        canaryVersionId: enableCanary && selectedCanaryVersionId ? selectedCanaryVersionId : null,
        canaryPercentage: enableCanary ? canaryPercentage : null,
      })
      showToast(`发布成功！发布代次: Rev ${res.releaseRevision}`)
      setReleaseModalOpen(false)
      await loadPromptDetail(selectedCode)
      await loadPrompts(selectedCode)
    } catch (err: any) {
      showToast(`发布失败: ${err?.message || '发布操作中断'}`)
    } finally {
      setReleasing(false)
    }
  }

  // 打开回滚确认弹窗
  const handleOpenRollback = (ver: PromptVersionItem) => {
    setRollbackTargetVersion(ver)
    setRollbackModalOpen(true)
  }

  // 执行回滚
  const handleConfirmRollback = async () => {
    if (!selectedCode || !rollbackTargetVersion || rollingBack) return
    setRollingBack(true)
    try {
      await rollbackPrompt(selectedCode, rollbackTargetVersion.versionId)
      showToast(`已成功回滚到版本 v${rollbackTargetVersion.versionNo}！`)
      setRollbackModalOpen(false)
      await loadPromptDetail(selectedCode)
      await loadPrompts(selectedCode)
    } catch (err: any) {
      showToast(`回滚失败: ${err?.message || '回滚请求拒绝'}`)
    } finally {
      setRollingBack(false)
    }
  }

  // 打开版本 Diff 对比弹窗
  const handleOpenDiff = (ver: PromptVersionItem) => {
    setDiffVersion(ver)
    setDiffModalOpen(true)
  }

  // 构建版本 Dropdown 选项
  const versionSelectOptions: SelectOption[] = useMemo(() => {
    if (!currentPrompt?.versions) return []
    return currentPrompt.versions.map((v) => ({
      value: String(v.versionId),
      label: `v${v.versionNo} (ID: ${v.versionId}) - ${v.remark || '无备注'} [${v.createdBy}]`,
    }))
  }, [currentPrompt])

  // 当前发布信息
  const activeRelease: PromptReleaseItem | null = useMemo(() => {
    if (!currentPrompt?.releases || currentPrompt.releases.length === 0) return null
    return currentPrompt.releases[0]
  }, [currentPrompt])

  const activeStableVersion: PromptVersionItem | null = useMemo(() => {
    if (!currentPrompt?.versions || !activeRelease) return null
    return currentPrompt.versions.find((v) => String(v.versionId) === String(activeRelease.stableVersionId)) || null
  }, [currentPrompt, activeRelease])

  const activeCanaryVersion: PromptVersionItem | null = useMemo(() => {
    if (!currentPrompt?.versions || !activeRelease?.canaryVersionId) return null
    return currentPrompt.versions.find((v) => String(v.versionId) === String(activeRelease.canaryVersionId)) || null
  }, [currentPrompt, activeRelease])

  if (loading) {
    return (
      <div className="flex h-full min-h-0 flex-1 items-center justify-center bg-[#f8f9fc]">
        <div className="flex flex-col items-center gap-3 text-slate-500">
          <Loader2 className="size-8 animate-spin text-[#6f62e8]" />
          <p className="text-xs font-semibold">正在从后端加载提示词定义与发布历史…</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col overflow-hidden bg-[#f6f7fb] text-slate-800">
      {/* Toast 信息指示器 */}
      {toastMessage && (
        <div className="fixed top-4 right-4 z-50 rounded-xl bg-slate-900 px-4 py-2.5 text-xs font-semibold text-white shadow-lg animate-in fade-in slide-in-from-top-2">
          {toastMessage}
        </div>
      )}

      {/* 顶部 Header: 紧凑高质感 Studio 顶栏 */}
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-[#e8ebf1] bg-white px-5">
        <div className="flex items-center gap-3">
          <span className="flex size-8 items-center justify-center rounded-lg bg-gradient-to-br from-[#6f62e8] to-[#9285f8] text-white shadow-xs">
            <Sparkles className="size-4" />
          </span>
          <div className="flex items-center gap-2">
            <h1 className="text-sm font-bold tracking-tight text-slate-900">提示词编排与版本管理 Studio</h1>
            <span className="rounded-md bg-[#eeecff] px-2 py-0.5 text-[10px] font-bold text-[#5649ce]">
              Dify Studio
            </span>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 rounded-lg bg-slate-100/80 px-2.5 py-1 text-xs text-slate-600">
            <Layers className="size-3.5 text-[#6f62e8]" />
            <span>模板数: <strong className="text-slate-900">{prompts.length}</strong></span>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => loadPrompts(selectedCode)}
            className="h-8 gap-1.5 rounded-lg border-slate-200 text-xs font-semibold text-slate-700"
          >
            <RotateCcw className="size-3.5 text-slate-500" />
            刷新
          </Button>
        </div>
      </header>

      {/* 全局 Error 提示 */}
      {error && (
        <div className="px-5 pt-3 shrink-0">
          <div className="rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-700">
            {error}
          </div>
        </div>
      )}

      {/* 主体 3 栏/固定视口 Studio 布局 (Left Sidebar, Center Workspace, Right Inspector) */}
      <div className="flex min-h-0 flex-1 overflow-hidden">

        {/* Pane 1: 左侧提示词导航面板 (270px) */}
        <aside className="flex w-[270px] shrink-0 flex-col border-r border-[#e8ebf1] bg-white">
          <div className="p-3.5 border-b border-slate-100 space-y-2">
            <div className="relative">
              <Search className="absolute left-2.5 top-2.5 size-3.5 text-slate-400" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="搜索 Prompt 编码或名称…"
                className="w-full rounded-lg border border-slate-200 bg-slate-50 py-1.5 pl-8 pr-3 text-xs text-slate-700 outline-none placeholder:text-slate-400 focus:border-[#6f62e8] focus:bg-white"
              />
            </div>
            <div className="flex items-center justify-between text-[10px] font-semibold uppercase tracking-wider text-slate-400 px-1">
              <span>提示词目录 ({filteredPrompts.length})</span>
              <Terminal className="size-3" />
            </div>
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto p-2 space-y-1">
            {filteredPrompts.map((p) => {
              const isSelected = selectedCode === p.promptCode
              return (
                <div
                  key={p.promptCode}
                  onClick={() => handleSelectPrompt(p.promptCode)}
                  className={cn(
                    'group flex cursor-pointer flex-col gap-1 rounded-xl p-3 transition-all border text-left',
                    isSelected
                      ? 'border-[#b6aeff] bg-[#f5f3ff] text-[#5649ce] shadow-2xs font-semibold'
                      : 'border-transparent hover:bg-slate-50 text-slate-700',
                  )}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs truncate font-bold">{p.name}</span>
                    <span
                      className={cn(
                        'rounded-md px-1.5 py-0.5 text-[9px] font-bold shrink-0',
                        p.enabled ? 'bg-emerald-50 text-emerald-600' : 'bg-slate-100 text-slate-400',
                      )}
                    >
                      {p.enabled ? '启用' : '禁用'}
                    </span>
                  </div>

                  <div className="flex items-center justify-between text-[10px] opacity-75">
                    <span className="font-mono truncate">{p.promptCode}</span>
                    <span className="font-medium text-[#6f62e8]">
                      Rev {p.currentReleaseRevision ?? 1}
                    </span>
                  </div>
                </div>
              )
            })}
          </div>
        </aside>

        {/* Pane 2: 中间主工作区 - Prompt 编辑器 (Flex-1 充盈视口) */}
        <section className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
          {detailLoading ? (
            <div className="flex flex-1 items-center justify-center">
              <Loader2 className="size-7 animate-spin text-[#6f62e8]" />
            </div>
          ) : currentPrompt ? (
            <>
              {/* 编辑器 Top Bar: 干净精简的操作栏 + 可编辑基础定义控制 */}
              <div className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-b border-[#e8ebf1] bg-[#fafafc] px-5 py-3">
                <div className="flex items-center gap-3 min-w-0">
                  <div className="flex items-center gap-1.5 min-w-0">
                    <h2 className="text-base font-bold text-slate-900 truncate">{currentPrompt.name}</h2>
                    <TooltipProvider>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <button
                            type="button"
                            onClick={handleOpenEditDefinition}
                            className="flex size-6 items-center justify-center rounded-md text-slate-400 hover:bg-slate-200 hover:text-[#6f62e8] transition-colors"
                          >
                            <Edit3 className="size-3.5" />
                          </button>
                        </TooltipTrigger>
                        <TooltipContent side="bottom" className="text-[11px]">编辑名称与变量契约 Schema</TooltipContent>
                      </Tooltip>
                    </TooltipProvider>
                  </div>

                  <div
                    onClick={() => handleCopyCode(currentPrompt.promptCode)}
                    className="flex cursor-pointer items-center gap-1 rounded-md bg-white border border-slate-200 px-2 py-0.5 font-mono text-xs font-semibold text-slate-700 hover:border-[#6f62e8] hover:text-[#5649ce] transition-colors shrink-0"
                    title="点击复制编码"
                  >
                    <span>{currentPrompt.promptCode}</span>
                    <Copy className="size-3" />
                  </div>

                  {/* 交互式 启用/禁用 Toggle 开关 */}
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <button
                          type="button"
                          disabled={updatingDefinition}
                          onClick={handleToggleEnabled}
                          className={cn(
                            'flex items-center gap-1 rounded-md px-2 py-0.5 text-[10px] font-bold transition-all shrink-0 cursor-pointer',
                            currentPrompt.enabled
                              ? 'bg-emerald-50 text-emerald-600 hover:bg-emerald-100'
                              : 'bg-slate-100 text-slate-500 hover:bg-slate-200',
                          )}
                        >
                          <span className={cn('size-1.5 rounded-full', currentPrompt.enabled ? 'bg-emerald-500' : 'bg-slate-400')} />
                          {currentPrompt.enabled ? '已启用 (点击禁用)' : '已禁用 (点击启用)'}
                        </button>
                      </TooltipTrigger>
                      <TooltipContent side="bottom" className="text-[11px]">切换全局 Prompt 物理启用/禁用状态</TooltipContent>
                    </Tooltip>
                  </TooltipProvider>

                  <span className="rounded-md bg-purple-50 px-2 py-0.5 text-[10px] font-bold text-[#6f62e8] shrink-0">
                    Rev {currentPrompt.currentReleaseRevision ?? 1}
                  </span>
                </div>

                {/* 精简操作按钮组: 图标化工具项 + 主次清晰的操作按钮 */}
                <div className="flex items-center gap-2 shrink-0">
                  <TooltipProvider>
                    {/* 主题切换图标按钮 */}
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <button
                          type="button"
                          onClick={() => setEditorTheme(editorTheme === 'light' ? 'dark' : 'light')}
                          className="flex size-8 items-center justify-center rounded-lg border border-slate-200 bg-white text-slate-600 transition-colors hover:bg-slate-50 hover:text-[#6f62e8]"
                        >
                          {editorTheme === 'light' ? <Sun className="size-4 text-amber-500" /> : <Moon className="size-4 text-indigo-400" />}
                        </button>
                      </TooltipTrigger>
                      <TooltipContent side="bottom" className="text-[11px]">
                        切换编辑器主题 ({editorTheme === 'light' ? '切换为暗黑模式' : '切换为明亮模式'})
                      </TooltipContent>
                    </Tooltip>

                    {/* 脱敏预览图标按钮 */}
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <button
                          type="button"
                          onClick={handleOpenPreview}
                          className="flex size-8 items-center justify-center rounded-lg border border-slate-200 bg-white text-[#6f62e8] transition-colors hover:bg-purple-50 hover:border-[#6f62e8]"
                        >
                          <Eye className="size-4" />
                        </button>
                      </TooltipTrigger>
                      <TooltipContent side="bottom" className="text-[11px]">
                        脱敏正文在线渲染预览
                      </TooltipContent>
                    </Tooltip>
                  </TooltipProvider>

                  {/* 灰度与发布 */}
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={handleOpenReleaseModal}
                    className="h-8 gap-1.5 rounded-lg border-slate-200 text-xs font-semibold text-slate-700 hover:border-[#6f62e8] hover:text-[#6f62e8]"
                  >
                    <GitBranch className="size-3.5 text-amber-500" />
                    灰度与发布
                  </Button>

                  {/* 提交并发布 (主按钮) */}
                  <Button
                    size="sm"
                    disabled={isSubmitting}
                    onClick={handleSubmitPromptContent}
                    className="h-8 gap-1.5 rounded-lg bg-[#6f62e8] text-xs font-bold text-white shadow-xs hover:bg-[#5f52d9]"
                  >
                    {isSubmitting ? (
                      <Loader2 className="size-3.5 animate-spin" />
                    ) : (
                      <Send className="size-3.5" />
                    )}
                    提交并发布
                  </Button>
                </div>
              </div>

              {/* Dify 动态 Mustache 变量解析条 + 契约 JSON 配置按钮 */}
              <div className="flex shrink-0 flex-wrap items-center justify-between gap-2 border-b border-[#e8ebf1] bg-[#fcfcff] px-5 py-2">
                <div className="flex items-center gap-2 text-xs font-bold text-slate-800">
                  <Code2 className="size-3.5 text-[#6f62e8]" />
                  <span>变量契约:</span>
                </div>

                <div className="flex flex-1 flex-wrap items-center gap-1.5 px-2">
                  {detectedVariables.length === 0 ? (
                    <span className="text-[11px] text-slate-400 italic">
                      编辑框中使用 {'{{varName}}'} 自动提取变量
                    </span>
                  ) : (
                    detectedVariables.map((v) => {
                      const isRequired = requiredSchemaVariables.includes(v)
                      return (
                        <TooltipProvider key={v}>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <button
                                type="button"
                                onClick={() => handleInsertVariable(v)}
                                className={cn(
                                  'flex items-center gap-1 rounded-md border px-2 py-0.5 font-mono text-[11px] font-semibold transition-transform hover:scale-105',
                                  isRequired
                                    ? 'border-purple-200 bg-purple-100 text-[#5649ce]'
                                    : 'border-slate-200 bg-white text-slate-700',
                                )}
                              >
                                <span>{`{{${v}}}`}</span>
                                {isRequired && <span className="text-[8px] text-purple-600 font-sans font-bold">必填</span>}
                              </button>
                            </TooltipTrigger>
                            <TooltipContent side="bottom" className="text-[10px]">点击插入光标尾部</TooltipContent>
                          </Tooltip>
                        </TooltipProvider>
                      )
                    })
                  )}
                </div>

                <div className="flex items-center gap-2 text-[10px] text-slate-400">
                  <span>Schema 要求: {requiredSchemaVariables.length > 0 ? requiredSchemaVariables.join(', ') : '无'}</span>
                  <button
                    type="button"
                    onClick={handleOpenEditDefinition}
                    className="flex items-center gap-1 font-semibold text-[#6f62e8] hover:underline"
                  >
                    <Settings2 className="size-3" />
                    配置契约
                  </button>
                </div>
              </div>

              {/* 代码行号风格 IDE 编辑器区域 (支持浅色明亮/暗黑主题切换) */}
              <div
                className={cn(
                  'relative flex min-h-0 flex-1 transition-colors',
                  editorTheme === 'light' ? 'bg-[#fdfdff]' : 'bg-[#1e1e2e]',
                )}
              >
                {/* 行号列 */}
                <div
                  ref={lineNumbersRef}
                  className={cn(
                    'w-11 shrink-0 select-none overflow-hidden py-4 text-right font-mono text-xs leading-6 pr-2.5 border-r transition-colors',
                    editorTheme === 'light'
                      ? 'border-[#e8ebf1] bg-[#f4f3f8] text-slate-400'
                      : 'border-[#2d2d3f] bg-[#181825] text-slate-600',
                  )}
                >
                  {Array.from({ length: lineCount }).map((_, i) => (
                    <div key={i}>{i + 1}</div>
                  ))}
                </div>

                {/* 文本输入框 */}
                <textarea
                  ref={textareaRef}
                  value={editorContent}
                  onChange={(e) => setEditorContent(e.target.value)}
                  onScroll={handleScrollEditor}
                  placeholder="请输入 Prompt 模板正文，支持使用 {{varName}} 进行变量插值…"
                  className={cn(
                    'h-full w-full resize-none bg-transparent p-4 font-mono text-xs leading-6 outline-none transition-colors',
                    editorTheme === 'light'
                      ? 'text-slate-800 placeholder:text-slate-400'
                      : 'text-slate-100 placeholder:text-slate-500',
                  )}
                />
              </div>

              {/* 编辑器状态 Bottom Status Bar */}
              <div className="flex h-8 shrink-0 items-center justify-between border-t border-[#e8ebf1] bg-[#fafafc] px-5 text-[11px] text-slate-500">
                <div className="flex items-center gap-3">
                  {isDirty ? (
                    <span className="flex items-center gap-1 font-semibold text-amber-600">
                      <AlertCircle className="size-3" />
                      存在未保存修改
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 text-emerald-600 font-medium">
                      <CheckCircle2 className="size-3" />
                      与当前版本同步
                    </span>
                  )}
                </div>
                <div>
                  <span>总行数: {lineCount}</span> · <span>字符数: {editorContent.length}</span>
                </div>
              </div>
            </>
          ) : (
            <div className="flex flex-1 flex-col items-center justify-center text-slate-400">
              <Terminal className="size-10 text-slate-300" />
              <p className="mt-3 text-sm font-semibold">请在左侧选择一个 Prompt 定义以进行在线编辑与发布控制。</p>
            </div>
          )}
        </section>

        {/* Pane 3: 右侧 Inspector 检查器面板 - 灰度发布与版本历史 (340px) */}
        <aside className="flex w-[340px] shrink-0 flex-col border-l border-[#e8ebf1] bg-white">
          {/* Inspector Tab Header */}
          <div className="flex h-11 shrink-0 items-center border-b border-[#e8ebf1] bg-[#fafafc] px-3">
            <button
              type="button"
              onClick={() => setInspectorTab('release')}
              className={cn(
                'flex flex-1 items-center justify-center gap-1.5 py-2 text-xs font-bold transition-all border-b-2',
                inspectorTab === 'release'
                  ? 'border-[#6f62e8] text-[#5649ce]'
                  : 'border-transparent text-slate-500 hover:text-slate-800',
              )}
            >
              <SlidersHorizontal className="size-3.5" />
              发布分流
            </button>

            <button
              type="button"
              onClick={() => setInspectorTab('history')}
              className={cn(
                'flex flex-1 items-center justify-center gap-1.5 py-2 text-xs font-bold transition-all border-b-2',
                inspectorTab === 'history'
                  ? 'border-[#6f62e8] text-[#5649ce]'
                  : 'border-transparent text-slate-500 hover:text-slate-800',
              )}
            >
              <History className="size-3.5" />
              版本历史 ({currentPrompt?.versions?.length || 0})
            </button>
          </div>

          {/* Inspector Tab 1: 发布分流卡片 */}
          {inspectorTab === 'release' && (
            <div className="min-h-0 flex-1 overflow-y-auto p-4 space-y-4">
              <div className="rounded-xl border border-slate-200 bg-[#fbfaff] p-3.5">
                <div className="flex items-center justify-between text-xs font-bold text-slate-800 pb-2 border-b border-slate-100">
                  <span>当前物理发布 (Release)</span>
                  <span className="rounded-md bg-emerald-50 px-1.5 py-0.5 text-[9px] text-emerald-600">生效中</span>
                </div>

                <div className="mt-3 space-y-3">
                  {/* 正式版本 */}
                  <div className="rounded-lg border border-emerald-200 bg-emerald-50/60 p-3">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-bold text-emerald-800">🟢 正式版本 (Stable)</span>
                      <span className="font-mono font-bold text-emerald-700">
                        {activeStableVersion ? `v${activeStableVersion.versionNo}` : '暂无'}
                      </span>
                    </div>
                    <p className="mt-1 font-mono text-[10px] text-emerald-900 truncate">
                      ID: {activeRelease?.stableVersionId || '未配置'}
                    </p>
                    <p className="mt-0.5 text-[10px] text-slate-500">
                      发布时间: {activeRelease?.releasedAt ? new Date(activeRelease.releasedAt).toLocaleString() : '-'}
                    </p>
                  </div>

                  {/* 灰度版本 */}
                  <div className="rounded-lg border border-amber-200 bg-amber-50/60 p-3">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-bold text-amber-800">🟡 灰度分流 (Canary)</span>
                      <span className="font-mono font-bold text-amber-700">
                        {activeCanaryVersion ? `v${activeCanaryVersion.versionNo}` : '未启用'}
                      </span>
                    </div>

                    {activeCanaryVersion ? (
                      <div className="mt-2 space-y-1.5">
                        <div className="flex justify-between text-[10px] font-semibold text-amber-900">
                          <span>灰度流量比例</span>
                          <span>{canaryPercentage}%</span>
                        </div>
                        <div className="h-2 w-full overflow-hidden rounded-full bg-amber-200/70">
                          <div
                            className="h-full bg-amber-500 transition-all duration-300"
                            style={{ width: `${canaryPercentage}%` }}
                          />
                        </div>
                      </div>
                    ) : (
                      <p className="mt-1 text-[11px] text-slate-500">
                        100% 流量走向正式版本。
                      </p>
                    )}
                  </div>
                </div>

                <Button
                  size="sm"
                  onClick={handleOpenReleaseModal}
                  className="mt-3.5 w-full gap-1.5 rounded-lg bg-[#6f62e8] text-xs font-bold text-white hover:bg-[#5f52d9]"
                >
                  <GitBranch className="size-3.5" />
                  配置灰度与正式发布
                </Button>
              </div>
            </div>
          )}

          {/* Inspector Tab 2: 不可变版本历史 Timeline */}
          {inspectorTab === 'history' && (
            <div className="min-h-0 flex-1 overflow-y-auto p-4 space-y-2.5">
              {currentPrompt?.versions && currentPrompt.versions.length > 0 ? (
                currentPrompt.versions.map((ver) => {
                  const isStable = Boolean(activeRelease && String(activeRelease.stableVersionId) === String(ver.versionId))
                  const isCanary = activeRelease && String(activeRelease.canaryVersionId) === String(ver.versionId)
                  return (
                    <div
                      key={ver.versionId}
                      className="rounded-xl border border-slate-200/80 bg-slate-50/70 p-3 text-xs transition-colors hover:bg-slate-100/70 space-y-1.5"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-1.5">
                          <span className="font-mono font-bold text-slate-900">v{ver.versionNo}</span>
                          {isStable && (
                            <span className="rounded-md bg-emerald-100 px-1.5 py-0.5 text-[9px] font-bold text-emerald-700">
                              正式版
                            </span>
                          )}
                          {isCanary && (
                            <span className="rounded-md bg-amber-100 px-1.5 py-0.5 text-[9px] font-bold text-amber-700">
                              灰度版
                            </span>
                          )}
                        </div>
                        <span className="text-[10px] text-slate-400">ID: {ver.versionId}</span>
                      </div>

                      <p className="text-[11px] text-slate-500 leading-4">
                        {ver.remark || '初始模板更新'}
                      </p>
                      <p className="text-[10px] text-slate-400">
                        {ver.createdBy} · {new Date(ver.createdAt).toLocaleString()}
                      </p>

                      <div className="flex items-center justify-end gap-2 pt-1 border-t border-slate-200/50">
                        <button
                          type="button"
                          onClick={() => handleOpenDiff(ver)}
                          className="flex items-center gap-1 text-[11px] font-semibold text-[#6f62e8] hover:underline"
                        >
                          <ArrowLeftRight className="size-3" />
                          对比
                        </button>

                        <button
                          type="button"
                          disabled={isStable}
                          onClick={() => handleOpenRollback(ver)}
                          className={cn(
                            'flex items-center gap-1 text-[11px] font-semibold',
                            isStable ? 'text-slate-300 cursor-not-allowed' : 'text-amber-600 hover:underline',
                          )}
                        >
                          <RotateCcw className="size-3" />
                          回滚
                        </button>
                      </div>
                    </div>
                  )
                })
              ) : (
                <p className="py-6 text-center text-xs text-slate-400">暂无版本历史</p>
              )}
            </div>
          )}
        </aside>

      </div>

      {/* 编辑定义信息 Dialog (支持修改名称、变量契约 JSON) */}
      <Dialog open={editDefinitionModalOpen} onOpenChange={setEditDefinitionModalOpen}>
        <DialogContent className="max-w-lg bg-white">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base font-bold text-slate-900">
              <Edit3 className="size-4 text-[#6f62e8]" />
              编辑 Prompt 基础定义与契约
            </DialogTitle>
            <DialogDescription className="text-xs text-slate-500">
              修改提示词名称或变量契约 JSON Schema，将更新后端 `prompt_definition` 主记录。
            </DialogDescription>
          </DialogHeader>

          <div className="mt-3 space-y-4">
            <div className="space-y-1.5">
              <label className="text-xs font-bold text-slate-800">提示词名称 (Prompt Name)</label>
              <input
                value={editPromptName}
                onChange={(e) => setEditPromptName(e.target.value)}
                placeholder="例如: 会话问题改写"
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 px-3 text-xs text-slate-800 outline-none focus:border-[#6f62e8] focus:bg-white"
              />
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center justify-between text-xs font-bold text-slate-800">
                <span>变量契约 JSON (Variable Schema JSON)</span>
                <span className="text-[10px] text-slate-400 font-normal">须符合合法 JSON 结构</span>
              </div>
              <textarea
                value={editVariableSchema}
                onChange={(e) => setEditVariableSchema(e.target.value)}
                rows={5}
                placeholder='例如: {"required":["question"]}'
                className="w-full resize-y rounded-xl border border-slate-200 bg-[#1e1e2e] p-3 font-mono text-xs text-slate-100 outline-none placeholder:text-slate-500 focus:border-[#6f62e8]"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button
                variant="outline"
                size="sm"
                disabled={updatingDefinition}
                onClick={() => setEditDefinitionModalOpen(false)}
                className="rounded-xl text-xs"
              >
                取消
              </Button>
              <Button
                size="sm"
                disabled={updatingDefinition}
                onClick={handleSaveDefinition}
                className="gap-1.5 rounded-xl bg-[#6f62e8] text-xs font-bold text-white hover:bg-[#5f52d9]"
              >
                {updatingDefinition ? <Loader2 className="size-3.5 animate-spin" /> : <CheckCircle2 className="size-3.5" />}
                保存定义修改
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* 脱敏试运行预览 Dialog */}
      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent className="max-w-2xl bg-white">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base font-bold text-slate-900">
              <Eye className="size-4 text-[#6f62e8]" />
              Prompt 脱敏预览 (Preview Render)
            </DialogTitle>
            <DialogDescription className="text-xs text-slate-500">
              使用后端脱敏示例变量实时渲染当前编辑模板，不会写入数据库或触发模型实际扣费。
            </DialogDescription>
          </DialogHeader>

          <div className="mt-3 space-y-3">
            <div className="rounded-xl border border-slate-200 bg-[#fdfdff] p-4 text-xs font-mono text-slate-800 max-h-[360px] overflow-y-auto whitespace-pre-wrap leading-6">
              {previewing ? (
                <div className="flex items-center gap-2 text-[#6f62e8]">
                  <Loader2 className="size-4 animate-spin" />
                  <span>正在渲染脱敏正文…</span>
                </div>
              ) : (
                previewResult || '无输出'
              )}
            </div>

            <div className="flex justify-end">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPreviewOpen(false)}
                className="rounded-xl text-xs"
              >
                关闭预览
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* 灰度分流与正式发布 Dialog */}
      <Dialog open={releaseModalOpen} onOpenChange={setReleaseModalOpen}>
        <DialogContent className="max-w-md bg-white">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base font-bold text-slate-900">
              <GitBranch className="size-4 text-amber-500" />
              配置 Prompt 正式版与灰度分流发布
            </DialogTitle>
            <DialogDescription className="text-xs text-slate-500">
              将历史已提交的物理版本上线。灰度流量触发时，全站根据固定百分比随机路由给灰度版本。
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 space-y-4">
            <div className="space-y-1.5">
              <label className="text-xs font-bold text-slate-800">1. 正式物理版本 (Stable Version)</label>
              <CustomSelect
                value={String(selectedStableVersionId)}
                onChange={(val) => setSelectedStableVersionId(val)}
                options={versionSelectOptions}
                className="w-full"
              />
            </div>

            <div className="space-y-3 rounded-xl border border-amber-100 bg-amber-50/40 p-3.5">
              <label className="flex items-center gap-2 cursor-pointer text-xs font-bold text-amber-900">
                <input
                  type="checkbox"
                  checked={enableCanary}
                  onChange={(e) => setEnableCanary(e.target.checked)}
                  className="rounded border-amber-300 text-[#6f62e8] focus:ring-[#6f62e8]"
                />
                开启灰度版本分流 (Enable Canary)
              </label>

              {enableCanary && (
                <div className="space-y-3 pt-2">
                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-slate-700">2. 灰度物理版本 (Canary Version)</label>
                    <CustomSelect
                      value={String(selectedCanaryVersionId)}
                      onChange={(val) => setSelectedCanaryVersionId(val)}
                      options={versionSelectOptions}
                      className="w-full"
                    />
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex justify-between text-xs font-semibold text-slate-700">
                      <span>灰度分配百分比</span>
                      <span className="font-mono font-bold text-[#6f62e8]">{canaryPercentage}%</span>
                    </div>
                    <input
                      type="range"
                      min={0}
                      max={100}
                      value={canaryPercentage}
                      onChange={(e) => setCanaryPercentage(Number(e.target.value))}
                      className="w-full accent-[#6f62e8]"
                    />
                  </div>
                </div>
              )}
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button
                variant="outline"
                size="sm"
                disabled={releasing}
                onClick={() => setReleaseModalOpen(false)}
                className="rounded-xl text-xs"
              >
                取消
              </Button>
              <Button
                size="sm"
                disabled={releasing}
                onClick={handleConfirmRelease}
                className="gap-1.5 rounded-xl bg-[#6f62e8] text-xs font-bold text-white hover:bg-[#5f52d9]"
              >
                {releasing ? <Loader2 className="size-3.5 animate-spin" /> : <CheckCircle2 className="size-3.5" />}
                确认发布此配置
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* 回滚确认 Dialog */}
      <Dialog open={rollbackModalOpen} onOpenChange={setRollbackModalOpen}>
        <DialogContent className="max-w-md bg-white">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base font-bold text-slate-900">
              <RotateCcw className="size-4 text-amber-600" />
              确认回滚 Prompt 版本？
            </DialogTitle>
            <DialogDescription className="mt-2 text-xs text-slate-500">
              确定要将 Prompt [{selectedCode}] 回滚至版本 v{rollbackTargetVersion?.versionNo} (ID: {rollbackTargetVersion?.versionId}) 吗？操作将立即生成新发布代次并对生产生效。
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 flex justify-end gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={rollingBack}
              onClick={() => setRollbackModalOpen(false)}
              className="rounded-xl text-xs"
            >
              取消
            </Button>
            <Button
              size="sm"
              disabled={rollingBack}
              onClick={handleConfirmRollback}
              className="gap-1.5 rounded-xl bg-amber-600 text-xs font-bold text-white hover:bg-amber-700"
            >
              {rollingBack ? <Loader2 className="size-3.5 animate-spin" /> : <RotateCcw className="size-3.5" />}
              确认执行回滚
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* 版本 Diff 对比 Dialog */}
      <Dialog open={diffModalOpen} onOpenChange={setDiffModalOpen}>
        <DialogContent className="max-w-3xl bg-white">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base font-bold text-slate-900">
              <ArrowLeftRight className="size-4 text-[#6f62e8]" />
              版本对比 (Version Comparison: v{diffVersion?.versionNo} vs 当前编辑器)
            </DialogTitle>
          </DialogHeader>

          <div className="mt-3 grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <span className="text-xs font-bold text-slate-700">历史版本 v{diffVersion?.versionNo} 正文</span>
              <div className="rounded-xl border border-slate-200 bg-[#fdfdff] p-3 font-mono text-xs text-slate-800 max-h-[300px] overflow-y-auto whitespace-pre-wrap leading-5">
                {diffVersion?.content || '无内容'}
              </div>
            </div>

            <div className="space-y-1.5">
              <span className="text-xs font-bold text-[#6f62e8]">当前编辑器窗口正文</span>
              <div className="rounded-xl border border-purple-200 bg-[#fcfcff] p-3 font-mono text-xs text-slate-800 max-h-[300px] overflow-y-auto whitespace-pre-wrap leading-5">
                {editorContent || '无内容'}
              </div>
            </div>
          </div>

          <div className="mt-4 flex justify-end">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setDiffModalOpen(false)}
              className="rounded-xl text-xs"
            >
              关闭对比
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}

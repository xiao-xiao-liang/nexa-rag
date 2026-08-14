import { useEffect, useMemo, useState } from 'react'
import {
  Copy, Cpu, Database, Edit3, Eye, EyeOff, Key, Link2, Loader2, Plus, RefreshCw, Search, Sparkles, Wrench,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/ui/empty-state'
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tag } from '@/components/ui/tag'
import { Toast } from '@/components/ui/toast'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { CustomSelect, type SelectOption } from '@/components/ui/select'
import { cn } from '@/lib/utils'
import {
  getModelProviderCatalog,
  getModelConfigs,
  createModelConfig,
  updateModelConfig,
  deleteModelConfig,
  testModelConfig,
  type ModelProviderCatalogItem,
  type ModelConfigItem,
} from '../api/model-api'

// 对应 LobeHub 官方图片 CDN 镜像
const PROVIDER_LOGOS: Record<string, string> = {
  dashscope: 'https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-png/light/bailian-color.png',
  openai: 'https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-png/light/openai.png',
  deepseek: 'https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-png/light/deepseek-color.png',
  zhipu: 'https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-png/light/zhipu-color.png',
  anthropic: 'https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-png/light/anthropic.png',
  siliconflow: 'https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-png/light/siliconcloud-color.png',
  moonshot: 'https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-png/light/moonshot.png',
  ollama: 'https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-png/light/ollama.png',
  compatible: 'https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-png/light/vllm-color.png',
}

const getProviderLogoUrl = (providerKey: string) => {
  if (!providerKey) return PROVIDER_LOGOS.compatible
  const key = providerKey.toLowerCase()
  if (key.includes('dashscope') || key.includes('bailian') || key.includes('aliyun')) return PROVIDER_LOGOS.dashscope
  if (key.includes('openai')) return PROVIDER_LOGOS.openai
  if (key.includes('deepseek')) return PROVIDER_LOGOS.deepseek
  if (key.includes('zhipu') || key.includes('glm')) return PROVIDER_LOGOS.zhipu
  if (key.includes('anthropic') || key.includes('claude')) return PROVIDER_LOGOS.anthropic
  if (key.includes('silicon') || key.includes('siliconflow') || key.includes('siliconcloud')) return PROVIDER_LOGOS.siliconflow
  if (key.includes('moonshot') || key.includes('kimi')) return PROVIDER_LOGOS.moonshot
  if (key.includes('ollama')) return PROVIDER_LOGOS.ollama
  return PROVIDER_LOGOS.compatible
}

const CATEGORY_OPTIONS: SelectOption[] = [
  { value: 'Chat', label: 'Chat (大语言模型对话)' },
  { value: 'Embedding', label: 'Embedding (文本向量嵌入)' },
  { value: 'Rerank', label: 'Rerank (精细重排引擎)' },
]

/** 模型供应商与治理网关页：飞书式两栏布局。 */
export default function ModelConfigPage() {
  const [catalogProviders, setCatalogProviders] = useState<ModelProviderCatalogItem[]>([])
  const [realConfigs, setRealConfigs] = useState<ModelConfigItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [selectedProviderId, setSelectedProviderId] = useState<string>('')
  const [searchKeyword, setSearchKeyword] = useState('')
  const [categoryFilter, setCategoryFilter] = useState<'all' | 'joined'>('all')
  const [toastMessage, setToastMessage] = useState<string | null>(null)
  const [testingConfigId, setTestingConfigId] = useState<number | string | null>(null)

  // Modals 控制
  const [credentialsModalOpen, setCredentialsModalOpen] = useState(false)
  const [addModelModalOpen, setAddModelModalOpen] = useState(false)
  const [editModelModalOpen, setEditModelModalOpen] = useState(false)

  // 表单状态
  const [formBaseUrl, setFormBaseUrl] = useState('')
  const [formApiKey, setFormApiKey] = useState('')
  const [showApiKey, setShowApiKey] = useState(false)
  const [newModelName, setNewModelName] = useState('')
  const [newModelCategory, setNewModelCategory] = useState<'Chat' | 'Embedding' | 'Rerank'>('Chat')

  // 编辑表单状态
  const [editingConfigId, setEditingConfigId] = useState<number | string | null>(null)
  const [editModelName, setEditModelName] = useState('')
  const [editModelCategory, setEditModelCategory] = useState<'Chat' | 'Embedding' | 'Rerank'>('Chat')
  const [editBaseUrl, setEditBaseUrl] = useState('')
  const [editApiKey, setEditApiKey] = useState('')
  const [showEditApiKey, setShowEditApiKey] = useState(false)

  const loadData = async () => {
    setLoading(true)
    setError(null)
    try {
      const [catalogRes, configsRes] = await Promise.all([
        getModelProviderCatalog(),
        getModelConfigs(),
      ])
      setCatalogProviders(catalogRes || [])
      setRealConfigs(configsRes || [])

      if (catalogRes && catalogRes.length > 0) {
        setSelectedProviderId(catalogRes[0].provider)
      }
    } catch (err) {
      console.error('加载模型配置失败:', err)
      setError(err instanceof Error ? err.message : '无法连接至后端模型配置服务，请检查网络或后端状态')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadData()
  }, [])

  const selectedCatalog = useMemo(() => {
    return catalogProviders.find((p) => p.provider === selectedProviderId) || catalogProviders[0]
  }, [catalogProviders, selectedProviderId])

  const currentProviderConfigs = useMemo(() => {
    if (!selectedProviderId) return []
    return realConfigs.filter((c) => c.provider?.toUpperCase() === selectedProviderId?.toUpperCase())
  }, [realConfigs, selectedProviderId])

  const recommendedModelsList = useMemo(() => {
    if (!selectedCatalog?.recommendedModels) return []
    const list: { name: string; type: 'Chat' | 'Embedding' | 'Rerank' }[] = []
    Object.entries(selectedCatalog.recommendedModels).forEach(([typeStr, names]) => {
      const type = typeStr.toUpperCase().includes('EMBEDD')
        ? 'Embedding'
        : typeStr.toUpperCase().includes('RERANK')
          ? 'Rerank'
          : 'Chat'
      names.forEach((name) => list.push({ name, type }))
    })
    return list
  }, [selectedCatalog])

  const filteredCatalogProviders = useMemo(() => {
    return catalogProviders.filter((p) => {
      const hasConfigs = realConfigs.some((c) => c.provider?.toUpperCase() === p.provider?.toUpperCase())
      if (categoryFilter === 'joined' && !hasConfigs) return false

      if (searchKeyword.trim()) {
        const query = searchKeyword.trim().toLowerCase()
        return (
          (p.displayName || p.provider).toLowerCase().includes(query) ||
          p.provider.toLowerCase().includes(query)
        )
      }
      return true
    })
  }, [catalogProviders, realConfigs, categoryFilter, searchKeyword])

  const showToast = (msg: string) => {
    setToastMessage(msg)
    setTimeout(() => setToastMessage(null), 2500)
  }

  const handleCopyText = (text: string, label: string) => {
    if (!text) return
    navigator.clipboard.writeText(text)
    showToast(`已成功复制 ${label} 到剪贴板`)
  }

  const handleOpenCredentials = (providerId: string) => {
    setSelectedProviderId(providerId)
    const p = catalogProviders.find((item) => item.provider === providerId)
    setFormBaseUrl(p?.defaultBaseUrl || 'https://api.openai.com/v1')
    setFormApiKey('')
    setCredentialsModalOpen(true)
  }

  const handleOpenAddModel = (presetModelName?: string, presetType?: 'Chat' | 'Embedding' | 'Rerank') => {
    setNewModelName(presetModelName || '')
    if (presetType) setNewModelCategory(presetType)
    setAddModelModalOpen(true)
  }

  const handleOpenEditModel = (config: ModelConfigItem) => {
    setEditingConfigId(config.configId)
    setEditModelName(config.modelName || '')
    const cat = (config.modelType?.toUpperCase().includes('EMBEDD')
      ? 'Embedding'
      : config.modelType?.toUpperCase().includes('RERANK')
        ? 'Rerank'
        : 'Chat') as 'Chat' | 'Embedding' | 'Rerank'
    setEditModelCategory(cat)
    setEditBaseUrl(config.baseUrl || '')
    setEditApiKey('')
    setShowEditApiKey(false)
    setEditModelModalOpen(true)
  }

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newModelName.trim()) return
    try {
      const created = await createModelConfig({
        modelName: newModelName.trim(),
        provider: selectedProviderId,
        modelType: newModelCategory,
        baseUrl: formBaseUrl.trim() || selectedCatalog?.defaultBaseUrl || 'https://api.openai.com/v1',
        apiKey: formApiKey.trim(),
      })
      setRealConfigs((prev) => [created, ...prev])
      setAddModelModalOpen(false)
      setCredentialsModalOpen(false)
      showToast(`保存模型节点 [${created.modelName}] 成功！`)
    } catch (err) {
      showToast(`创建失败: ${err instanceof Error ? err.message : '服务器未响应'}`)
    }
  }

  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!editingConfigId || !editModelName.trim()) return
    try {
      const updated = await updateModelConfig(editingConfigId, {
        modelName: editModelName.trim(),
        modelType: editModelCategory,
        baseUrl: editBaseUrl.trim() || undefined,
        apiKey: editApiKey.trim() || undefined,
      })
      setRealConfigs((prev) => prev.map((c) => (c.configId === editingConfigId ? updated : c)))
      setEditModelModalOpen(false)
      showToast(`已成功修改模型 [${updated.modelName}] 的配置与 API Key！`)
    } catch (err) {
      showToast(`修改失败: ${err instanceof Error ? err.message : '服务器拒绝'}`)
    }
  }

  const handleTestConnectionReal = async (configId: number | string, modelName: string) => {
    setTestingConfigId(configId)
    try {
      const res = await testModelConfig(configId)
      showToast(res?.success ? `探针测试: [${modelName}] 连通物理成功！` : `测试反馈: ${res?.message || '连接失败'}`)
    } catch (err) {
      showToast(`测试失败: ${err instanceof Error ? err.message : '物理端口拒绝'}`)
    } finally {
      setTestingConfigId(null)
    }
  }

  const handleDeleteConfigReal = async (configId: number | string, modelName: string) => {
    try {
      await deleteModelConfig(configId)
      setRealConfigs((prev) => prev.filter((c) => c.configId !== configId))
      showToast(`已成功删除模型节点配置 [${modelName}]`)
    } catch (err) {
      showToast(`删除失败: ${err instanceof Error ? err.message : '未知错误'}`)
    }
  }

  if (loading) {
    return (
      <div className="flex h-full min-h-0 flex-1 items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-3 text-secondary">
          <Loader2 className="size-8 animate-spin text-primary" />
          <p className="text-xs font-medium">正在从后端加载真实模型配置与厂商目录…</p>
        </div>
      </div>
    )
  }

  const inputClass = 'w-full rounded-md border border-input bg-card px-2.5 py-1.5 text-xs text-foreground outline-none focus:border-primary focus:ring-2 focus:ring-primary/30'

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col overflow-y-auto bg-background">
      <Toast message={toastMessage} />
      {error && (
        <div className="mx-auto mt-4 w-full max-w-[1200px] px-6">
          <div className="rounded-md border border-danger-light bg-danger-light p-3 text-xs text-danger">{error}</div>
        </div>
      )}

      {/* 顶部 Header */}
      <header className="border-b border-border bg-card px-6 py-4">
        <div className="w-full px-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="flex size-8 items-center justify-center rounded-md bg-primary-light text-primary">
                <Cpu className="size-4" />
              </span>
              <div>
                <h1 className="text-lg font-semibold text-foreground">模型供应商与治理网关</h1>
                <p className="text-[11px] text-tertiary">统一管理厂商连接凭据与全站模型调度路由。</p>
              </div>
            </div>
            <Button size="sm" onClick={() => handleOpenCredentials(selectedProviderId)}>
              <Key className="size-3.5" />
              配置当前供应商凭据
            </Button>
          </div>

        </div>
      </header>

      {/* 主体：两栏布局 */}
      <main className="w-full min-w-0 flex-1 px-6 py-5">
        <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-[200px_minmax(0,1fr)]">
          {/* 左侧供应商列表 */}
          <aside className="rounded-lg border border-border bg-card p-3">
            <div className="relative mb-2">
              <Search className="absolute left-2.5 top-2.5 size-3.5 text-tertiary" />
              <input
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                placeholder="搜索供应商或模型…"
                className="h-8 w-full rounded-md border border-border bg-muted pl-8 pr-3 text-xs text-foreground outline-none placeholder:text-tertiary focus:border-primary focus:bg-card"
              />
            </div>
            <div className="mb-2 flex gap-1 border-b border-border pb-2">
              {[
                { id: 'all', label: '全部' },
                { id: 'joined', label: '已配置' },
              ].map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setCategoryFilter(item.id as 'all' | 'joined')}
                  className={cn(
                    'rounded px-2.5 py-1 text-xs transition-colors',
                    categoryFilter === item.id
                      ? 'bg-primary-light font-medium text-primary'
                      : 'text-secondary hover:bg-muted',
                  )}
                >
                  {item.label}
                </button>
              ))}
            </div>
            <div className="max-h-[calc(100vh-340px)] space-y-1 overflow-y-auto">
              <TooltipProvider>
                {filteredCatalogProviders.map((provider) => {
                  const isSelected = selectedProviderId === provider.provider
                  const providerConfigs = realConfigs.filter(
                    (c) => c.provider?.toUpperCase() === provider.provider?.toUpperCase(),
                  )
                  const hasJoined = providerConfigs.length > 0
                  const displayName = provider.displayName || provider.provider

                  return (
                    <div
                      key={provider.provider}
                      onClick={() => setSelectedProviderId(provider.provider)}
                      className={cn(
                        'group flex cursor-pointer items-center justify-between rounded px-3 py-2 transition-colors',
                        isSelected ? 'bg-primary-light text-primary' : 'text-secondary hover:bg-muted',
                      )}
                    >
                      <div className="flex min-w-0 items-center gap-2.5">
                        <div className="flex size-7 shrink-0 items-center justify-center rounded bg-card p-1 ring-1 ring-border">
                          <img src={getProviderLogoUrl(provider.provider)} alt={displayName} className="size-full object-contain" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-xs font-medium">{displayName}</p>
                          <p className="truncate text-[10px] text-tertiary">{providerConfigs.length} 项已接入配置</p>
                        </div>
                      </div>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <span
                            className={cn(
                              'size-2 shrink-0 rounded-full',
                              hasJoined ? 'bg-success' : 'bg-border',
                            )}
                          />
                        </TooltipTrigger>
                        <TooltipContent side="right" className="text-[11px]">
                          {hasJoined ? '凭据已生效 (已配置接入)' : '凭据未配置 (无激活模型)'}
                        </TooltipContent>
                      </Tooltip>
                    </div>
                  )
                })}
              </TooltipProvider>
            </div>
          </aside>

          {/* 右侧供应商详情 */}
          <section className="rounded-lg border border-border bg-card p-5">
            {selectedCatalog && (
              <>
                <div className="flex flex-col gap-4 border-b border-border pb-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="flex items-center gap-4">
                    <div className="flex size-11 items-center justify-center rounded-md bg-card p-2 ring-1 ring-border">
                      <img
                        src={getProviderLogoUrl(selectedCatalog.provider)}
                        alt={selectedCatalog.displayName || selectedCatalog.provider}
                        className="size-full object-contain"
                      />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <h2 className="text-base font-semibold text-foreground">
                          {selectedCatalog.displayName || selectedCatalog.provider}
                        </h2>
                        <Tag variant={currentProviderConfigs.length > 0 ? 'success' : 'neutral'}>
                          {currentProviderConfigs.length > 0 ? '凭据与模型已生效' : '未配置模型'}
                        </Tag>
                      </div>
                      <p className="mt-1 text-xs text-tertiary">
                        {selectedCatalog.defaultGovernanceDescription || '统一模型连接凭据与节点能力提供商。'}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <Button size="sm" variant="outline" onClick={() => handleOpenCredentials(selectedCatalog.provider)}>
                      <Wrench className="size-3.5" />
                      管理 API 凭据
                    </Button>
                    <Button size="sm" onClick={() => handleOpenAddModel()}>
                      <Plus className="size-3.5" />
                      增加自定义模型
                    </Button>
                  </div>
                </div>

                {/* 推荐的模型快捷配置区 */}
                {recommendedModelsList.length > 0 && (
                  <div className="mt-4 rounded-md border border-border bg-muted p-3">
                    <p className="flex items-center gap-1.5 text-xs font-medium text-secondary">
                      <Sparkles className="size-3.5 text-primary" />
                      后端推荐预设模型，一键快捷接入：
                    </p>
                    <div className="mt-2 flex flex-wrap gap-2">
                      {recommendedModelsList.map((rec) => {
                        const isAlreadyConfigured = currentProviderConfigs.some((c) => c.modelName === rec.name)
                        return (
                          <div key={rec.name} className="flex items-center gap-2 rounded-md border border-border bg-card px-2.5 py-1.5">
                            <span className="font-mono text-xs font-medium text-foreground">{rec.name}</span>
                            <Tag variant={rec.type === 'Chat' ? 'info' : rec.type === 'Embedding' ? 'success' : 'warning'}>
                              {rec.type}
                            </Tag>
                            <Button
                              type="button"
                              size="sm"
                              variant={isAlreadyConfigured ? 'outline' : 'default'}
                              disabled={isAlreadyConfigured}
                              onClick={() => handleOpenAddModel(rec.name, rec.type)}
                            >
                              {isAlreadyConfigured ? '已接入' : '一键接入'}
                            </Button>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                )}

                {/* 密钥信息 Bar */}
                <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-md bg-muted px-3.5 py-2.5 text-xs text-secondary">
                  <div className="flex flex-wrap items-center gap-4">
                    <span className="flex items-center gap-1.5">
                      <Link2 className="size-3.5 text-primary" />
                      Base URL: <span className="font-mono text-foreground">{selectedCatalog.defaultBaseUrl}</span>
                      <Copy
                        className="size-3 cursor-pointer text-tertiary hover:text-primary"
                        onClick={() => handleCopyText(selectedCatalog.defaultBaseUrl, 'Base URL 端点')}
                      />
                    </span>
                    <span className="flex items-center gap-1.5">
                      <Key className="size-3.5 text-tertiary" />
                      凭据 Key: <span className="font-mono text-foreground">{currentProviderConfigs[0]?.apiKeyMask || currentProviderConfigs[0]?.apiKeyMasked || 'sk-****default'}</span>
                      {currentProviderConfigs[0] && (
                        <Copy
                          className="size-3 cursor-pointer text-tertiary hover:text-primary"
                          onClick={() => handleCopyText(currentProviderConfigs[0]?.apiKeyMask || currentProviderConfigs[0]?.apiKeyMasked || '', '脱敏密钥')}
                        />
                      )}
                    </span>
                  </div>
                  <span className="text-[11px] text-tertiary">数据库已存储配置 {currentProviderConfigs.length} 项</span>
                </div>

                {/* 模型配置表 */}
                <div className="mt-4 overflow-hidden rounded-md border border-border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>模型标识</TableHead>
                        <TableHead>类型</TableHead>
                        <TableHead>端点</TableHead>
                        <TableHead>密钥</TableHead>
                        <TableHead className="text-right">操作</TableHead>
                        <TableHead>测试连接</TableHead>
                        <TableHead>删除</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {currentProviderConfigs.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={7}>
                            <EmptyState
                              icon={Database}
                              title="暂无已注册模型"
                              description="数据库中暂无该供应商的已注册模型，可点击【一键接入推荐模型】或【增加自定义模型】。"
                            />
                          </TableCell>
                        </TableRow>
                      ) : (
                        currentProviderConfigs.map((config) => {
                          const maskKey = config.apiKeyMask || config.apiKeyMasked || 'sk-****default'
                          return (
                            <TableRow key={config.configId}>
                              <TableCell className="font-mono font-medium text-foreground">
                                <div className="flex items-center gap-2">
                                  <span>{config.modelName}</span>
                                  <Copy
                                    className="size-3 cursor-pointer text-border hover:text-primary"
                                    onClick={() => handleCopyText(config.modelName, '模型名称')}
                                  />
                                </div>
                              </TableCell>
                              <TableCell>
                                <Tag variant={config.modelType === 'Chat' || config.modelType === 'LLM' ? 'info' : config.modelType === 'Embedding' || config.modelType === 'Text Embedding' ? 'success' : 'warning'}>
                                  {config.modelType}
                                </Tag>
                              </TableCell>
                              <TableCell className="max-w-[220px] truncate font-mono text-secondary" title={config.baseUrl}>
                                <div className="flex items-center gap-1.5">
                                  <span className="truncate">{config.baseUrl}</span>
                                  <Copy
                                    className="size-3 shrink-0 cursor-pointer text-border hover:text-primary"
                                    onClick={() => handleCopyText(config.baseUrl, 'Base URL 端点')}
                                  />
                                </div>
                              </TableCell>
                              <TableCell className="font-mono text-secondary">
                                <div className="flex items-center gap-1.5">
                                  <span>{maskKey}</span>
                                  <Copy
                                    className="size-3 cursor-pointer text-border hover:text-primary"
                                    onClick={() => handleCopyText(maskKey, '脱敏密钥')}
                                  />
                                </div>
                              </TableCell>
                              <TableCell className="text-right">
                                <button
                                  type="button"
                                  onClick={() => handleOpenEditModel(config)}
                                  className="flex items-center gap-1 text-primary hover:underline"
                                  title="编辑模型标识与 API Key"
                                >
                                  <Edit3 className="size-3.5" />
                                  编辑
                                </button>
                              </TableCell>
                              <TableCell>
                                <button
                                  type="button"
                                  disabled={testingConfigId === config.configId}
                                  onClick={() => void handleTestConnectionReal(config.configId, config.modelName)}
                                  className="flex items-center gap-1 text-primary hover:underline disabled:opacity-50"
                                >
                                  <RefreshCw className={cn('size-3', testingConfigId === config.configId && 'animate-spin')} />
                                  {testingConfigId === config.configId ? '测试中…' : '测试连接'}
                                </button>
                              </TableCell>
                              <TableCell>
                                <button
                                  type="button"
                                  onClick={() => void handleDeleteConfigReal(config.configId, config.modelName)}
                                  className="text-danger hover:underline"
                                  title="移除此配置"
                                >
                                  删除
                                </button>
                              </TableCell>
                            </TableRow>
                          )
                        })
                      )}
                    </TableBody>
                  </Table>
                </div>
              </>
            )}
          </section>
        </div>
      </main>

      {/* 凭据 Modal */}
      <Dialog open={credentialsModalOpen} onOpenChange={setCredentialsModalOpen}>
        <DialogContent className="max-w-lg bg-card">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2.5 text-base font-semibold text-foreground">
              <span className="flex size-7 items-center justify-center rounded bg-card p-1 ring-1 ring-border">
                <img
                  src={getProviderLogoUrl(selectedProviderId)}
                  alt={selectedCatalog?.displayName}
                  className="size-full object-contain"
                />
              </span>
              配置 {selectedCatalog?.displayName || selectedCatalog?.provider} 凭据
            </DialogTitle>
            <DialogDescription className="text-xs text-secondary">
              添加密钥并调用后端真实 REST API 存入数据库。
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="mt-4 space-y-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">配置激活的模型名称 (Model Name)</label>
              <input
                required
                value={newModelName}
                onChange={(e) => setNewModelName(e.target.value)}
                placeholder="例如: qwen-plus, gpt-4o 等"
                className={cn(inputClass, 'font-mono')}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">模型类型</label>
              <CustomSelect value={newModelCategory} onChange={(v) => setNewModelCategory(v as 'Chat' | 'Embedding' | 'Rerank')} options={CATEGORY_OPTIONS} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">API Key 密钥</label>
              <div className="relative">
                <input
                  type={showApiKey ? 'text' : 'password'}
                  required
                  value={formApiKey}
                  onChange={(e) => setFormApiKey(e.target.value)}
                  placeholder="sk-..."
                  className={cn(inputClass, 'pr-10 font-mono')}
                />
                <button type="button" onClick={() => setShowApiKey(!showApiKey)} className="absolute right-3 top-2 text-tertiary hover:text-secondary">
                  {showApiKey ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">API Base URL 端点</label>
              <input
                value={formBaseUrl}
                onChange={(e) => setFormBaseUrl(e.target.value)}
                placeholder={selectedCatalog?.defaultBaseUrl}
                className={cn(inputClass, 'font-mono')}
              />
            </div>
            <div className="mt-6 flex justify-end gap-2.5">
              <Button type="button" variant="outline" size="sm" onClick={() => setCredentialsModalOpen(false)}>取消</Button>
              <Button type="submit" size="sm">提交至后端生效</Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* 添加新模型 Modal */}
      <Dialog open={addModelModalOpen} onOpenChange={setAddModelModalOpen}>
        <DialogContent className="max-w-md bg-card">
          <DialogHeader>
            <DialogTitle className="text-base font-semibold text-foreground">
              在 [{selectedCatalog?.displayName || selectedCatalog?.provider}] 下定义新模型
            </DialogTitle>
            <DialogDescription className="text-xs text-secondary">
              配置新模型的类型与标识，保存后可立即在全站路由调用。
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="mt-4 space-y-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">模型类别 (Category)</label>
              <CustomSelect value={newModelCategory} onChange={(v) => setNewModelCategory(v as 'Chat' | 'Embedding' | 'Rerank')} options={CATEGORY_OPTIONS} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">模型标识名称 (Model ID)</label>
              <input
                required
                value={newModelName}
                onChange={(e) => setNewModelName(e.target.value)}
                placeholder="例如: qwen-turbo, gpt-4o-mini 等"
                className={cn(inputClass, 'font-mono')}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">API Key 密钥</label>
              <input
                type="password"
                required
                value={formApiKey}
                onChange={(e) => setFormApiKey(e.target.value)}
                placeholder="sk-..."
                className={cn(inputClass, 'font-mono')}
              />
            </div>
            <div className="mt-6 flex justify-end gap-2.5">
              <Button type="button" variant="outline" size="sm" onClick={() => setAddModelModalOpen(false)}>取消</Button>
              <Button type="submit" size="sm">添加真实配置</Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* 编辑模型配置 Modal */}
      <Dialog open={editModelModalOpen} onOpenChange={setEditModelModalOpen}>
        <DialogContent className="max-w-md bg-card">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base font-semibold text-foreground">
              <Edit3 className="size-4 text-primary" />
              编辑模型配置 [{editModelName}]
            </DialogTitle>
            <DialogDescription className="text-xs text-secondary">
              直接修改当前模型的 API Key 密钥、Base URL 及配置参数。
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleEditSubmit} className="mt-4 space-y-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">模型标识名称 (Model ID)</label>
              <input
                required
                value={editModelName}
                onChange={(e) => setEditModelName(e.target.value)}
                placeholder="例如: qwen-plus, gpt-4o 等"
                className={cn(inputClass, 'font-mono')}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">模型类别 (Category)</label>
              <CustomSelect value={editModelCategory} onChange={(v) => setEditModelCategory(v as 'Chat' | 'Embedding' | 'Rerank')} options={CATEGORY_OPTIONS} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">
                新 API Key 密钥 <span className="font-normal text-tertiary">（若不修改请留空）</span>
              </label>
              <div className="relative">
                <input
                  type={showEditApiKey ? 'text' : 'password'}
                  value={editApiKey}
                  onChange={(e) => setEditApiKey(e.target.value)}
                  placeholder="留空则保持现有 Key 不变 (sk-...)"
                  className={cn(inputClass, 'pr-10 font-mono')}
                />
                <button type="button" onClick={() => setShowEditApiKey(!showEditApiKey)} className="absolute right-3 top-2 text-tertiary hover:text-secondary">
                  {showEditApiKey ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">API Base URL 端点</label>
              <input
                value={editBaseUrl}
                onChange={(e) => setEditBaseUrl(e.target.value)}
                placeholder="https://api.openai.com/v1"
                className={cn(inputClass, 'font-mono')}
              />
            </div>
            <div className="mt-6 flex justify-end gap-2.5">
              <Button type="button" variant="outline" size="sm" onClick={() => setEditModelModalOpen(false)}>取消</Button>
              <Button type="submit" size="sm">保存配置修改</Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

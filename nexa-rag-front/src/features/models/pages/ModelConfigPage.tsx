import { useEffect, useMemo, useState } from 'react'
import {
  Activity, CheckCircle2, ChevronRight, Copy, Cpu, Database, Eye, EyeOff, Key,
  Layers, Link2, MoreHorizontal, Plus, RefreshCw, Search, Server, ShieldCheck,
  SlidersHorizontal, Sparkles, Trash2, Zap, X, Check, Globe, HelpCircle, Wrench,
  ToggleLeft, ToggleRight, Edit3, ExternalLink, AlertCircle, Loader2, ShieldAlert,
  Scale, ArrowRightLeft, Shield
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { CustomSelect, type SelectOption } from '@/components/ui/select'
import { cn } from '@/lib/utils'
import {
  getModelProviderCatalog,
  getModelConfigs,
  createModelConfig,
  deleteModelConfig,
  testModelConfig,
  getModelGovernanceConfig,
  updateModelGovernanceConfig,
  type ModelProviderCatalogItem,
  type ModelConfigItem,
  type ModelGovernanceConfigDTO,
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

const LLM_OPTIONS: SelectOption[] = [
  { value: 'qwen-plus', label: 'qwen-plus (阿里云)' },
  { value: 'qwen-max', label: 'qwen-max (阿里云)' },
  { value: 'gpt-4o', label: 'gpt-4o (OpenAI)' },
  { value: 'deepseek-chat', label: 'deepseek-chat (DeepSeek)' },
  { value: 'moonshot-v1-8k', label: 'moonshot-v1-8k (月之暗面)' },
]

const EMBEDDING_OPTIONS: SelectOption[] = [
  { value: 'text-embedding-v3', label: 'text-embedding-v3 (阿里云)' },
  { value: 'text-embedding-3-large', label: 'text-embedding-3-large (OpenAI)' },
  { value: 'bge-m3-local', label: 'bge-m3-local (私有化)' },
]

const RERANK_OPTIONS: SelectOption[] = [
  { value: 'gte-rerank', label: 'gte-rerank (阿里云)' },
  { value: 'bge-rerank-large', label: 'bge-rerank-large' },
]

const CATEGORY_OPTIONS: SelectOption[] = [
  { value: 'Chat', label: 'Chat (大语言模型对话)' },
  { value: 'Embedding', label: 'Embedding (文本向量嵌入)' },
  { value: 'Rerank', label: 'Rerank (精细重排引擎)' },
]

export default function ModelConfigPage() {
  const [catalogProviders, setCatalogProviders] = useState<ModelProviderCatalogItem[]>([])
  const [realConfigs, setRealConfigs] = useState<ModelConfigItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [selectedProviderId, setSelectedProviderId] = useState<string>('')
  const [searchKeyword, setSearchKeyword] = useState('')
  const [categoryFilter, setCategoryFilter] = useState<'all' | 'joined' | 'domestic' | 'international' | 'compatible'>('all')
  const [toastMessage, setToastMessage] = useState<string | null>(null)
  const [testingConfigId, setTestingConfigId] = useState<number | null>(null)

  // 全局默认系统模型设置
  const [defaultLLM, setDefaultLLM] = useState('qwen-plus')
  const [defaultEmbedding, setDefaultEmbedding] = useState('text-embedding-v3')
  const [defaultRerank, setDefaultRerank] = useState('gte-rerank')

  // Modals 控制
  const [credentialsModalOpen, setCredentialsModalOpen] = useState(false)
  const [addModelModalOpen, setAddModelModalOpen] = useState(false)
  const [governanceModalOpen, setGovernanceModalOpen] = useState(false)

  // 当前治理配置状态
  const [activeGovernanceConfig, setActiveGovernanceConfig] = useState<ModelConfigItem | null>(null)
  const [govStrategyMode, setGovStrategyMode] = useState<'FAILOVER' | 'WEIGHTED' | 'PROTECTION'>('FAILOVER')
  const [govTimeoutSec, setGovTimeoutSec] = useState<number>(30)
  const [govMaxRetries, setGovMaxRetries] = useState<number>(3)
  const [govMaxConcurrency, setGovMaxConcurrency] = useState<number>(10)
  const [govRateLimitRpm, setGovRateLimitRpm] = useState<number>(60)
  const [govFallbackModel, setGovFallbackModel] = useState<string>('deepseek-chat')
  const [govPrimaryWeight, setGovPrimaryWeight] = useState<number>(70)
  const [govCircuitBreaker, setGovCircuitBreaker] = useState<boolean>(true)

  // 表单状态
  const [formBaseUrl, setFormBaseUrl] = useState('')
  const [formApiKey, setFormApiKey] = useState('')
  const [showApiKey, setShowApiKey] = useState(false)
  const [newModelName, setNewModelName] = useState('')
  const [newModelCategory, setNewModelCategory] = useState<'Chat' | 'Embedding' | 'Rerank'>('Chat')

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
    } catch (err: any) {
      console.error('加载模型配置失败:', err)
      setError(err?.message || '无法连接至后端模型配置服务，请检查网络或后端状态')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
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

      const name = p.displayName || p.provider
      if (searchKeyword.trim()) {
        const query = searchKeyword.trim().toLowerCase()
        return (
          name.toLowerCase().includes(query) ||
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

  const handleOpenGovernanceModal = async (config: ModelConfigItem) => {
    setActiveGovernanceConfig(config)
    setGovernanceModalOpen(true)
    try {
      const res = await getModelGovernanceConfig(config.configId)
      if (res) {
        setGovStrategyMode(res.strategyMode || 'FAILOVER')
        setGovTimeoutSec(res.timeoutMs ? Math.round(res.timeoutMs / 1000) : 30)
        setGovMaxRetries(res.maxRetries ?? 3)
        setGovMaxConcurrency(res.maxConcurrency ?? 10)
        setGovRateLimitRpm(res.rateLimitRpm ?? 60)
        setGovFallbackModel(res.fallbackModel || 'deepseek-chat')
        setGovPrimaryWeight(res.primaryWeight ?? 70)
        setGovCircuitBreaker(res.circuitBreakerEnabled !== false)
      }
    } catch (err: any) {
      console.warn('获取治理参数失败，使用默认配置:', err)
    }
  }

  const handleSaveGovernanceSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!activeGovernanceConfig) return

    try {
      await updateModelGovernanceConfig(activeGovernanceConfig.configId, {
        configId: activeGovernanceConfig.configId,
        strategyMode: govStrategyMode,
        timeoutMs: govTimeoutSec * 1000,
        maxRetries: govMaxRetries,
        maxConcurrency: govMaxConcurrency,
        rateLimitRpm: govRateLimitRpm,
        fallbackModel: govFallbackModel,
        primaryWeight: govPrimaryWeight,
        fallbackWeight: 100 - govPrimaryWeight,
        circuitBreakerEnabled: govCircuitBreaker,
      })
      setGovernanceModalOpen(false)
      showToast(`已将【${govStrategyMode === 'FAILOVER' ? '主备降级' : govStrategyMode === 'WEIGHTED' ? '加权负载' : '严格限流'}】策略应用至模型 [${activeGovernanceConfig.modelName}]`)
    } catch (err: any) {
      showToast(`保存治理配置失败: ${err?.message || '服务器未响应'}`)
    }
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
      showToast(`保存模型配置 [${created.modelName}] 成功！`)
    } catch (err: any) {
      showToast(`创建失败: ${err?.message || '服务器未响应'}`)
    }
  }

  const handleTestConnectionReal = async (configId: number, modelName: string) => {
    setTestingConfigId(configId)
    try {
      const res = await testModelConfig(configId)
      if (res?.success) {
        showToast(`探针测试: [${modelName}] 连通物理成功！`)
      } else {
        showToast(`测试反馈: ${res?.message || '连接失败'}`)
      }
    } catch (err: any) {
      showToast(`测试失败: ${err?.message || '物理端口拒绝'}`)
    } finally {
      setTestingConfigId(null)
    }
  }

  const handleDeleteConfigReal = async (configId: number, modelName: string) => {
    try {
      await deleteModelConfig(configId)
      setRealConfigs((prev) => prev.filter((c) => c.configId !== configId))
      showToast(`已成功删除模型配置 [${modelName}]`)
    } catch (err: any) {
      showToast(`删除失败: ${err?.message}`)
    }
  }

  if (loading) {
    return (
      <div className="flex h-full min-h-0 flex-1 items-center justify-center bg-[#f8f9fc]">
        <div className="flex flex-col items-center gap-3 text-slate-500">
          <Loader2 className="size-8 animate-spin text-[#6f62e8]" />
          <p className="text-xs font-semibold">正在从后端加载真实模型配置与厂商目录…</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col overflow-y-auto bg-[#f8f9fc] text-slate-800">
      {/* 顶部 Header & 全局系统默认模型设置 */}
      <header className="border-b border-[#e8ebf1] bg-white px-6 py-5 sm:px-8">
        <div className="mx-auto max-w-[1500px]">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="flex size-9 items-center justify-center rounded-xl bg-[#eeecff] text-[#6f62e8]">
                <Cpu className="size-5" />
              </span>
              <div>
                <h1 className="text-xl font-bold tracking-tight text-slate-900">模型供应商与接入配置</h1>
                <p className="mt-0.5 text-xs text-slate-500">
                  真实接入后端 API 数据，管理模型连接凭据、脱敏密钥及路由调度。
                </p>
              </div>
            </div>

            <Button
              onClick={() => handleOpenCredentials(selectedProviderId)}
              className="gap-2 rounded-xl bg-[#6f62e8] text-xs font-semibold text-white shadow-sm hover:bg-[#5f52d9]"
            >
              <Key className="size-3.5" />
              配置当前供应商凭据
            </Button>
          </div>

          {/* 全局默认系统模型选择 */}
          <div className="mt-4 rounded-xl border border-purple-100 bg-gradient-to-r from-[#fbfaff] via-[#f8f6ff] to-[#f4f2ff] p-3.5 shadow-sm">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Sparkles className="size-4 text-[#6f62e8]" />
                <span className="text-xs font-bold text-slate-800">全站 RAG 默认模型调度 (System Defaults)</span>
              </div>
              <span className="text-[11px] text-[#7166f7]">控制全站问答与向量检索的默认引擎</span>
            </div>

            <div className="mt-2.5 grid grid-cols-1 gap-3 sm:grid-cols-3">
              <div className="flex items-center justify-between gap-2 rounded-lg border border-white bg-white/90 px-3 py-2 text-xs shadow-sm">
                <span className="font-bold text-purple-600 shrink-0">对话生成 (LLM)</span>
                <CustomSelect
                  value={defaultLLM}
                  onChange={setDefaultLLM}
                  options={LLM_OPTIONS}
                  className="w-48"
                />
              </div>

              <div className="flex items-center justify-between gap-2 rounded-lg border border-white bg-white/90 px-3 py-2 text-xs shadow-sm">
                <span className="font-bold text-emerald-600 shrink-0">向量检索 (Embedding)</span>
                <CustomSelect
                  value={defaultEmbedding}
                  onChange={setDefaultEmbedding}
                  options={EMBEDDING_OPTIONS}
                  className="w-52"
                />
              </div>

              <div className="flex items-center justify-between gap-2 rounded-lg border border-white bg-white/90 px-3 py-2 text-xs shadow-sm">
                <span className="font-bold text-amber-600 shrink-0">精细重排 (Rerank)</span>
                <CustomSelect
                  value={defaultRerank}
                  onChange={setDefaultRerank}
                  options={RERANK_OPTIONS}
                  className="w-48"
                />
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* 主体核心： Split 布局 */}
      <main className="mx-auto w-full max-w-[1500px] min-w-0 flex-1 px-6 py-6 sm:px-8">
        <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[300px_minmax(0,1fr)]">
          {/* 左侧供应商侧边菜单 */}
          <aside className="rounded-2xl border border-slate-200/80 bg-white p-4 shadow-sm">
            <div className="space-y-3">
              <div className="relative">
                <Search className="absolute left-2.5 top-2.5 size-3.5 text-slate-400" />
                <input
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  placeholder="搜索供应商或模型…"
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 py-1.5 pl-8 pr-3 text-xs text-slate-700 outline-none placeholder:text-slate-400 focus:border-[#6f62e8] focus:bg-white"
                />
              </div>

              <div className="flex flex-wrap gap-1 border-b border-slate-100 pb-3">
                {[
                  { id: 'all', label: '全部' },
                  { id: 'joined', label: '已配置' },
                ].map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => setCategoryFilter(item.id as any)}
                    className={cn(
                      'rounded-lg px-2.5 py-1 text-[11px] font-medium transition-all',
                      categoryFilter === item.id
                        ? 'bg-[#eeecff] font-bold text-[#6f62e8]'
                        : 'text-slate-500 hover:bg-slate-100',
                    )}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>

            {/* 供应商列表 */}
            <div className="mt-3 max-h-[calc(100vh-360px)] overflow-y-auto space-y-1 pr-1">
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
                        'group flex cursor-pointer items-center justify-between rounded-xl px-3 py-2.5 transition-all',
                        isSelected
                          ? 'bg-[#eeecff] text-[#5649ce] shadow-sm font-semibold'
                          : 'text-slate-700 hover:bg-slate-100/80',
                      )}
                    >
                      <div className="flex items-center gap-2.5 min-w-0">
                        <div className="flex size-7 shrink-0 items-center justify-center rounded-xl bg-white p-1 shadow-xs ring-1 ring-slate-100">
                          <img
                            src={getProviderLogoUrl(provider.provider)}
                            alt={displayName}
                            className="size-full object-contain"
                          />
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-xs font-bold">{displayName}</p>
                          <p className="truncate text-[10px] opacity-75">{providerConfigs.length} 项已接入配置</p>
                        </div>
                      </div>

                      <Tooltip>
                        <TooltipTrigger asChild>
                          <span
                            className={cn(
                              'size-2.5 shrink-0 rounded-full transition-transform group-hover:scale-125',
                              hasJoined ? 'bg-emerald-500 ring-2 ring-emerald-100' : 'bg-slate-300',
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

          {/* 右侧选中的供应商详情 & 真实数据表格 */}
          <section className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm">
            {/* 供应商 Detail 顶栏 */}
            {selectedCatalog && (
              <>
                <div className="flex flex-col gap-4 border-b border-slate-100 pb-5 sm:flex-row sm:items-center sm:justify-between">
                  <div className="flex items-center gap-4">
                    <div className="flex size-12 items-center justify-center rounded-2xl bg-white p-2 shadow-md ring-1 ring-slate-100">
                      <img
                        src={getProviderLogoUrl(selectedCatalog.provider)}
                        alt={selectedCatalog.displayName || selectedCatalog.provider}
                        className="size-full object-contain"
                      />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <h2 className="text-lg font-bold text-slate-900">
                          {selectedCatalog.displayName || selectedCatalog.provider}
                        </h2>
                        <span
                          className={cn(
                            'rounded-md px-2 py-0.5 text-[10px] font-bold',
                            currentProviderConfigs.length > 0
                              ? 'bg-emerald-50 text-emerald-600'
                              : 'bg-slate-100 text-slate-500',
                          )}
                        >
                          {currentProviderConfigs.length > 0 ? '凭据与模型已生效' : '未配置模型'}
                        </span>
                      </div>
                      <p className="mt-1 text-xs text-slate-500">
                        {selectedCatalog.defaultGovernanceDescription || '统一模型连接凭据与服务质量 Governance 治理。'}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => handleOpenCredentials(selectedCatalog.provider)}
                      className="gap-1.5 rounded-xl border-slate-200 text-xs font-semibold"
                    >
                      <Wrench className="size-3.5 text-slate-500" />
                      管理 API 凭据
                    </Button>
                    <Button
                      size="sm"
                      onClick={() => handleOpenAddModel()}
                      className="gap-1.5 rounded-xl bg-[#6f62e8] text-xs font-semibold text-white hover:bg-[#5f52d9]"
                    >
                      <Plus className="size-3.5" />
                      增加自定义模型
                    </Button>
                  </div>
                </div>

                {/* 推荐的模型快捷配置区 */}
                {recommendedModelsList.length > 0 && (
                  <div className="mt-4 rounded-xl border border-blue-100 bg-[#f4f8ff] p-3.5">
                    <div className="flex items-center justify-between">
                      <p className="flex items-center gap-1.5 text-xs font-bold text-blue-900">
                        <Sparkles className="size-3.5 text-blue-600" />
                        【后端推荐预设模型 (Catalog Presets)】一键快捷接入：
                      </p>
                      <span className="text-[11px] text-blue-600">后端推荐规则</span>
                    </div>

                    <div className="mt-2.5 flex flex-wrap gap-2">
                      {recommendedModelsList.map((rec) => {
                        const isAlreadyConfigured = currentProviderConfigs.some((c) => c.modelName === rec.name)
                        return (
                          <div
                            key={rec.name}
                            className="flex items-center gap-2 rounded-lg border border-white bg-white/90 px-3 py-1.5 shadow-xs"
                          >
                            <div>
                              <span className="font-mono text-xs font-bold text-slate-800">{rec.name}</span>
                              <span className="ml-1.5 rounded-md bg-purple-50 px-1.5 py-0.5 text-[9px] font-bold text-[#6f62e8]">
                                {rec.type}
                              </span>
                            </div>
                            <Button
                              size="sm"
                              variant={isAlreadyConfigured ? 'ghost' : 'default'}
                              disabled={isAlreadyConfigured}
                              onClick={() => handleOpenAddModel(rec.name, rec.type)}
                              className={cn(
                                'h-6 rounded-md px-2 text-[10px] font-semibold',
                                isAlreadyConfigured
                                  ? 'text-emerald-600 bg-emerald-50'
                                  : 'bg-[#6f62e8] text-white hover:bg-[#5f52d9]',
                              )}
                            >
                              {isAlreadyConfigured ? '已接入' : '一键接入'}
                            </Button>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                )}

                {/* 当前供应商的密钥信息 Bar */}
                <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-[#f8f8fc] px-4 py-2.5 text-xs text-slate-600">
                  <div className="flex items-center gap-4">
                    <span className="flex items-center gap-1.5 font-medium">
                      <Link2 className="size-3.5 text-[#6f62e8]" />
                      Base URL: <span className="font-mono text-slate-800">{selectedCatalog.defaultBaseUrl}</span>
                      <Copy
                        className="size-3 cursor-pointer text-slate-400 hover:text-[#6f62e8]"
                        onClick={() => handleCopyText(selectedCatalog.defaultBaseUrl, 'Base URL 端点')}
                        title="复制端点"
                      />
                    </span>
                    <span className="flex items-center gap-1.5 font-medium">
                      <Key className="size-3.5 text-slate-400" />
                      凭据 Key: <span className="font-mono text-slate-800">{currentProviderConfigs[0]?.apiKeyMask || currentProviderConfigs[0]?.apiKeyMasked || 'sk-****default'}</span>
                      {currentProviderConfigs[0] && (
                        <Copy
                          className="size-3 cursor-pointer text-slate-400 hover:text-[#6f62e8]"
                          onClick={() => handleCopyText(currentProviderConfigs[0]?.apiKeyMask || currentProviderConfigs[0]?.apiKeyMasked || '', '脱敏密钥')}
                          title="复制脱敏密钥"
                        />
                      )}
                    </span>
                  </div>
                  <span className="text-[11px] text-slate-400">数据库已存储配置 {currentProviderConfigs.length} 项</span>
                </div>

                {/* 真实数据模型列表表格 */}
                <div className="mt-5 overflow-hidden rounded-xl border border-slate-200">
                  <table className="w-full text-left text-xs">
                    <thead className="border-b border-slate-200 bg-[#fafafc] text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                      <tr>
                        <th className="px-4 py-3">模型标识 (MODEL NAME)</th>
                        <th className="px-4 py-3">类型 (TYPE)</th>
                        <th className="px-4 py-3">端点 (BASE URL)</th>
                        <th className="px-4 py-3">脱敏密钥</th>
                        <th className="px-4 py-3 text-right">操作与治理</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 bg-white">
                      {currentProviderConfigs.length === 0 ? (
                        <tr>
                          <td colSpan={5} className="px-4 py-10 text-center text-slate-400">
                            <Database className="mx-auto size-8 text-slate-300" />
                            <p className="mt-2 text-xs">数据库中暂无该供应商的已注册模型，可点击上方的【一键接入推荐模型】或【增加自定义模型】。</p>
                          </td>
                        </tr>
                      ) : (
                        currentProviderConfigs.map((config) => {
                          const maskKey = config.apiKeyMask || config.apiKeyMasked || 'sk-****default'
                          return (
                            <tr key={config.configId} className="transition-colors hover:bg-slate-50/80">
                              <td className="px-4 py-3 font-mono font-bold text-slate-800">
                                <div className="flex items-center gap-2">
                                  <span>{config.modelName}</span>
                                  <Copy
                                    className="size-3 cursor-pointer text-slate-300 transition-colors hover:text-[#6f62e8]"
                                    onClick={() => handleCopyText(config.modelName, '模型名称')}
                                    title="复制模型标识"
                                  />
                                </div>
                              </td>
                              <td className="px-4 py-3">
                                <span
                                  className={cn(
                                    'rounded-md px-2 py-0.5 text-[10px] font-bold',
                                    config.modelType === 'Chat' || config.modelType === 'LLM'
                                      ? 'bg-purple-50 text-[#6f62e8]'
                                      : config.modelType === 'Embedding' || config.modelType === 'Text Embedding'
                                        ? 'bg-emerald-50 text-emerald-600'
                                        : 'bg-amber-50 text-amber-600',
                                  )}
                                >
                                  {config.modelType}
                                </span>
                              </td>
                              <td className="max-w-[240px] truncate px-4 py-3 font-mono text-slate-600" title={config.baseUrl}>
                                <div className="flex items-center gap-1.5">
                                  <span className="truncate">{config.baseUrl}</span>
                                  <Copy
                                    className="size-3 shrink-0 cursor-pointer text-slate-300 transition-colors hover:text-[#6f62e8]"
                                    onClick={() => handleCopyText(config.baseUrl, 'Base URL 端点')}
                                    title="复制端点"
                                  />
                                </div>
                              </td>
                              <td className="px-4 py-3 font-mono text-slate-600">
                                <div className="flex items-center gap-1.5">
                                  <span>{maskKey}</span>
                                  <Copy
                                    className="size-3 cursor-pointer text-slate-300 transition-colors hover:text-[#6f62e8]"
                                    onClick={() => handleCopyText(maskKey, '脱敏密钥')}
                                    title="复制脱敏密钥"
                                  />
                                </div>
                              </td>
                              <td className="px-4 py-3 text-right">
                                <div className="flex items-center justify-end gap-2">
                                  <button
                                    type="button"
                                    onClick={() => handleOpenGovernanceModal(config)}
                                    className="flex items-center gap-1 rounded-lg bg-purple-50 px-2 py-1 text-[11px] font-semibold text-[#6f62e8] transition-colors hover:bg-purple-100"
                                    title="设置主备降级、加权负载均衡与限流"
                                  >
                                    <SlidersHorizontal className="size-3" />
                                    治理设置
                                  </button>

                                  <button
                                    type="button"
                                    disabled={testingConfigId === config.configId}
                                    onClick={() => handleTestConnectionReal(config.configId, config.modelName)}
                                    className="flex items-center gap-1 font-semibold text-slate-600 hover:text-[#6f62e8] hover:underline"
                                  >
                                    <RefreshCw className={cn('size-3', testingConfigId === config.configId && 'animate-spin')} />
                                    {testingConfigId === config.configId ? '探针测试' : '测试连接'}
                                  </button>

                                  <button
                                    type="button"
                                    onClick={() => handleDeleteConfigReal(config.configId, config.modelName)}
                                    className="text-slate-400 hover:text-red-600"
                                    title="移除此配置"
                                  >
                                    <Trash2 className="size-3.5" />
                                  </button>
                                </div>
                              </td>
                            </tr>
                          )
                        })
                      )}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </section>
        </div>
      </main>

      {/* 支持【主备降级】与【加权负载均衡】的多模式模型治理 Modal */}
      <Dialog open={governanceModalOpen} onOpenChange={setGovernanceModalOpen}>
        <DialogContent className="max-w-xl rounded-2xl bg-white p-6 shadow-2xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base font-bold text-slate-900">
              <SlidersHorizontal className="size-4 text-[#6f62e8]" />
              模型治理与路由策略 (Governance & Routing) - [{activeGovernanceConfig?.modelName}]
            </DialogTitle>
            <DialogDescription className="text-xs text-slate-500">
              由用户自定义配置【主备故障转移】、【加权负载均衡】或【严格限流保护】模式。
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSaveGovernanceSubmit} className="mt-4 space-y-4">
            {/* 治理模式切换三选一 Card Radio */}
            <div>
              <label className="mb-2 block text-xs font-bold text-slate-800">1. 选择核心治理与路由模式 (Strategy Mode)</label>
              <div className="grid grid-cols-3 gap-2.5">
                {/* 模式 A: 主备降级 */}
                <div
                  onClick={() => setGovStrategyMode('FAILOVER')}
                  className={cn(
                    'flex cursor-pointer flex-col justify-between rounded-xl border p-3 transition-all',
                    govStrategyMode === 'FAILOVER'
                      ? 'border-[#6f62e8] bg-[#f5f3ff] ring-2 ring-[#eeecff]'
                      : 'border-slate-200 bg-white hover:border-slate-300',
                  )}
                >
                  <div className="flex items-center gap-1.5 font-bold text-xs text-slate-800">
                    <ArrowRightLeft className="size-3.5 text-[#6f62e8]" />
                    主备降级 (Failover)
                  </div>
                  <p className="mt-1 text-[10px] text-slate-500">主模型超时或报错时，秒级自动降级至备用模型。</p>
                </div>

                {/* 模式 B: 加权负载 */}
                <div
                  onClick={() => setGovStrategyMode('WEIGHTED')}
                  className={cn(
                    'flex cursor-pointer flex-col justify-between rounded-xl border p-3 transition-all',
                    govStrategyMode === 'WEIGHTED'
                      ? 'border-[#6f62e8] bg-[#f5f3ff] ring-2 ring-[#eeecff]'
                      : 'border-slate-200 bg-white hover:border-slate-300',
                  )}
                >
                  <div className="flex items-center gap-1.5 font-bold text-xs text-slate-800">
                    <Scale className="size-3.5 text-purple-600" />
                    加权负载 (Weighted)
                  </div>
                  <p className="mt-1 text-[10px] text-slate-500">按设定的权重比例（如 70%:30%）分发全站流量。</p>
                </div>

                {/* 模式 C: 严格限流保护 */}
                <div
                  onClick={() => setGovStrategyMode('PROTECTION')}
                  className={cn(
                    'flex cursor-pointer flex-col justify-between rounded-xl border p-3 transition-all',
                    govStrategyMode === 'PROTECTION'
                      ? 'border-[#6f62e8] bg-[#f5f3ff] ring-2 ring-[#eeecff]'
                      : 'border-slate-200 bg-white hover:border-slate-300',
                  )}
                >
                  <div className="flex items-center gap-1.5 font-bold text-xs text-slate-800">
                    <Shield className="size-3.5 text-emerald-600" />
                    严格限流 (Protected)
                  </div>
                  <p className="mt-1 text-[10px] text-slate-500">对并发与 RPM 限制，防止调用超出 API 配额。</p>
                </div>
              </div>
            </div>

            {/* 模式特定配置面板 */}
            {govStrategyMode === 'FAILOVER' && (
              <div className="rounded-xl border border-purple-100 bg-[#f7f5ff] p-3.5 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-purple-900">🛡️ 主备故障转移降级配置</span>
                  <button
                    type="button"
                    onClick={() => setGovCircuitBreaker(!govCircuitBreaker)}
                    className="flex items-center gap-1 text-xs text-[#6f62e8]"
                  >
                    {govCircuitBreaker ? <ToggleRight className="size-5 text-emerald-600" /> : <ToggleLeft className="size-5 text-slate-300" />}
                    <span className="text-[11px]">{govCircuitBreaker ? '自动熔断已开启' : '熔断已关闭'}</span>
                  </button>
                </div>

                <div>
                  <label className="mb-1 block text-[11px] text-slate-600">主模型发生异常时自动降级的备用模型 (Fallback Model)</label>
                  <CustomSelect
                    value={govFallbackModel}
                    onChange={setGovFallbackModel}
                    options={LLM_OPTIONS}
                  />
                </div>
              </div>
            )}

            {govStrategyMode === 'WEIGHTED' && (
              <div className="rounded-xl border border-blue-100 bg-[#f4f8ff] p-3.5 space-y-3">
                <span className="text-xs font-bold text-blue-900">⚖️ 流量加权轮询权重比例设置</span>

                <div className="space-y-2">
                  <div className="flex justify-between text-xs text-slate-700">
                    <span>主节点 [{activeGovernanceConfig?.modelName}] 权重: <strong className="text-[#6f62e8]">{govPrimaryWeight}%</strong></span>
                    <span>备用节点 [{govFallbackModel}] 权重: <strong className="text-blue-600">{100 - govPrimaryWeight}%</strong></span>
                  </div>
                  <input
                    type="range"
                    min={10}
                    max={90}
                    step={5}
                    value={govPrimaryWeight}
                    onChange={(e) => setGovPrimaryWeight(Number(e.target.value))}
                    className="w-full accent-[#6f62e8]"
                  />
                </div>

                <div>
                  <label className="mb-1 block text-[11px] text-slate-600">关联负载的次级并发模型</label>
                  <CustomSelect
                    value={govFallbackModel}
                    onChange={setGovFallbackModel}
                    options={LLM_OPTIONS}
                  />
                </div>
              </div>
            )}

            {/* 通用 QoS 参数：超时、重试、RPM */}
            <div className="rounded-xl border border-slate-100 bg-[#f8f8fc] p-3.5">
              <p className="text-xs font-bold text-slate-800">2. 服务质量 QoS 与速率阈值限制 (QoS Limits)</p>
              <div className="mt-2.5 grid grid-cols-3 gap-3">
                <div>
                  <label className="mb-1 block text-[11px] text-slate-500">超时时间 (秒)</label>
                  <input
                    type="number"
                    min={1}
                    value={govTimeoutSec}
                    onChange={(e) => setGovTimeoutSec(Number(e.target.value))}
                    className="w-full rounded-xl border border-slate-200 bg-white px-3 py-1 text-xs text-slate-800 outline-none focus:border-[#6f62e8]"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-[11px] text-slate-500">重试上限 (Retries)</label>
                  <input
                    type="number"
                    min={0}
                    value={govMaxRetries}
                    onChange={(e) => setGovMaxRetries(Number(e.target.value))}
                    className="w-full rounded-xl border border-slate-200 bg-white px-3 py-1 text-xs text-slate-800 outline-none focus:border-[#6f62e8]"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-[11px] text-slate-500">每分钟 RPM 限额</label>
                  <input
                    type="number"
                    min={1}
                    value={govRateLimitRpm}
                    onChange={(e) => setGovRateLimitRpm(Number(e.target.value))}
                    className="w-full rounded-xl border border-slate-200 bg-white px-3 py-1 text-xs text-slate-800 outline-none focus:border-[#6f62e8]"
                  />
                </div>
              </div>
            </div>

            <div className="mt-6 flex justify-end gap-2.5 pt-2">
              <Button type="button" variant="outline" size="sm" onClick={() => setGovernanceModalOpen(false)}>
                取消
              </Button>
              <Button type="submit" size="sm" className="bg-[#6f62e8] text-white hover:bg-[#5f52d9]">
                应用此治理策略
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* 凭据 Modal */}
      <Dialog open={credentialsModalOpen} onOpenChange={setCredentialsModalOpen}>
        <DialogContent className="max-w-lg rounded-2xl bg-white p-6 shadow-2xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2.5 text-base font-bold text-slate-900">
              <span className="flex size-7 items-center justify-center rounded-xl bg-white p-1 shadow-xs ring-1 ring-slate-100">
                <img
                  src={getProviderLogoUrl(selectedProviderId)}
                  alt={selectedCatalog?.displayName}
                  className="size-full object-contain"
                />
              </span>
              配置 {selectedCatalog?.displayName || selectedCatalog?.provider} 凭据
            </DialogTitle>
            <DialogDescription className="text-xs text-slate-500">
              添加密钥并调用后端真实 REST API 存入数据库。
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="mt-4 space-y-4">
            <div>
              <label className="mb-1 block text-xs font-semibold text-slate-700">配置激活的模型名称 (Model Name)</label>
              <input
                required
                value={newModelName}
                onChange={(e) => setNewModelName(e.target.value)}
                placeholder="例如: qwen-plus, gpt-4o 等"
                className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-mono text-slate-800 outline-none focus:border-[#6f62e8]"
              />
            </div>

            <div>
              <label className="mb-1 block text-xs font-semibold text-slate-700">模型类型</label>
              <CustomSelect
                value={newModelCategory}
                onChange={(v) => setNewModelCategory(v as any)}
                options={CATEGORY_OPTIONS}
              />
            </div>

            <div>
              <label className="mb-1 block text-xs font-semibold text-slate-700">API Key 密钥</label>
              <div className="relative">
                <input
                  type={showApiKey ? 'text' : 'password'}
                  required
                  value={formApiKey}
                  onChange={(e) => setFormApiKey(e.target.value)}
                  placeholder="sk-..."
                  className="w-full rounded-xl border border-slate-200 bg-white py-2 pl-3 pr-10 text-xs font-mono text-slate-800 outline-none focus:border-[#6f62e8]"
                />
                <button
                  type="button"
                  onClick={() => setShowApiKey(!showApiKey)}
                  className="absolute right-3 top-2.5 text-slate-400 hover:text-slate-600"
                >
                  {showApiKey ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
            </div>

            <div>
              <label className="mb-1 block text-xs font-semibold text-slate-700">API Base URL 端点</label>
              <input
                value={formBaseUrl}
                onChange={(e) => setFormBaseUrl(e.target.value)}
                placeholder={selectedCatalog?.defaultBaseUrl}
                className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-mono text-slate-800 outline-none focus:border-[#6f62e8]"
              />
            </div>

            <div className="mt-6 flex justify-end gap-2.5">
              <Button type="button" variant="outline" size="sm" onClick={() => setCredentialsModalOpen(false)}>
                取消
              </Button>
              <Button type="submit" size="sm" className="bg-[#6f62e8] text-white hover:bg-[#5f52d9]">
                提交至后端生效
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* 添加新模型 Modal */}
      <Dialog open={addModelModalOpen} onOpenChange={setAddModelModalOpen}>
        <DialogContent className="max-w-md rounded-2xl bg-white p-6 shadow-2xl">
          <DialogHeader>
            <DialogTitle className="text-base font-bold text-slate-900">
              在 [{selectedCatalog?.displayName || selectedCatalog?.provider}] 下定义新模型
            </DialogTitle>
            <DialogDescription className="text-xs text-slate-500">
              配置新模型的类型与标识，保存后可立即在全站路由调用。
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="mt-4 space-y-4">
            <div>
              <label className="mb-1 block text-xs font-semibold text-slate-700">模型类别 (Category)</label>
              <CustomSelect
                value={newModelCategory}
                onChange={(v) => setNewModelCategory(v as any)}
                options={CATEGORY_OPTIONS}
              />
            </div>

            <div>
              <label className="mb-1 block text-xs font-semibold text-slate-700">模型标识名称 (Model ID)</label>
              <input
                required
                value={newModelName}
                onChange={(e) => setNewModelName(e.target.value)}
                placeholder="例如: qwen-turbo, gpt-4o-mini 等"
                className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-mono text-slate-800 outline-none focus:border-[#6f62e8]"
              />
            </div>

            <div>
              <label className="mb-1 block text-xs font-semibold text-slate-700">API Key 密钥</label>
              <input
                type="password"
                required
                value={formApiKey}
                onChange={(e) => setFormApiKey(e.target.value)}
                placeholder="sk-..."
                className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-mono text-slate-800 outline-none focus:border-[#6f62e8]"
              />
            </div>

            <div className="mt-6 flex justify-end gap-2.5">
              <Button type="button" variant="outline" size="sm" onClick={() => setAddModelModalOpen(false)}>
                取消
              </Button>
              <Button type="submit" size="sm" className="bg-[#6f62e8] text-white hover:bg-[#5f52d9]">
                添加真实配置
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* 底部 Toast */}
      {toastMessage && (
        <div className="fixed bottom-6 right-8 z-50 animate-in fade-in slide-in-from-bottom-2">
          <div className="flex items-center gap-2 rounded-xl bg-slate-900 px-4 py-2.5 text-xs font-semibold text-white shadow-2xl">
            <CheckCircle2 className="size-4 text-emerald-400" />
            {toastMessage}
          </div>
        </div>
      )}
    </div>
  )
}

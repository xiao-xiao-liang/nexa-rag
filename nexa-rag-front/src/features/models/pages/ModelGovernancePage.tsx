import { useEffect, useState } from 'react'
import {
  Activity, CheckCircle2, Cpu, Loader2, RefreshCw, ShieldCheck,
  ShieldAlert, SlidersHorizontal, ToggleLeft, ToggleRight, Wrench, RotateCcw
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { cn } from '@/lib/utils'
import {
  getModelConfigs,
  listModelGovernanceConfigs,
  resetModelGovernanceDefault,
  refreshModelRegistry,
  getModelGovernanceConfig,
  updateModelGovernanceConfig,
  type ModelConfigItem,
  type ModelGovernanceConfigDTO,
} from '../api/model-api'

export default function ModelGovernancePage() {
  const [governanceList, setGovernanceList] = useState<ModelGovernanceConfigDTO[]>([])
  const [modelConfigs, setModelConfigs] = useState<ModelConfigItem[]>([])
  const [loading, setLoading] = useState(true)
  const [toastMessage, setToastMessage] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)

  // 治理编辑 Modal 控制
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [currentConfigId, setCurrentConfigId] = useState<number | null>(null)
  const [currentGovernanceId, setCurrentGovernanceId] = useState<number | null>(null)
  const [targetModelName, setTargetModelName] = useState<string>('')

  // 20+ 精细防护表单 State
  const [govBindingMode, setGovBindingMode] = useState<'CONFIG' | 'ROUTE'>('CONFIG')
  const [govEnabled, setGovEnabled] = useState(true)
  const [govRetryEnabled, setGovRetryEnabled] = useState(true)
  const [govMaxAttempts, setGovMaxAttempts] = useState(3)
  const [govRetryWaitMs, setGovRetryWaitMs] = useState(1000)
  const [govCircuitEnabled, setGovCircuitEnabled] = useState(true)
  const [govFailureRateThreshold, setGovFailureRateThreshold] = useState(50)
  const [govSlowCallDurationMs, setGovSlowCallDurationMs] = useState(3000)
  const [govRateLimitEnabled, setGovRateLimitEnabled] = useState(true)
  const [govLimitForPeriod, setGovLimitForPeriod] = useState(120)
  const [govStreamFirstChunkTimeoutMs, setGovStreamFirstChunkTimeoutMs] = useState(30000)
  const [govStreamMaxDurationMs, setGovStreamMaxDurationMs] = useState(300000)
  const [govMaxConcurrentCalls, setGovMaxConcurrentCalls] = useState(20)

  const showToast = (msg: string) => {
    setToastMessage(msg)
    setTimeout(() => setToastMessage(null), 2500)
  }

  const loadData = async () => {
    setLoading(true)
    try {
      const [govRes, configsRes] = await Promise.all([
        listModelGovernanceConfigs().catch(() => []),
        getModelConfigs().catch(() => []),
      ])
      setGovernanceList(govRes || [])
      setModelConfigs(configsRes || [])
    } catch (err: any) {
      console.error('加载模型治理配置失败:', err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [])

  // 手动广播刷新 JVM 注册表快照
  const handleRefreshRegistry = async () => {
    setRefreshing(true)
    try {
      const res = await refreshModelRegistry()
      showToast(res?.message || '已向 JVM 实例成功广播刷新模型注册表快照！')
      void loadData()
    } catch (err: any) {
      showToast(`广播刷新失败: ${err?.message || '网络连接拒绝'}`)
    } finally {
      setRefreshing(false)
    }
  }

  // 打开编辑指定模型节点治理参数 Modal
  const handleOpenEdit = async (configId: number, modelName: string, governanceId?: number) => {
    setCurrentConfigId(configId)
    setCurrentGovernanceId(governanceId || null)
    setTargetModelName(modelName)
    setEditModalOpen(true)
    try {
      const gov = await getModelGovernanceConfig(configId)
      if (gov) {
        setGovBindingMode(gov.bindingMode || 'CONFIG')
        setGovEnabled(gov.enabled !== false)
        setGovRetryEnabled(gov.retryEnabled !== false)
        setGovMaxAttempts(gov.maxAttempts || 3)
        setGovRetryWaitMs(gov.retryWaitMs || 1000)
        setGovCircuitEnabled(gov.circuitEnabled !== false)
        setGovFailureRateThreshold(gov.failureRateThreshold || 50)
        setGovSlowCallDurationMs(gov.slowCallDurationMs || 3000)
        setGovRateLimitEnabled(gov.rateLimitEnabled !== false)
        setGovLimitForPeriod(gov.limitForPeriod || 120)
        setGovStreamFirstChunkTimeoutMs(gov.streamFirstChunkTimeoutMs || 30000)
        setGovStreamMaxDurationMs(gov.streamMaxDurationMs || 300000)
        setGovMaxConcurrentCalls(gov.maxConcurrentCalls || 20)
      }
    } catch (e) {
      console.warn('获取目标模型治理配置:', e)
    }
  }

  // 提交单独治理配置更新
  const handleSaveGovernance = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!currentConfigId) return
    try {
      await updateModelGovernanceConfig(currentConfigId, {
        configId: currentConfigId,
        bindingMode: govBindingMode,
        enabled: govEnabled,
        retryEnabled: govRetryEnabled,
        maxAttempts: govMaxAttempts,
        retryWaitMs: govRetryWaitMs,
        circuitEnabled: govCircuitEnabled,
        failureRateThreshold: govFailureRateThreshold,
        slowCallRateThreshold: 100,
        slowCallDurationMs: govSlowCallDurationMs,
        rateLimitEnabled: govRateLimitEnabled,
        limitForPeriod: govLimitForPeriod,
        streamFirstChunkTimeoutMs: govStreamFirstChunkTimeoutMs,
        streamMaxDurationMs: govStreamMaxDurationMs,
        maxConcurrentCalls: govMaxConcurrentCalls,
      })
      setEditModalOpen(false)
      showToast(`已成功保存 [${targetModelName}] 的精细防护治理参数！`)
      void loadData()
    } catch (err: any) {
      showToast(`保存失败: ${err?.message || '服务器未响应'}`)
    }
  }

  // 一键重置为系统默认值
  const handleResetDefault = async (governanceId: number, name: string) => {
    try {
      await resetModelGovernanceDefault(governanceId)
      showToast(`已成功重置节点 [${name}] 的治理配置为系统默认规则！`)
      void loadData()
    } catch (err: any) {
      showToast(`重置失败: ${err?.message || '服务器拒绝'}`)
    }
  }

  if (loading) {
    return (
      <div className="flex h-full min-h-0 flex-1 items-center justify-center bg-[#f8f9fc]">
        <div className="flex flex-col items-center gap-3 text-slate-500">
          <Loader2 className="size-8 animate-spin text-[#6f62e8]" />
          <p className="text-xs font-semibold">正在从后端加载全站模型治理与 Resilience4j 配置…</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col overflow-y-auto bg-[#f8f9fc] text-slate-800">
      {/* 顶部 Header：独立模型治理页面控制台 */}
      <header className="border-b border-[#e8ebf1] bg-white px-6 py-4 sm:px-8">
        <div className="mx-auto max-w-[1500px]">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="flex size-9 items-center justify-center rounded-xl bg-[#eeecff] text-[#6f62e8]">
                <SlidersHorizontal className="size-5" />
              </span>
              <div>
                <h1 className="text-lg font-bold tracking-tight text-slate-900">模型运行治理控制台</h1>
                <p className="text-[11px] text-slate-500">
                  专注于 Resilience4j 物理防护：限流、熔断、慢调用隔离、自动重试与高并发容错控制。
                </p>
              </div>
            </div>

            <Button
              onClick={handleRefreshRegistry}
              disabled={refreshing}
              className="gap-2 rounded-xl bg-[#6f62e8] text-xs font-semibold text-white shadow-sm hover:bg-[#5f52d9]"
            >
              <RefreshCw className={cn('size-3.5', refreshing && 'animate-spin')} />
              {refreshing ? '正在广播…' : '广播刷新 JVM 注册表快照'}
            </Button>
          </div>

          {/* 全局防护指标卡片 */}
          <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-4">
            <div className="flex items-center gap-3 rounded-xl border border-purple-100 bg-[#faf9ff] p-3 shadow-2xs">
              <ShieldCheck className="size-6 text-[#6f62e8]" />
              <div>
                <p className="text-[10px] font-bold text-slate-400">活跃治理规则</p>
                <p className="text-sm font-bold text-slate-900">{modelConfigs.length} 项已配置节点防护</p>
              </div>
            </div>

            <div className="flex items-center gap-3 rounded-xl border border-blue-100 bg-[#f4f8ff] p-3 shadow-2xs">
              <Activity className="size-6 text-blue-600" />
              <div>
                <p className="text-[10px] font-bold text-slate-400">全局限流防护 (RateLimit)</p>
                <p className="text-sm font-bold text-slate-900">默认 120 RPM / 1000ms</p>
              </div>
            </div>

            <div className="flex items-center gap-3 rounded-xl border border-emerald-100 bg-[#f2fcf6] p-3 shadow-2xs">
              <ShieldAlert className="size-6 text-emerald-600" />
              <div>
                <p className="text-[10px] font-bold text-slate-400">熔断判定阈值 (CircuitBreaker)</p>
                <p className="text-sm font-bold text-slate-900">50% 失败率 / 3000ms 慢调用</p>
              </div>
            </div>

            <div className="flex items-center gap-3 rounded-xl border border-amber-100 bg-[#fffdf5] p-3 shadow-2xs">
              <Wrench className="size-6 text-amber-600" />
              <div>
                <p className="text-[10px] font-bold text-slate-400">绑定模式 (Binding Mode)</p>
                <p className="text-sm font-bold text-slate-900">CONFIG 物理节点独立生效</p>
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* 主体：全量模型治理列表 */}
      <main className="mx-auto w-full max-w-[1500px] min-w-0 flex-1 px-6 py-6 sm:px-8">
        <section className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm">
          <div className="flex items-center justify-between border-b border-slate-100 pb-4">
            <div>
              <h2 className="text-base font-bold text-slate-900">全站已接入模型节点治理规则列表</h2>
              <p className="text-xs text-slate-500">为每个具体物理模型配置独立的 20+ 项 Resilience4j 容错规则与保护策略。</p>
            </div>
          </div>

          <div className="mt-5 overflow-hidden rounded-xl border border-slate-200">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-slate-200 bg-[#fafafc] text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                <tr>
                  <th className="px-4 py-3">模型标识 (MODEL)</th>
                  <th className="px-4 py-3">绑定模式</th>
                  <th className="px-4 py-3">重试保护 (RETRY)</th>
                  <th className="px-4 py-3">单周期限流 (RPM)</th>
                  <th className="px-4 py-3">熔断器 (CIRCUIT)</th>
                  <th className="px-4 py-3 text-right">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {modelConfigs.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-10 text-center text-slate-400">
                      <p className="text-xs">暂无已配置的模型节点，请先在【模型配置】页面中接入物理厂商模型。</p>
                    </td>
                  </tr>
                ) : (
                  modelConfigs.map((config) => {
                    const gov = governanceList.find((g) => g.configId === config.configId)
                    return (
                      <tr key={config.configId} className="transition-colors hover:bg-slate-50/80">
                        <td className="px-4 py-3 font-mono font-bold text-slate-800">
                          <div className="flex items-center gap-2">
                            <span>{config.modelName}</span>
                            <span className="rounded-md bg-purple-50 px-1.5 py-0.5 text-[9px] font-bold text-[#6f62e8]">
                              {config.provider}
                            </span>
                          </div>
                        </td>
                        <td className="px-4 py-3 font-mono text-slate-600">
                          <span className="rounded-md bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-700">
                            {gov?.bindingMode || 'CONFIG'}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={cn(
                              'rounded-md px-2 py-0.5 text-[10px] font-bold',
                              gov?.retryEnabled !== false
                                ? 'bg-emerald-50 text-emerald-600'
                                : 'bg-slate-100 text-slate-400',
                            )}
                          >
                            {gov?.retryEnabled !== false ? `已开启 (${gov?.maxAttempts || 3} 次)` : '已关闭'}
                          </span>
                        </td>
                        <td className="px-4 py-3 font-mono text-slate-700">
                          {gov?.rateLimitEnabled !== false ? `${gov?.limitForPeriod || 120} 次 / 周期` : '未限制'}
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={cn(
                              'rounded-md px-2 py-0.5 text-[10px] font-bold',
                              gov?.circuitEnabled !== false
                                ? 'bg-purple-50 text-[#6f62e8]'
                                : 'bg-slate-100 text-slate-400',
                            )}
                          >
                            {gov?.circuitEnabled !== false ? `阈值 ${gov?.failureRateThreshold || 50}%` : '未开启'}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right">
                          <div className="flex items-center justify-end gap-3">
                            <button
                              type="button"
                              onClick={() => handleOpenEdit(config.configId, config.modelName, gov?.governanceId)}
                              className="flex items-center gap-1 font-semibold text-[#6f62e8] hover:underline"
                            >
                              <Wrench className="size-3" />
                              精细配置
                            </button>

                            {gov?.governanceId && (
                              <button
                                type="button"
                                onClick={() => handleResetDefault(gov.governanceId!, config.modelName)}
                                className="flex items-center gap-1 text-slate-400 hover:text-amber-600"
                                title="重置为系统默认防护规则"
                              >
                                <RotateCcw className="size-3" />
                                重置默认
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>
        </section>
      </main>

      {/* 节点精细治理配置 Modal */}
      <Dialog open={editModalOpen} onOpenChange={setEditModalOpen}>
        <DialogContent className="max-w-xl rounded-2xl bg-white p-6 shadow-2xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base font-bold text-slate-900">
              <ShieldCheck className="size-4 text-[#6f62e8]" />
              精细节点保护配置: [{targetModelName}]
            </DialogTitle>
            <DialogDescription className="text-xs text-slate-500">
              配置 Resilience4j 重试、单周期限流配额、慢调用熔断与流式首包/耗时保护。
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSaveGovernance} className="mt-4 space-y-4">
            {/* 1. 自动重试 Retry */}
            <div className="rounded-xl border border-slate-200 bg-white p-3 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-800">🔄 自动重试保护 (Retry)</span>
                <button
                  type="button"
                  onClick={() => setGovRetryEnabled(!govRetryEnabled)}
                  className="flex items-center gap-1 text-xs text-[#6f62e8]"
                >
                  {govRetryEnabled ? <ToggleRight className="size-5 text-emerald-600" /> : <ToggleLeft className="size-5 text-slate-300" />}
                  <span className="text-[11px]">{govRetryEnabled ? '重试已开启' : '重试已关闭'}</span>
                </button>
              </div>
              {govRetryEnabled && (
                <div className="grid grid-cols-2 gap-3 pt-1">
                  <div>
                    <label className="mb-1 block text-[11px] text-slate-500">最大尝试次数 (Max Attempts)</label>
                    <input
                      type="number"
                      min={1}
                      max={10}
                      value={govMaxAttempts}
                      onChange={(e) => setGovMaxAttempts(Number(e.target.value))}
                      className="w-full rounded-xl border border-slate-200 px-3 py-1 text-xs outline-none focus:border-[#6f62e8]"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-[11px] text-slate-500">重试等待间隔 (ms)</label>
                    <input
                      type="number"
                      step={100}
                      value={govRetryWaitMs}
                      onChange={(e) => setGovRetryWaitMs(Number(e.target.value))}
                      className="w-full rounded-xl border border-slate-200 px-3 py-1 text-xs outline-none focus:border-[#6f62e8]"
                    />
                  </div>
                </div>
              )}
            </div>

            {/* 2. 单周期限流与并发隔离 */}
            <div className="rounded-xl border border-slate-200 bg-white p-3 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-800">🛡️ 单周期限流与最大并发隔离</span>
                <button
                  type="button"
                  onClick={() => setGovRateLimitEnabled(!govRateLimitEnabled)}
                  className="flex items-center gap-1 text-xs text-[#6f62e8]"
                >
                  {govRateLimitEnabled ? <ToggleRight className="size-5 text-emerald-600" /> : <ToggleLeft className="size-5 text-slate-300" />}
                  <span className="text-[11px]">{govRateLimitEnabled ? '限流已开启' : '限流已关闭'}</span>
                </button>
              </div>
              <div className="grid grid-cols-2 gap-3 pt-1">
                <div>
                  <label className="mb-1 block text-[11px] text-slate-500">单周期请求配额 (Limit for Period)</label>
                  <input
                    type="number"
                    min={1}
                    value={govLimitForPeriod}
                    onChange={(e) => setGovLimitForPeriod(Number(e.target.value))}
                    className="w-full rounded-xl border border-slate-200 px-3 py-1 text-xs outline-none focus:border-[#6f62e8]"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-[11px] text-slate-500">最大并发数 (Max Concurrent Calls)</label>
                  <input
                    type="number"
                    min={1}
                    value={govMaxConcurrentCalls}
                    onChange={(e) => setGovMaxConcurrentCalls(Number(e.target.value))}
                    className="w-full rounded-xl border border-slate-200 px-3 py-1 text-xs outline-none focus:border-[#6f62e8]"
                  />
                </div>
              </div>
            </div>

            {/* 3. 熔断与慢调用 */}
            <div className="rounded-xl border border-slate-200 bg-white p-3 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-800">⚡ 熔断器与慢调用阈值</span>
                <button
                  type="button"
                  onClick={() => setGovCircuitEnabled(!govCircuitEnabled)}
                  className="flex items-center gap-1 text-xs text-[#6f62e8]"
                >
                  {govCircuitEnabled ? <ToggleRight className="size-5 text-emerald-600" /> : <ToggleLeft className="size-5 text-slate-300" />}
                  <span className="text-[11px]">{govCircuitEnabled ? '熔断防护已开启' : '熔断已关闭'}</span>
                </button>
              </div>
              <div className="grid grid-cols-2 gap-3 pt-1">
                <div>
                  <label className="mb-1 block text-[11px] text-slate-500">失败率阈值 (%)</label>
                  <input
                    type="number"
                    min={10}
                    max={100}
                    value={govFailureRateThreshold}
                    onChange={(e) => setGovFailureRateThreshold(Number(e.target.value))}
                    className="w-full rounded-xl border border-slate-200 px-3 py-1 text-xs outline-none focus:border-[#6f62e8]"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-[11px] text-slate-500">慢调用判定耗时 (ms)</label>
                  <input
                    type="number"
                    step={500}
                    value={govSlowCallDurationMs}
                    onChange={(e) => setGovSlowCallDurationMs(Number(e.target.value))}
                    className="w-full rounded-xl border border-slate-200 px-3 py-1 text-xs outline-none focus:border-[#6f62e8]"
                  />
                </div>
              </div>
            </div>

            {/* 4. 流式超时 */}
            <div className="rounded-xl border border-slate-200 bg-white p-3 space-y-2">
              <span className="text-xs font-bold text-slate-800">⏱️ 流式 SSE 首包与总耗时超时控制</span>
              <div className="grid grid-cols-2 gap-3 pt-1">
                <div>
                  <label className="mb-1 block text-[11px] text-slate-500">流式首包超时时间 (ms)</label>
                  <input
                    type="number"
                    step={1000}
                    value={govStreamFirstChunkTimeoutMs}
                    onChange={(e) => setGovStreamFirstChunkTimeoutMs(Number(e.target.value))}
                    className="w-full rounded-xl border border-slate-200 px-3 py-1 text-xs outline-none focus:border-[#6f62e8]"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-[11px] text-slate-500">流式最大耗时限制 (ms)</label>
                  <input
                    type="number"
                    step={5000}
                    value={govStreamMaxDurationMs}
                    onChange={(e) => setGovStreamMaxDurationMs(Number(e.target.value))}
                    className="w-full rounded-xl border border-slate-200 px-3 py-1 text-xs outline-none focus:border-[#6f62e8]"
                  />
                </div>
              </div>
            </div>

            <div className="mt-6 flex justify-end gap-2.5">
              <Button type="button" variant="outline" size="sm" onClick={() => setEditModalOpen(false)}>
                取消
              </Button>
              <Button type="submit" size="sm" className="bg-[#6f62e8] text-white hover:bg-[#5f52d9]">
                保存物理防护策略
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

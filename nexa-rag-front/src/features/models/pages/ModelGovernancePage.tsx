import { useEffect, useState } from 'react'
import {
  Activity, Loader2, RefreshCw, ShieldCheck,
  ShieldAlert, SlidersHorizontal, ToggleLeft, ToggleRight, Wrench, RotateCcw
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tag } from '@/components/ui/tag'
import { Toast } from '@/components/ui/toast'
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
  const [currentConfigId, setCurrentConfigId] = useState<number | string | null>(null)
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
  const handleOpenEdit = async (configId: number | string, modelName: string) => {
    setCurrentConfigId(configId)
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
  const handleResetDefault = async (governanceId: number | string, name: string) => {
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
      <div className="flex h-full min-h-0 flex-1 items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-3 text-secondary">
          <Loader2 className="size-8 animate-spin text-primary" />
          <p className="text-xs font-medium">正在从后端加载全站模型治理与 Resilience4j 配置…</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col overflow-y-auto bg-background">
      {/* 顶部 Header：独立模型治理页面控制台 */}
      <header className="border-b border-border bg-card px-6 py-4">
        <div className="w-full px-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="flex size-8 items-center justify-center rounded-md bg-primary-light text-primary">
                <SlidersHorizontal className="size-4" />
              </span>
              <div>
                <h1 className="text-lg font-semibold text-foreground">模型运行治理控制台</h1>
                <p className="text-[11px] text-tertiary">
                  专注于 Resilience4j 物理防护：限流、熔断、慢调用隔离、自动重试与高并发容错控制。
                </p>
              </div>
            </div>

            <Button
              onClick={handleRefreshRegistry}
              disabled={refreshing}
              size="sm"
            >
              <RefreshCw className={cn('size-3.5', refreshing && 'animate-spin')} />
              {refreshing ? '正在广播…' : '广播刷新 JVM 注册表快照'}
            </Button>
          </div>

        </div>
      </header>

      {/* 主体：全量模型治理列表 */}
      <main className="w-full min-w-0 flex-1 px-6 py-5">
        {/* 全局防护指标卡片 */}
        <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-4">
          <div className="flex items-center gap-3 rounded-md border border-border bg-card p-3">
            <ShieldCheck className="size-5 text-primary" />
            <div>
              <p className="text-[10px] font-medium text-tertiary">活跃治理规则</p>
              <p className="text-sm font-semibold text-foreground">{modelConfigs.length} 项已配置节点防护</p>
            </div>
          </div>

          <div className="flex items-center gap-3 rounded-md border border-border bg-card p-3">
            <Activity className="size-5 text-primary" />
            <div>
              <p className="text-[10px] font-medium text-tertiary">全局限流防护 (RateLimit)</p>
              <p className="text-sm font-semibold text-foreground">默认 120 RPM / 1000ms</p>
            </div>
          </div>

          <div className="flex items-center gap-3 rounded-md border border-border bg-card p-3">
            <ShieldAlert className="size-5 text-success" />
            <div>
              <p className="text-[10px] font-medium text-tertiary">熔断判定阈值 (CircuitBreaker)</p>
              <p className="text-sm font-semibold text-foreground">50% 失败率 / 3000ms 慢调用</p>
            </div>
          </div>

          <div className="flex items-center gap-3 rounded-md border border-border bg-card p-3">
            <Wrench className="size-5 text-warning" />
            <div>
              <p className="text-[10px] font-medium text-tertiary">绑定模式 (Binding Mode)</p>
              <p className="text-sm font-semibold text-foreground">CONFIG 物理节点独立生效</p>
            </div>
          </div>
        </div>

        <section className="rounded-lg border border-border bg-card p-5">
          <div className="flex items-center justify-between border-b border-border pb-4">
            <div>
              <h2 className="text-base font-semibold text-foreground">全站已接入模型节点治理规则列表</h2>
              <p className="text-xs text-tertiary">为每个具体物理模型配置独立的 20+ 项 Resilience4j 容错规则与保护策略。</p>
            </div>
          </div>

          <div className="mt-5 overflow-hidden rounded-md border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>模型标识</TableHead>
                  <TableHead>绑定模式</TableHead>
                  <TableHead>重试保护</TableHead>
                  <TableHead>单周期限流</TableHead>
                  <TableHead>熔断器</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {modelConfigs.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} className="py-10 text-center text-tertiary">
                      <p className="text-xs">暂无已配置的模型节点，请先在【模型配置】页面中接入物理厂商模型。</p>
                    </TableCell>
                  </TableRow>
                ) : (
                  modelConfigs.map((config) => {
                    const gov = governanceList.find((g) => g.configId === config.configId)
                    return (
                      <TableRow key={config.configId}>
                        <TableCell className="font-mono font-medium text-foreground">
                          <div className="flex items-center gap-2">
                            <span>{config.modelName}</span>
                            <Tag variant="info">{config.provider}</Tag>
                          </div>
                        </TableCell>
                        <TableCell className="font-mono text-secondary">
                          <Tag variant="neutral">{gov?.bindingMode || 'CONFIG'}</Tag>
                        </TableCell>
                        <TableCell>
                          <Tag variant={gov?.retryEnabled !== false ? 'success' : 'neutral'}>
                            {gov?.retryEnabled !== false ? `已开启 (${gov?.maxAttempts || 3} 次)` : '已关闭'}
                          </Tag>
                        </TableCell>
                        <TableCell className="font-mono text-secondary">
                          {gov?.rateLimitEnabled !== false ? `${gov?.limitForPeriod || 120} 次 / 周期` : '未限制'}
                        </TableCell>
                        <TableCell>
                          <Tag variant={gov?.circuitEnabled !== false ? 'info' : 'neutral'}>
                            {gov?.circuitEnabled !== false ? `阈值 ${gov?.failureRateThreshold || 50}%` : '未开启'}
                          </Tag>
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex items-center justify-end gap-3">
                            <button
                              type="button"
                              onClick={() => handleOpenEdit(config.configId, config.modelName)}
                              className="flex items-center gap-1 text-primary hover:underline"
                            >
                              <Wrench className="size-3" />
                              精细配置
                            </button>

                            {gov?.governanceId && (
                              <button
                                type="button"
                                onClick={() => handleResetDefault(gov.governanceId!, config.modelName)}
                                className="flex items-center gap-1 text-tertiary hover:text-warning"
                                title="重置为系统默认防护规则"
                              >
                                <RotateCcw className="size-3" />
                                重置默认
                              </button>
                            )}
                          </div>
                        </TableCell>
                      </TableRow>
                    )
                  })
                )}
              </TableBody>
            </Table>
          </div>
        </section>
      </main>

      {/* 节点精细治理配置 Modal */}
      <Dialog open={editModalOpen} onOpenChange={setEditModalOpen}>
        <DialogContent className="max-w-xl bg-card">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-base font-semibold text-foreground">
              <ShieldCheck className="size-4 text-primary" />
              精细节点保护配置: [{targetModelName}]
            </DialogTitle>
            <DialogDescription className="text-xs text-secondary">
              配置 Resilience4j 重试、单周期限流配额、慢调用熔断与流式首包/耗时保护。
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleSaveGovernance} className="mt-4 space-y-4">
            {/* 1. 自动重试 Retry */}
            <div className="rounded-md border border-border bg-card p-3 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-foreground">🔄 自动重试保护 (Retry)</span>
                <button
                  type="button"
                  onClick={() => setGovRetryEnabled(!govRetryEnabled)}
                  className="flex items-center gap-1 text-xs text-primary"
                >
                  {govRetryEnabled ? <ToggleRight className="size-5 text-success" /> : <ToggleLeft className="size-5 text-border" />}
                  <span className="text-[11px]">{govRetryEnabled ? '重试已开启' : '重试已关闭'}</span>
                </button>
              </div>
              {govRetryEnabled && (
                <div className="grid grid-cols-2 gap-3 pt-1">
                  <div>
                    <label className="mb-1 block text-[11px] text-secondary">最大尝试次数 (Max Attempts)</label>
                    <input
                      type="number"
                      min={1}
                      max={10}
                      value={govMaxAttempts}
                      onChange={(e) => setGovMaxAttempts(Number(e.target.value))}
                      className="w-full rounded-md border border-input bg-card px-3 py-1 text-xs outline-none focus:border-primary focus:ring-2 focus:ring-primary/30"
                    />
                  </div>
                  <div>
                    <label className="mb-1 block text-[11px] text-secondary">重试等待间隔 (ms)</label>
                    <input
                      type="number"
                      step={100}
                      value={govRetryWaitMs}
                      onChange={(e) => setGovRetryWaitMs(Number(e.target.value))}
                      className="w-full rounded-md border border-input bg-card px-3 py-1 text-xs outline-none focus:border-primary focus:ring-2 focus:ring-primary/30"
                    />
                  </div>
                </div>
              )}
            </div>

            {/* 2. 单周期限流与并发隔离 */}
            <div className="rounded-md border border-border bg-card p-3 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-foreground">🛡️ 单周期限流与最大并发隔离</span>
                <button
                  type="button"
                  onClick={() => setGovRateLimitEnabled(!govRateLimitEnabled)}
                  className="flex items-center gap-1 text-xs text-primary"
                >
                  {govRateLimitEnabled ? <ToggleRight className="size-5 text-success" /> : <ToggleLeft className="size-5 text-border" />}
                  <span className="text-[11px]">{govRateLimitEnabled ? '限流已开启' : '限流已关闭'}</span>
                </button>
              </div>
              <div className="grid grid-cols-2 gap-3 pt-1">
                <div>
                    <label className="mb-1 block text-[11px] text-secondary">单周期请求配额 (Limit for Period)</label>
                  <input
                    type="number"
                    min={1}
                    value={govLimitForPeriod}
                    onChange={(e) => setGovLimitForPeriod(Number(e.target.value))}
                      className="w-full rounded-md border border-input bg-card px-3 py-1 text-xs outline-none focus:border-primary focus:ring-2 focus:ring-primary/30"
                  />
                </div>
                <div>
                    <label className="mb-1 block text-[11px] text-secondary">最大并发数 (Max Concurrent Calls)</label>
                  <input
                    type="number"
                    min={1}
                    value={govMaxConcurrentCalls}
                    onChange={(e) => setGovMaxConcurrentCalls(Number(e.target.value))}
                      className="w-full rounded-md border border-input bg-card px-3 py-1 text-xs outline-none focus:border-primary focus:ring-2 focus:ring-primary/30"
                  />
                </div>
              </div>
            </div>

            {/* 3. 熔断与慢调用 */}
            <div className="rounded-md border border-border bg-card p-3 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-foreground">⚡ 熔断器与慢调用阈值</span>
                <button
                  type="button"
                  onClick={() => setGovCircuitEnabled(!govCircuitEnabled)}
                  className="flex items-center gap-1 text-xs text-primary"
                >
                  {govCircuitEnabled ? <ToggleRight className="size-5 text-success" /> : <ToggleLeft className="size-5 text-border" />}
                  <span className="text-[11px]">{govCircuitEnabled ? '熔断防护已开启' : '熔断已关闭'}</span>
                </button>
              </div>
              <div className="grid grid-cols-2 gap-3 pt-1">
                <div>
                    <label className="mb-1 block text-[11px] text-secondary">失败率阈值 (%)</label>
                  <input
                    type="number"
                    min={10}
                    max={100}
                    value={govFailureRateThreshold}
                    onChange={(e) => setGovFailureRateThreshold(Number(e.target.value))}
                      className="w-full rounded-md border border-input bg-card px-3 py-1 text-xs outline-none focus:border-primary focus:ring-2 focus:ring-primary/30"
                  />
                </div>
                <div>
                    <label className="mb-1 block text-[11px] text-secondary">慢调用判定耗时 (ms)</label>
                  <input
                    type="number"
                    step={500}
                    value={govSlowCallDurationMs}
                    onChange={(e) => setGovSlowCallDurationMs(Number(e.target.value))}
                      className="w-full rounded-md border border-input bg-card px-3 py-1 text-xs outline-none focus:border-primary focus:ring-2 focus:ring-primary/30"
                  />
                </div>
              </div>
            </div>

            {/* 4. 流式超时 */}
            <div className="rounded-md border border-border bg-card p-3 space-y-2">
              <span className="text-xs font-medium text-foreground">⏱️ 流式 SSE 首包与总耗时超时控制</span>
              <div className="grid grid-cols-2 gap-3 pt-1">
                <div>
                  <label className="mb-1 block text-[11px] text-secondary">流式首包超时时间 (ms)</label>
                  <input
                    type="number"
                    step={1000}
                    value={govStreamFirstChunkTimeoutMs}
                    onChange={(e) => setGovStreamFirstChunkTimeoutMs(Number(e.target.value))}
                    className="w-full rounded-md border border-input bg-card px-3 py-1 text-xs outline-none focus:border-primary focus:ring-2 focus:ring-primary/30"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-[11px] text-secondary">流式最大耗时限制 (ms)</label>
                  <input
                    type="number"
                    step={5000}
                    value={govStreamMaxDurationMs}
                    onChange={(e) => setGovStreamMaxDurationMs(Number(e.target.value))}
                    className="w-full rounded-md border border-input bg-card px-3 py-1 text-xs outline-none focus:border-primary focus:ring-2 focus:ring-primary/30"
                  />
                </div>
              </div>
            </div>

            <div className="mt-6 flex justify-end gap-2.5">
              <Button type="button" variant="outline" size="sm" onClick={() => setEditModalOpen(false)}>
                取消
              </Button>
              <Button type="submit" size="sm">
                保存物理防护策略
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* 底部 Toast */}
      <Toast message={toastMessage} />
    </div>
  )
}

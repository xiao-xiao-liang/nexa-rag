import { useEffect, useState, type FormEvent } from 'react'
import { Plus, RefreshCw, Settings2, Trash2 } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tag } from '@/components/ui/tag'
import { Toast } from '@/components/ui/toast'
import {
  createModelRoute,
  createModelRouteConfig,
  deleteModelRoute,
  deleteModelRouteConfig,
  getModelConfigs,
  getModelRouteConfigs,
  getModelRoutes,
  type ModelConfigItem,
  type ModelRouteConfigItem,
  type ModelRouteItem,
} from '../api/model-api'

/** 路由管理页：路由列表、新建/删除路由、候选模型配置管理。 */
export default function ModelRoutePage() {
  const navigate = useNavigate()
  const [routes, setRoutes] = useState<ModelRouteItem[]>([])
  const [candidateCounts, setCandidateCounts] = useState<Record<number | string, number>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [routeKey, setRouteKey] = useState('')
  const [modelType, setModelType] = useState('CHAT')
  const [strategy, setStrategy] = useState('FAILOVER')

  const [candidateRoute, setCandidateRoute] = useState<ModelRouteItem | null>(null)
  const [candidates, setCandidates] = useState<ModelRouteConfigItem[]>([])
  const [configs, setConfigs] = useState<ModelConfigItem[]>([])
  const [candidateConfigId, setCandidateConfigId] = useState<string>('')
  const [candidatePriority, setCandidatePriority] = useState(10)
  const [candidatesLoading, setCandidatesLoading] = useState(false)

  const showToast = (message: string) => {
    setToastMessage(message)
    setTimeout(() => setToastMessage(null), 2500)
  }

  const loadRoutes = async () => {
    setLoading(true)
    setError(null)
    try {
      const routeList = await getModelRoutes()
      setRoutes(routeList)
      const countEntries = await Promise.all(
        routeList.map(async (route) => {
          try {
            const items = await getModelRouteConfigs(route.routeId)
            return [route.routeId, items.length] as const
          } catch {
            return [route.routeId, 0] as const
          }
        }),
      )
      setCandidateCounts(Object.fromEntries(countEntries))
    } catch (err) {
      setError(err instanceof Error ? err.message : '路由列表加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadRoutes()
  }, [])

  const openCandidateDialog = async (route: ModelRouteItem) => {
    setCandidateRoute(route)
    setCandidatesLoading(true)
    try {
      const [candidateList, configList] = await Promise.all([
        getModelRouteConfigs(route.routeId),
        getModelConfigs(),
      ])
      setCandidates(candidateList)
      setConfigs(configList)
      setCandidateConfigId(String(configList[0]?.configId ?? ''))
    } catch (err) {
      showToast(err instanceof Error ? err.message : '候选配置加载失败')
    } finally {
      setCandidatesLoading(false)
    }
  }

  const handleCreateRoute = async (event: FormEvent) => {
    event.preventDefault()
    if (!routeKey.trim() || creating) return
    setCreating(true)
    try {
      await createModelRoute({ routeKey: routeKey.trim(), modelType, strategy })
      setCreateOpen(false)
      setRouteKey('')
      showToast(`已创建路由 [${routeKey.trim()}]`)
      void loadRoutes()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '创建路由失败')
    } finally {
      setCreating(false)
    }
  }

  const handleDeleteRoute = async (route: ModelRouteItem) => {
    if (!window.confirm(`确认删除路由 [${route.routeKey}]？`)) return
    try {
      await deleteModelRoute(route.routeId)
      showToast(`已删除路由 [${route.routeKey}]`)
      void loadRoutes()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '删除失败')
    }
  }

  const handleAddCandidate = async (event: FormEvent) => {
    event.preventDefault()
    if (!candidateRoute || !candidateConfigId || candidatesLoading) return
    try {
      const created = await createModelRouteConfig(candidateRoute.routeId, {
        configId: candidateConfigId,
        priority: candidatePriority,
      })
      setCandidates((current) => [...current, created])
      showToast('已添加候选模型')
    } catch (err) {
      showToast(err instanceof Error ? err.message : '添加候选模型失败')
    }
  }

  const handleDeleteCandidate = async (routeConfigId: number | string) => {
    if (!candidateRoute) return
    try {
      await deleteModelRouteConfig(candidateRoute.routeId, routeConfigId)
      setCandidates((current) => current.filter((item) => item.routeConfigId !== routeConfigId))
      showToast('已移除候选模型')
    } catch (err) {
      showToast(err instanceof Error ? err.message : '移除候选模型失败')
    }
  }

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col overflow-y-auto bg-background">
      <Toast message={toastMessage} />
      <div className="w-full px-6 py-5">
        <div className="mb-4 flex items-center justify-between">
          <div>
            <h1 className="text-xl font-semibold text-foreground">路由管理</h1>
            <p className="mt-0.5 text-xs text-tertiary">配置模型调度路由与候选模型</p>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => void loadRoutes()}>
              <RefreshCw className="size-3.5" />
              刷新
            </Button>
            <Button size="sm" onClick={() => setCreateOpen(true)}>
              <Plus className="size-3.5" />
              新建路由
            </Button>
          </div>
        </div>
        {error && (
          <div className="mb-3 rounded-md border border-danger-light bg-danger-light p-3 text-xs text-danger">
            {error}
          </div>
        )}
        <div className="overflow-hidden rounded-lg border border-border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>路由名称</TableHead>
                <TableHead>routeKey</TableHead>
                <TableHead>模型类型</TableHead>
                <TableHead>候选配置数</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={5} className="py-10 text-center text-xs text-tertiary">正在加载…</TableCell>
                </TableRow>
              ) : routes.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={5} className="py-10 text-center text-xs text-tertiary">暂无路由，点击右上角新建</TableCell>
                </TableRow>
              ) : (
                routes.map((route) => (
                  <TableRow key={route.routeId}>
                    <TableCell>
                      <div className="font-medium text-foreground">{route.routeKey}</div>
                      <div className="text-[10px] text-tertiary">
                        {route.strategy || 'FAILOVER'} · {route.enabled ? '已启用' : '已禁用'}
                      </div>
                    </TableCell>
                    <TableCell className="font-mono text-xs text-secondary">{route.routeKey}</TableCell>
                    <TableCell><Tag variant="info">{route.modelType}</Tag></TableCell>
                    <TableCell className="text-secondary">{candidateCounts[route.routeId] ?? '—'}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex items-center justify-end gap-3 text-xs">
                        <button
                          type="button"
                          onClick={() => void openCandidateDialog(route)}
                          className="inline-flex items-center gap-1 text-primary hover:underline"
                        >
                          <Settings2 className="size-3.5" />
                          候选配置
                        </button>
                        <button
                          type="button"
                          onClick={() => navigate('/models/governance')}
                          className="text-secondary hover:text-primary"
                        >
                          治理
                        </button>
                        <button
                          type="button"
                          aria-label={`删除 ${route.routeKey}`}
                          onClick={() => void handleDeleteRoute(route)}
                          className="text-danger hover:underline"
                        >
                          <Trash2 className="size-3.5" />
                        </button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
          {!loading && routes.length > 0 && (
            <div className="border-t border-border px-4 py-3 text-xs text-tertiary">
              共 {routes.length} 条路由
            </div>
          )}
        </div>
      </div>

      {/* 新建路由 Dialog */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-md bg-card">
          <DialogHeader>
            <DialogTitle className="text-base font-semibold text-foreground">新建路由</DialogTitle>
            <DialogDescription className="text-xs text-secondary">创建模型调度路由，保存后可在候选配置中绑定模型。</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreateRoute} className="mt-2 space-y-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">routeKey</label>
              <Input
                required
                value={routeKey}
                onChange={(e) => setRouteKey(e.target.value)}
                placeholder="例如 DEFAULT_LLM"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">模型类型</label>
              <Input
                required
                value={modelType}
                onChange={(e) => setModelType(e.target.value)}
                placeholder="CHAT / EMBEDDING / RERANK"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-secondary">策略</label>
              <Input
                value={strategy}
                onChange={(e) => setStrategy(e.target.value)}
                placeholder="FAILOVER / WEIGHTED"
              />
            </div>
            <div className="flex justify-end gap-2.5">
              <Button type="button" variant="outline" size="sm" onClick={() => setCreateOpen(false)}>
                取消
              </Button>
              <Button type="submit" size="sm" disabled={creating}>
                {creating ? '创建中…' : '创建路由'}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* 候选配置 Dialog */}
      <Dialog open={candidateRoute !== null} onOpenChange={(open) => !open && setCandidateRoute(null)}>
        <DialogContent className="max-w-xl bg-card">
          <DialogHeader>
            <DialogTitle className="text-base font-semibold text-foreground">
              路由候选配置：{candidateRoute?.routeKey ?? ''}
            </DialogTitle>
            <DialogDescription className="text-xs text-secondary">绑定候选模型并设置优先级，供路由调度使用。</DialogDescription>
          </DialogHeader>
          <div className="mt-2 space-y-4">
            <form onSubmit={handleAddCandidate} className="flex items-end gap-2">
              <div className="flex-1">
                <label className="mb-1 block text-xs font-medium text-secondary">候选模型</label>
                <select
                  value={candidateConfigId}
                  onChange={(e) => setCandidateConfigId(e.target.value)}
                  className="h-8 w-full rounded-md border border-input bg-card px-2 text-sm outline-none focus:ring-2 focus:ring-primary/30"
                >
                  {configs.map((config) => (
                    <option key={config.configId} value={String(config.configId)}>
                      {config.modelName}（{config.provider}）
                    </option>
                  ))}
                </select>
              </div>
              <div className="w-24">
                <label className="mb-1 block text-xs font-medium text-secondary">优先级</label>
                <Input
                  type="number"
                  value={candidatePriority}
                  onChange={(e) => setCandidatePriority(Number(e.target.value))}
                />
              </div>
              <Button type="submit" size="sm">添加</Button>
            </form>

            <div className="overflow-hidden rounded-md border border-border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>模型名称</TableHead>
                    <TableHead>厂商</TableHead>
                    <TableHead>优先级</TableHead>
                    <TableHead className="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {candidatesLoading ? (
                    <TableRow>
                      <TableCell colSpan={4} className="py-8 text-center text-xs text-tertiary">正在加载…</TableCell>
                    </TableRow>
                  ) : candidates.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={4} className="py-8 text-center text-xs text-tertiary">暂无候选模型</TableCell>
                    </TableRow>
                  ) : (
                    candidates.map((candidate) => (
                      <TableRow key={candidate.routeConfigId}>
                        <TableCell className="font-medium">{candidate.modelName || candidate.configId}</TableCell>
                        <TableCell className="text-secondary">{candidate.provider || '—'}</TableCell>
                        <TableCell className="text-secondary">{candidate.priority ?? '—'}</TableCell>
                        <TableCell className="text-right">
                          <button
                            type="button"
                            onClick={() => void handleDeleteCandidate(candidate.routeConfigId)}
                            className="text-danger hover:underline"
                          >
                            移除
                          </button>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}

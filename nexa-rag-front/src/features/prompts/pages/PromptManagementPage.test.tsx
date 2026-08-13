import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PromptManagementPage from './PromptManagementPage'
import * as promptApi from '../api/prompt-api'

const mockPrompts = [
  {
    promptCode: 'chat.rewrite.instruction',
    name: '会话问题改写',
    variableSchema: '{"required":["question"]}',
    enabled: true,
    currentReleaseId: 14101,
    currentReleaseRevision: 1,
    versions: [
      {
        versionId: 11401,
        versionNo: 1,
        content: '你是问题改写助手:\n{{question}}',
        createdBy: 'SYSTEM',
        createdAt: '2026-08-05T12:00:00',
        remark: '初始版本',
      },
    ],
    releases: [
      {
        releaseId: 14101,
        stableVersionId: 11401,
        canaryVersionId: null,
        canaryRule: null,
        releaseRevision: 1,
        releasedBy: 'SYSTEM',
        releasedAt: '2026-08-05T12:00:00',
        remark: '初始发布',
      },
    ],
  },
]

describe('PromptManagementPage 提示词管理页面', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('应成功渲染提示词列表与详情标头', async () => {
    vi.spyOn(promptApi, 'getPrompts').mockResolvedValue(mockPrompts as any)
    vi.spyOn(promptApi, 'getPrompt').mockResolvedValue(mockPrompts[0] as any)

    render(
      <MemoryRouter>
        <PromptManagementPage />
      </MemoryRouter>,
    )

    expect(await screen.findByText('已启用 (点击禁用)')).toBeInTheDocument()
    const titleElements = await screen.findAllByText('会话问题改写')
    expect(titleElements.length).toBeGreaterThan(0)
    const codeElements = await screen.findAllByText('chat.rewrite.instruction')
    expect(codeElements.length).toBeGreaterThan(0)
  })

  it('应自动提取模板变量并支持交互显示', async () => {
    vi.spyOn(promptApi, 'getPrompts').mockResolvedValue(mockPrompts as any)
    vi.spyOn(promptApi, 'getPrompt').mockResolvedValue(mockPrompts[0] as any)

    render(
      <MemoryRouter>
        <PromptManagementPage />
      </MemoryRouter>,
    )

    expect(await screen.findByText('{{question}}')).toBeInTheDocument()
  })
})

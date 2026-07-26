import { createBrowserRouter, Navigate, type RouteObject } from 'react-router-dom'
import { AppShell } from './AppShell'
import ChatWorkspace from '@/features/chat/ChatWorkspace'
import { DocumentDetailPage } from '@/features/knowledge-base/pages/DocumentDetailPage'
import { KnowledgeBaseListPage } from '@/features/knowledge-base/pages/KnowledgeBaseListPage'

/** 应用可复用路由表，供浏览器路由和测试内存路由共同使用。 */
export const routes: RouteObject[] = [{
  path: '/',
  element: <AppShell />,
  children: [
    { index: true, element: <Navigate to="/chat" replace /> },
    { path: 'chat', element: <ChatWorkspace /> },
    { path: 'knowledge-base', element: <KnowledgeBaseListPage /> },
    { path: 'knowledge-base/:documentId', element: <DocumentDetailPage /> },
  ],
}]

/** 浏览器运行时路由器。 */
export const router = createBrowserRouter(routes)

import React from "react";
import { BrowserRouter, Routes, Route, Navigate, useLocation } from "react-router-dom";
import { AppShell } from "../components/layout/AppShell";
import { HomePage } from "../features/home/HomePage";
import { ChatPage } from "../features/chat/ChatPage";
import { KnowledgeBaseManagePage } from "../features/knowledge-base/KnowledgeBaseManagePage";
import { DocumentPage } from "../features/document/DocumentPage";
import { DocumentDetailPage } from "../features/document/DocumentDetailPage";
import { ModelPage } from "../features/model/ModelPage";
import { PromptPage } from "../features/prompt/PromptPage";
import { CrmOrderPage } from "../features/crm/CrmOrderPage";
import { LoginPage } from "@/features/auth";
import { authStore, useAuthStore } from "../features/auth/store/authStore";
import { authApi } from "../lib/api";

/**
 * 登录态路由守卫：未登录直接拦截并重定向至 /login
 */
const RequireAuth: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isSessionResolved } = useAuthStore();
  const location = useLocation();

  if (!isSessionResolved) {
    return null;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <>{children}</>;
};

/**
 * 页面权限路由守卫：无对应权限的用户访问时重定向至 /home。
 */
const RequirePermission: React.FC<{ permission: string; children: React.ReactNode }> = ({ permission, children }) => {
  const { hasPermission, isSessionResolved } = useAuthStore();

  if (!isSessionResolved || !hasPermission(permission)) {
    return <Navigate to="/home" replace />;
  }

  return <>{children}</>;
};

/**
 * 公开登录页守卫：已登录用户访问 /login 自动重定向至首页 /home
 */
const PublicOnlyRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isSessionResolved } = useAuthStore();

  if (!isSessionResolved) {
    return null;
  }

  if (isAuthenticated) {
    return <Navigate to="/home" replace />;
  }

  return <>{children}</>;
};

/**
 * 应用启动时从服务端同步会话，localStorage 仅作为展示缓存而非鉴权依据。
 */
const SessionBootstrap: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isSessionResolved } = useAuthStore();

  React.useEffect(() => {
    if (isSessionResolved) {
      return;
    }
    let cancelled = false;

    void authApi.getCurrentSession()
      .then((session) => {
        if (!cancelled) {
          authStore.setSession(session);
        }
      })
      .catch(() => {
        if (!cancelled) {
          authStore.clearSession();
        }
      });

    return () => {
      cancelled = true;
    };
  }, [isSessionResolved]);

  return <>{children}</>;
};

export const AppRouter: React.FC = () => {
  return (
    <BrowserRouter>
      <SessionBootstrap>
        <Routes>
          {/* 独立公开认证页面 (全屏无顶部导航外壳，已登录自动跳转主页) */}
          <Route
            path="/login"
            element={
              <PublicOnlyRoute>
                <LoginPage />
              </PublicOnlyRoute>
            }
          />

          {/* 业务主系统页面 (包裹在 RequireAuth 与 AppShell 中，未登录全量拦截) */}
          <Route
            path="/*"
            element={
              <RequireAuth>
                <AppShell>
                  <Routes>
                    <Route path="/" element={<Navigate to="/home" replace />} />
                    <Route path="/home" element={<HomePage />} />
                    <Route path="/chat" element={<ChatPage />} />
                    <Route path="/chat/:conversationId" element={<ChatPage />} />
                    <Route path="/knowledge-base" element={<KnowledgeBaseManagePage />} />
                    <Route path="/knowledge-base/:knowledgeBaseId" element={<DocumentPage />} />
                    <Route
                      path="/knowledge-base/:knowledgeBaseId/documents/:documentId"
                      element={<DocumentDetailPage />}
                    />

                    {/* 仅 ADMIN 管理员角色及具备相应权限才可访问的路由 */}
                    <Route
                      path="/models/*"
                      element={
                        <RequirePermission permission="model:manage">
                          <ModelPage />
                        </RequirePermission>
                      }
                    />
                    <Route
                      path="/prompts"
                      element={
                        <RequirePermission permission="prompt:manage">
                          <PromptPage />
                        </RequirePermission>
                      }
                    />
                    <Route
                      path="/crm"
                      element={
                        <RequirePermission permission="crm:view">
                          <CrmOrderPage />
                        </RequirePermission>
                      }
                    />

                    <Route path="*" element={<Navigate to="/home" replace />} />
                  </Routes>
                </AppShell>
              </RequireAuth>
            }
          />
        </Routes>
      </SessionBootstrap>
    </BrowserRouter>
  );
};

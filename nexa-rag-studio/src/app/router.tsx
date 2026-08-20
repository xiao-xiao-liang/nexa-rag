import React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AppShell } from "../components/layout/AppShell";
import { HomePage } from "../features/home/HomePage";
import { ChatPage } from "../features/chat/ChatPage";
import { KnowledgeBaseManagePage } from "../features/knowledge-base/KnowledgeBaseManagePage";
import { DocumentPage } from "../features/document/DocumentPage";
import { DocumentDetailPage } from "../features/document/DocumentDetailPage";
import { ModelPage } from "../features/model/ModelPage";
import { PromptPage } from "../features/prompt/PromptPage";
import { CrmOrderPage } from "../features/crm/CrmOrderPage";

export const AppRouter: React.FC = () => {
  return (
    <BrowserRouter>
      <AppShell>
        <Routes>
          <Route path="/" element={<Navigate to="/home" replace />} />
          <Route path="/home" element={<HomePage />} />
          <Route path="/chat" element={<ChatPage />} />
          <Route path="/knowledge-base" element={<KnowledgeBaseManagePage />} />
          <Route path="/knowledge-base/:knowledgeBaseId" element={<DocumentPage />} />
          <Route path="/knowledge-base/:knowledgeBaseId/documents/:documentId" element={<DocumentDetailPage />} />
          <Route path="/models/*" element={<ModelPage />} />
          <Route path="/prompts" element={<PromptPage />} />
          <Route path="/crm" element={<CrmOrderPage />} />
          <Route path="*" element={<Navigate to="/home" replace />} />
        </Routes>
      </AppShell>
    </BrowserRouter>
  );
};

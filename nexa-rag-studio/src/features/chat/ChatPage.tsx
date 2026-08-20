import React, { useState, useEffect, useRef, useCallback } from "react";
import { chatApi } from "../../lib/api";
import { ChatMessageVO, ChatConversationVO, ChatStreamEvent, ChatToolOperation } from "../../types";
import {
  ChatHeader,
  ChatInputBox,
  ChatMessageItem,
  ConversationSidebar,
  FeishuAgentCubeLogo,
} from "../../components/chat";
import { FEISHU_FONT_FAMILY } from "../../components/ui/feishu-table";
import { Sparkles, Table2, FileText, Globe } from "lucide-react";

export const ChatPage: React.FC = () => {
  const [conversations, setConversations] = useState<ChatConversationVO[]>([]);
  // 默认不选中任何历史会话 (null 表示新建会话状态)
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessageVO[]>([]);
  const [inputContent, setInputContent] = useState("");
  const [isGenerating, setIsGenerating] = useState(false);
  const [generationId, setGenerationId] = useState<string | null>(null);
  const [copiedMsgId, setCopiedMsgId] = useState<string | null>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(1);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const timerRef = useRef<NodeJS.Timeout | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const currentGenerationIdRef = useRef<string | null>(null);
  const lastEventVersionRef = useRef(0);
  const terminalRef = useRef(false);
  // 新会话的 META 事件会紧随用户消息写入到达；避免此时的历史占位消息覆盖本地流式消息。
  const skipNextHistoryLoadRef = useRef(false);

  const parseToolOperations = (toolOperationsJson?: string): ChatToolOperation[] => {
    if (!toolOperationsJson) return [];
    try {
      const parsed = JSON.parse(toolOperationsJson);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  };

  // 1. 初始化拉取会话列表 (保持默认新建会话视图，不自动激活首个会话)
  const loadConversations = useCallback(async () => {
    try {
      const res = await chatApi.listConversations(1, 50);
      if (res && res.records) {
        setConversations(res.records);
      }
    } catch (err) {
      console.warn("加载会话列表失败:", err);
    }
  }, []);

  useEffect(() => {
    loadConversations();
  }, [loadConversations]);

  // 2. 切换当前会话时，拉取该会话下的历史消息 (如果为 null 则重置为空)
  const loadHistory = useCallback(async (convId: string) => {
    setIsLoadingHistory(true);
    try {
      const res = await chatApi.getHistory(convId);
      if (res && res.records) {
        setMessages(res.records.map((message) => ({
          ...message,
          content: message.content ?? "",
          operations: parseToolOperations(message.toolOperationsJson),
        })));
      } else {
        setMessages([]);
      }
    } catch (err) {
      console.warn(`拉取会话 ${convId} 历史消息失败:`, err);
      setMessages([]);
    } finally {
      setIsLoadingHistory(false);
    }
  }, []);

  useEffect(() => {
    if (activeConversationId) {
      if (skipNextHistoryLoadRef.current) {
        skipNextHistoryLoadRef.current = false;
        return;
      }
      loadHistory(activeConversationId);
    } else {
      setMessages([]);
    }
  }, [activeConversationId, loadHistory]);

  // 3. 滚动到底部锚点
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isGenerating]);

  // 4. 计时器
  useEffect(() => {
    if (isGenerating) {
      setElapsedSeconds(1);
      timerRef.current = setInterval(() => {
        setElapsedSeconds((prev) => prev + 1);
      }, 1000);
    } else {
      if (timerRef.current) clearInterval(timerRef.current);
    }
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isGenerating]);

  // 5. 点击新建会话：重置为新建会话状态
  const handleNewConversation = () => {
    setActiveConversationId(null);
    setMessages([]);
    setInputContent("");
    setIsGenerating(false);
    setGenerationId(null);
  };

  // 6. 重命名会话 (对接 PUT /api/conversations/{id})
  const handleRenameConversation = async (conversationId: string, newTitle: string) => {
    try {
      await chatApi.updateConversationTitle(conversationId, newTitle);
      setConversations((prev) =>
        prev.map((c) => (c.conversationId === conversationId ? { ...c, title: newTitle } : c))
      );
    } catch (err) {
      console.warn("重命名会话失败:", err);
    }
  };

  // 7. 删除会话 (对接 DELETE /api/conversations/{id})
  const handleDeleteConversation = async (conversationId: string) => {
    try {
      await chatApi.deleteConversation(conversationId);
      const updated = conversations.filter((c) => c.conversationId !== conversationId);
      setConversations(updated);

      // 若删除当前活动会话，平滑重置为新建会话视图
      if (activeConversationId === conversationId) {
        handleNewConversation();
      }
    } catch (err) {
      console.warn("删除会话失败:", err);
    }
  };

  // 8. 发送消息并接入真实 SSE 流式对话 (POST /api/chat/stream)
  const handleSend = async (overrideContent?: string) => {
    const content = overrideContent || inputContent;
    if (!content.trim() || isGenerating) return;

    const timeStr = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

    const userMsg: ChatMessageVO = {
      messageId: `msg-${Date.now()}`,
      sequence: messages.length + 1,
      role: "user",
      status: "SUCCESS",
      content: content,
      createdTime: timeStr,
    };

    const assistantMsgId = `msg-${Date.now() + 1}`;
    const initialAssistantMsg: ChatMessageVO = {
      messageId: assistantMsgId,
      sequence: messages.length + 2,
      role: "assistant",
      status: "GENERATING",
      content: "",
      createdTime: timeStr,
    };

    setMessages((prev) => [...prev, userMsg, initialAssistantMsg]);
    setInputContent("");
    setIsGenerating(true);

    const controller = new AbortController();
    abortControllerRef.current = controller;
    terminalRef.current = false;
    lastEventVersionRef.current = 0;

    const applyStreamEvent = (event: ChatStreamEvent) => {
      const eventVersion = Number(event.eventVersion);
      if (Number.isFinite(eventVersion) && eventVersion > 0) {
        if (eventVersion <= lastEventVersionRef.current) return;
        lastEventVersionRef.current = eventVersion;
      }
      const targetMessageId = event.messageId || assistantMsgId;

      if (event.type === "META") {
        if (event.generationId) {
          currentGenerationIdRef.current = event.generationId;
          setGenerationId(event.generationId);
        }
        setMessages((prev) => prev.map((message) => message.messageId === assistantMsgId
          ? { ...message, messageId: targetMessageId, generationId: event.generationId }
          : message));
        if (event.conversationId && event.conversationId !== activeConversationId) {
          skipNextHistoryLoadRef.current = true;
          setActiveConversationId(event.conversationId);
          loadConversations();
        }
        return;
      }

      if (event.type === "SNAPSHOT") {
        setMessages((prev) => prev.map((message) => message.messageId === targetMessageId
          ? { ...message, operations: event.operations || [], status: "GENERATING", connectionState: "STREAMING" }
          : message));
        return;
      }

      if (event.type === "ANSWER_DELTA" || event.type === "TOKEN" || event.type === "TEXT") {
        if (event.content) setMessages((prev) => prev.map((message) => message.messageId === targetMessageId
          ? { ...message, content: `${message.content || ""}${event.content}`, status: "GENERATING", connectionState: "STREAMING" }
          : message));
        return;
      }

      if (event.type === "COMPLETE" || event.type === "CANCELLED" || event.type === "ERROR") {
        terminalRef.current = true;
        const status = event.type === "COMPLETE" ? "COMPLETED" : event.type;
        setMessages((prev) => prev.map((message) => message.messageId === targetMessageId
          ? { ...message, status, operations: event.operations || message.operations, connectionState: undefined }
          : message));
        setIsGenerating(false);
        currentGenerationIdRef.current = null;
        setGenerationId(null);
        loadConversations();
      }
    };

    const recoverStream = async (attempt: number): Promise<void> => {
      const currentGenerationId = currentGenerationIdRef.current;
      if (!currentGenerationId || terminalRef.current || controller.signal.aborted) return;
      if (attempt >= 3) {
        setMessages((prev) => prev.map((message) => (message.messageId === assistantMsgId
          || message.generationId === currentGenerationId)
          ? { ...message, connectionState: "BACKGROUND_RUNNING" }
          : message));
        setIsGenerating(false);
        return;
      }
      setMessages((prev) => prev.map((message) => (message.messageId === assistantMsgId
        || message.generationId === currentGenerationId)
        ? { ...message, connectionState: "RECONNECTING" }
        : message));
      await new Promise((resolve) => window.setTimeout(resolve, 300 * (attempt + 1)));
      try {
        await chatApi.resumeChat(currentGenerationId, lastEventVersionRef.current, applyStreamEvent, controller.signal);
        if (!terminalRef.current) await recoverStream(attempt + 1);
      } catch {
        await recoverStream(attempt + 1);
      }
    };

    await chatApi.streamChat(
      {
        conversationId: activeConversationId || undefined,
        content,
      },
      applyStreamEvent,
      () => recoverStream(0),
      () => {
        if (!terminalRef.current) void recoverStream(0);
      },
      controller.signal
    );
  };

  // 9. 主动取消生成 (对接 DELETE /api/chat/generations/{generationId})
  const handleCancelGeneration = async () => {
    if (generationId) {
      await chatApi.cancelGeneration(generationId);
    }
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setIsGenerating(false);
    currentGenerationIdRef.current = null;
    setGenerationId(null);
    setMessages((prev) =>
      prev.map((m) => (m.status === "GENERATING" ? { ...m, status: "CANCELLED" } : m))
    );
  };

  const handleCopy = (msgId: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedMsgId(msgId);
    setTimeout(() => setCopiedMsgId(null), 2000);
  };

  const activeConversation = conversations.find((c) => c.conversationId === activeConversationId);
  const currentTitle = activeConversation?.title || "飞书知识问答";

  return (
    <div
      style={{ fontFamily: FEISHU_FONT_FAMILY }}
      className="flex h-full w-full bg-white text-[#1F2329] select-none overflow-hidden"
    >
      {/* 1. 飞书 1:1 会话历史侧边栏 */}
      <ConversationSidebar
        conversations={conversations}
        activeId={activeConversationId}
        onSelect={(id) => setActiveConversationId(id)}
        onNew={handleNewConversation}
        onRename={handleRenameConversation}
        onDelete={handleDeleteConversation}
        isCollapsed={isSidebarCollapsed}
        onToggleCollapse={() => setIsSidebarCollapsed(!isSidebarCollapsed)}
      />

      {/* 2. 右侧主工作台 */}
      <div className="flex-1 flex flex-col h-full min-w-0 bg-white relative overflow-hidden">
        {/* 顶部 Header (解耦组件) */}
        <ChatHeader
          title={currentTitle}
          subtitle="内容由 AI 生成"
          onNewConversation={handleNewConversation}
        />

        {/* 消息视口流 */}
        <div className="flex-1 overflow-y-auto px-6 pt-4 pb-12 bg-white standard-scrollbar">
          {isLoadingHistory ? (
            <div className="flex items-center justify-center h-48 text-[13px] text-[#8F959E]">
              正在加载历史对话...
            </div>
          ) : messages.length === 0 && !isGenerating ? (
            /* 新会话空白欢迎引导卡 (默认展示) */
            <div className="max-w-[680px] mx-auto pt-10 pb-8 animate-in fade-in zoom-in-95 duration-200">
              <div className="text-center space-y-2.5">
                <div className="w-12 h-12 rounded-[14px] bg-[#E8F3FF] flex items-center justify-center mx-auto shadow-2xs">
                  <FeishuAgentCubeLogo />
                </div>
                <h2 className="text-[18px] font-semibold text-[#1F2329]">
                  {currentTitle}
                </h2>
                <p className="text-[13px] text-[#8F959E] max-w-md mx-auto">
                  你好！我是 Nexa-RAG 专属 AI 工作助手，支持知识库混合检索、多维表格协同与长文档智能解析。
                </p>
              </div>

              {/* 推荐提问 Prompt 引导卡片 */}
              <div className="grid grid-cols-2 gap-3 mt-8">
                <div
                  onClick={() => handleSend("查询多维表格里的数据")}
                  className="p-3.5 rounded-[10px] border border-[#DEE0E3] bg-white hover:border-[#3370FF] hover:bg-[#F0F6FF]/30 hover:shadow-2xs transition-all cursor-pointer group"
                >
                  <div className="flex items-center gap-2 text-[#3370FF]">
                    <Table2 className="w-4 h-4" />
                    <span className="text-[13px] font-semibold text-[#1F2329] group-hover:text-[#3370FF]">
                      查询多维表格数据
                    </span>
                  </div>
                  <p className="text-[12px] text-[#8F959E] mt-1.5 leading-normal">
                    快速聚合、统计多维表格跨表关联记录
                  </p>
                </div>

                <div
                  onClick={() => handleSend("请简要分析 Nexa-RAG 中的动态模型路由机制是怎样工作的？")}
                  className="p-3.5 rounded-[10px] border border-[#DEE0E3] bg-white hover:border-[#3370FF] hover:bg-[#F0F6FF]/30 hover:shadow-2xs transition-all cursor-pointer group"
                >
                  <div className="flex items-center gap-2 text-[#3370FF]">
                    <Sparkles className="w-4 h-4" />
                    <span className="text-[13px] font-semibold text-[#1F2329] group-hover:text-[#3370FF]">
                      检索模型路由机制
                    </span>
                  </div>
                  <p className="text-[12px] text-[#8F959E] mt-1.5 leading-normal">
                    深入理解 Dense+Sparse 检索与路由
                  </p>
                </div>

                <div
                  onClick={() => handleSend("飞书云文档与语雀文档在做分块切片时有什么主要区别？")}
                  className="p-3.5 rounded-[10px] border border-[#DEE0E3] bg-white hover:border-[#3370FF] hover:bg-[#F0F6FF]/30 hover:shadow-2xs transition-all cursor-pointer group"
                >
                  <div className="flex items-center gap-2 text-[#3370FF]">
                    <Globe className="w-4 h-4" />
                    <span className="text-[13px] font-semibold text-[#1F2329] group-hover:text-[#3370FF]">
                      飞书与语雀切片对比
                    </span>
                  </div>
                  <p className="text-[12px] text-[#8F959E] mt-1.5 leading-normal">
                    对比解析多维表格与 Markdown 分块
                  </p>
                </div>

                <div
                  onClick={() => handleSend("你有哪些核心能力？")}
                  className="p-3.5 rounded-[10px] border border-[#DEE0E3] bg-white hover:border-[#3370FF] hover:bg-[#F0F6FF]/30 hover:shadow-2xs transition-all cursor-pointer group"
                >
                  <div className="flex items-center gap-2 text-[#3370FF]">
                    <FileText className="w-4 h-4" />
                    <span className="text-[13px] font-semibold text-[#1F2329] group-hover:text-[#3370FF]">
                      智能助手能力白皮书
                    </span>
                  </div>
                  <p className="text-[12px] text-[#8F959E] mt-1.5 leading-normal">
                    查看多维表格、生态协同与通用能力
                  </p>
                </div>
              </div>
            </div>
          ) : (
            /* 1:1 飞书官方消息流 */
            <div className="max-w-[760px] mx-auto space-y-7">
              {messages.map((msg, index) => (
                <ChatMessageItem
                  key={msg.messageId || index}
                  message={msg}
                  index={index}
                  isGenerating={isGenerating && index === messages.length - 1}
                  isCopied={copiedMsgId === msg.messageId}
                  onCopy={handleCopy}
                  elapsedSeconds={elapsedSeconds}
                />
              ))}
              <div ref={messagesEndRef} />
            </div>
          )}
        </div>

        {/* 底部输入框 (解耦组件) */}
        <ChatInputBox
          value={inputContent}
          onChange={setInputContent}
          onSend={() => handleSend()}
          onCancel={handleCancelGeneration}
          isGenerating={isGenerating}
        />
      </div>
    </div>
  );
};

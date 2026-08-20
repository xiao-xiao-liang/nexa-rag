import React from "react";
import { Link } from "react-router-dom";
import {
  BookOpen,
  MessageSquare,
  Cpu,
  Sliders,
  TableProperties,
  Plus,
} from "lucide-react";
import { Button } from "../../components/ui/button";

/** 飞书原版 1:1 灰色极简趋势折线迷你图 (Sparklines) */
const Sparkline1 = () => (
  <svg className="w-16 h-6 text-[#C9CDD4] shrink-0" viewBox="0 0 64 24" fill="none">
    <path
      d="M2 18 L8 14 L14 19 L20 10 L26 17 L32 7 L38 15 L44 9 L50 16 L56 6 L62 11"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

const Sparkline2 = () => (
  <svg className="w-16 h-6 text-[#C9CDD4] shrink-0" viewBox="0 0 64 24" fill="none">
    <path
      d="M2 4 L6 19 L14 19 L22 19 L30 18 L38 19 L46 18 L54 19 L62 18"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

const Sparkline3 = () => (
  <svg className="w-16 h-6 text-[#C9CDD4] shrink-0" viewBox="0 0 64 24" fill="none">
    <path
      d="M2 5 L7 20 L15 19 L23 19 L31 18 L39 19 L47 18 L55 19 L62 18"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export const HomePage: React.FC = () => {
  return (
    <div className="flex h-full flex-col overflow-y-auto bg-white text-[#1F2329] select-none space-y-4">
      {/* 1. 顶部标题栏 Header (纯白背景 + 飞书风格标题与操作) */}
      <div className="flex flex-col justify-between gap-4 md:flex-row md:items-center">
        <div>
          <h1 className="text-[18px] font-bold text-[#1F2329] tracking-tight">NexaRAG 平台工作台</h1>
          <p className="mt-0.5 text-[13px] text-[#646A73]">
            企业级高可用 RAG 知识检索与 LangGraph 智能多 Agent 协同网关
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <Button asChild variant="outline" className="h-8 rounded-[6px] text-[13px] border-[#DEE0E3] bg-white text-[#1F2329] hover:bg-[#F2F3F5]">
            <Link to="/knowledge-base">
              <BookOpen className="mr-1.5 w-3.5 h-3.5" />
              进入知识库
            </Link>
          </Button>
          <Button asChild className="h-8 rounded-[6px] bg-[#3370FF] text-[13px] text-white hover:bg-[#2A62EA]">
            <Link to="/chat">
              <MessageSquare className="mr-1.5 w-3.5 h-3.5" />
              发起智能对话
            </Link>
          </Button>
        </div>
      </div>

      {/* 2. 飞书 1:1 快捷操作行 (Shortcut Row) */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1">
        <Link
          to="/chat"
          className="flex h-7 items-center gap-1.5 rounded-[6px] border border-[#DEE0E3] bg-white px-2.5 text-[12px] text-[#1F2329] transition-colors hover:border-[#3370FF] hover:text-[#3370FF] shrink-0"
        >
          <span className="flex h-3.5 w-3.5 items-center justify-center rounded-full bg-[#3370FF] text-[10px] text-white font-bold">
            <Plus className="w-2.5 h-2.5 stroke-[2.5]" />
          </span>
          新建智能对话
        </Link>
        <Link
          to="/knowledge-base"
          className="flex h-7 items-center gap-1.5 rounded-[6px] border border-[#DEE0E3] bg-white px-2.5 text-[12px] text-[#1F2329] transition-colors hover:border-[#3370FF] hover:text-[#3370FF] shrink-0"
        >
          <span className="flex h-3.5 w-3.5 items-center justify-center rounded-full bg-[#35BD4B] text-[10px] text-white font-bold">
            <Plus className="w-2.5 h-2.5 stroke-[2.5]" />
          </span>
          接入知识文档
        </Link>
        <Link
          to="/models/configs"
          className="flex h-7 items-center gap-1.5 rounded-[6px] border border-[#DEE0E3] bg-white px-2.5 text-[12px] text-[#1F2329] transition-colors hover:border-[#3370FF] hover:text-[#3370FF] shrink-0"
        >
          <span className="flex h-3.5 w-3.5 items-center justify-center rounded-full bg-[#8D55ED] text-[10px] text-white font-bold">
            <Cpu className="w-2.5 h-2.5" />
          </span>
          模型网关配置
        </Link>
        <Link
          to="/prompts"
          className="flex h-7 items-center gap-1.5 rounded-[6px] border border-[#DEE0E3] bg-white px-2.5 text-[12px] text-[#1F2329] transition-colors hover:border-[#3370FF] hover:text-[#3370FF] shrink-0"
        >
          <span className="flex h-3.5 w-3.5 items-center justify-center rounded-full bg-[#FF811A] text-[10px] text-white font-bold">
            <Sliders className="w-2.5 h-2.5" />
          </span>
          Prompt 在线工坊
        </Link>
        <Link
          to="/crm"
          className="flex h-7 items-center gap-1.5 rounded-[6px] border border-[#DEE0E3] bg-white px-2.5 text-[12px] text-[#1F2329] transition-colors hover:border-[#3370FF] hover:text-[#3370FF] shrink-0"
        >
          <span className="flex h-3.5 w-3.5 items-center justify-center rounded-full bg-[#10A893] text-[10px] text-white font-bold">
            <TableProperties className="w-2.5 h-2.5" />
          </span>
          CRM 调样演示
        </Link>
      </div>

      {/* 3. 核心看板网格 (1:1 飞书 CRM 3 + 1 经典黄金结构，圆角统一 12px) */}
      <div className="grid grid-cols-1 gap-3.5 lg:grid-cols-4">
        {/* 左侧 3 栏：效能与趋势指标卡 */}
        <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-3 lg:col-span-3">
          {/* 指标卡 1: 模型网关调用量 */}
          <div className="flex flex-col justify-between rounded-[12px] border border-[#DEE0E3] bg-white p-4 shadow-2xs">
            <div>
              <div className="text-[13px] text-[#646A73]">模型网关调用量</div>
              <div className="mt-1.5 text-[28px] font-bold text-[#1F2329] tracking-tight tabular-nums">
                326,580
              </div>
            </div>
            <div className="mt-3 flex items-end justify-between">
              <div className="flex items-center gap-1.5 text-[12px] text-[#10A893] font-medium">
                <span className="text-[#8F959E] font-normal">比上周</span>
                <span>▲ 18.42%</span>
              </div>
              <Sparkline1 />
            </div>
          </div>

          {/* 指标卡 2: 知识库检索总量 */}
          <div className="flex flex-col justify-between rounded-[12px] border border-[#DEE0E3] bg-white p-4 shadow-2xs">
            <div>
              <div className="text-[13px] text-[#646A73]">知识库检索总量</div>
              <div className="mt-1.5 text-[28px] font-bold text-[#1F2329] tracking-tight tabular-nums">
                89,420
              </div>
            </div>
            <div className="mt-3 flex items-end justify-between">
              <div className="flex items-center gap-1.5 text-[12px] text-[#10A893] font-medium">
                <span className="text-[#8F959E] font-normal">比上周</span>
                <span>▲ 24.15%</span>
              </div>
              <Sparkline2 />
            </div>
          </div>

          {/* 指标卡 3: 智能对话消息数 */}
          <div className="flex flex-col justify-between rounded-[12px] border border-[#DEE0E3] bg-white p-4 shadow-2xs">
            <div>
              <div className="text-[13px] text-[#646A73]">智能对话消息数</div>
              <div className="mt-1.5 text-[28px] font-bold text-[#1F2329] tracking-tight tabular-nums">
                14,230
              </div>
            </div>
            <div className="mt-3 flex items-end justify-between">
              <div className="flex items-center gap-1.5 text-[12px] text-[#10A893] font-medium">
                <span className="text-[#8F959E] font-normal">比上周</span>
                <span>▲ 12.80%</span>
              </div>
              <Sparkline3 />
            </div>
          </div>
        </div>

        {/* 右侧第 4 栏：飞书原版环形状态分布卡片 (Donut Chart) */}
        <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-4 shadow-2xs flex flex-col justify-between">
          <div className="text-[13px] font-semibold text-[#1F2329]">知识库切片状态分布</div>
          <div className="flex items-center gap-4 my-auto py-1">
            {/* CSS Conic 渐变 Donut 环形图 */}
            <div
              className="relative w-[84px] h-[84px] rounded-full shrink-0 flex items-center justify-center"
              style={{
                background:
                  "conic-gradient(#3370ff 0% 72%, #35bd4b 72% 92%, #8d55ed 92% 100%)",
              }}
            >
              <div className="w-[52px] h-[52px] rounded-full bg-white flex items-center justify-center">
                <span className="text-[11px] font-bold text-[#646A73]">1.28k</span>
              </div>
            </div>
            {/* 飞书 1:1 图例文本与数值 */}
            <div className="flex flex-col gap-1.5 text-[12px] text-[#646A73] flex-1">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-[#3370FF] shrink-0" />
                  <span>已向量索引</span>
                </div>
                <span className="font-semibold text-[#1F2329] tabular-nums">920</span>
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-[#35BD4B] shrink-0" />
                  <span>解析就绪</span>
                </div>
                <span className="font-semibold text-[#1F2329] tabular-nums">260</span>
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-[#8D55ED] shrink-0" />
                  <span>同步中</span>
                </div>
                <span className="font-semibold text-[#1F2329] tabular-nums">100</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 4. 实体状态计数卡 (1:1 飞书 CRM 图三 4 栏纯净白底卡片，圆角 12px) */}
      <div className="grid grid-cols-2 gap-3.5 lg:grid-cols-4">
        <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-4 shadow-2xs">
          <div className="text-[13px] text-[#646A73]">文档总数</div>
          <div className="mt-2 text-[30px] font-bold text-[#1F2329] tracking-tight tabular-nums">
            48
          </div>
        </div>

        <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-4 shadow-2xs">
          <div className="text-[13px] text-[#646A73]">向量索引分块 (Chunks)</div>
          <div className="mt-2 text-[30px] font-bold text-[#1F2329] tracking-tight tabular-nums">
            1,280
          </div>
        </div>

        <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-4 shadow-2xs">
          <div className="text-[13px] text-[#646A73]">已接入模型数</div>
          <div className="mt-2 text-[30px] font-bold text-[#1F2329] tracking-tight tabular-nums">
            12
          </div>
        </div>

        <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-4 shadow-2xs">
          <div className="text-[13px] text-[#646A73]">Prompt 模板数</div>
          <div className="mt-2 text-[30px] font-bold text-[#1F2329] tracking-tight tabular-nums">
            16
          </div>
        </div>
      </div>
    </div>
  );
};

import React, { useState, useEffect, useRef } from "react";
import { Link, useLocation } from "react-router-dom";
import {
  Home,
  MessageSquare,
  BookOpen,
  Cpu,
  Sliders,
  TableProperties,
  GitFork,
  ShieldCheck,
  Sparkles,
  Settings2,
  Search,
} from "lucide-react";
import { cn } from "../../lib/utils";
import { FeishuTopNav } from "./FeishuTopNav";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "../ui/tooltip";
import { useAuthStore } from "../../features/auth/store/authStore";

interface AppShellProps {
  children: React.ReactNode;
}

interface NavItemProps {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  isSubItem?: boolean;
  onClick?: () => void;
}

const NavItem: React.FC<NavItemProps> = ({ to, label, icon: Icon, isSubItem, onClick }) => {
  const location = useLocation();
  const currentPath = location.pathname;
  const isActive =
    currentPath === to ||
    (to === "/home" && currentPath === "/") ||
    (to !== "/home" && currentPath.startsWith(to + "/"));

  return (
    <Link
      to={to}
      onClick={onClick}
      className={cn(
        "relative mx-1.5 flex h-9 items-center justify-between rounded-[6px] px-2.5 text-[14px] leading-[22px] transition-colors duration-150 select-none cursor-pointer",
        isSubItem ? "pl-7" : "pl-2.5",
        isActive
          ? "bg-[#EBEDF0] font-normal text-[#1F2329]"
          : "text-[#1F2329] hover:bg-[rgba(31,35,41,0.04)] hover:text-[#1F2329]"
      )}
    >
      <div className="flex min-w-0 items-center gap-2">
        <Icon
          className={cn(
            "w-4 h-4 shrink-0 stroke-[1.6]",
            isActive ? "text-[#1F2329]" : "text-[#646A73]"
          )}
        />
        <span className="truncate">{label}</span>
      </div>
    </Link>
  );
};

interface NavGroupProps {
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  defaultOpen?: boolean;
  prefix: string;
  items: { to: string; label: string; icon: React.ComponentType<{ className?: string }> }[];
  onItemClick?: () => void;
}

const NavGroup: React.FC<NavGroupProps> = ({ label, icon: Icon, defaultOpen = true, prefix, items, onItemClick }) => {
  const location = useLocation();
  const currentPath = location.pathname;
  const hasActiveChild = currentPath.startsWith(prefix);
  const [isOpen, setIsOpen] = useState(defaultOpen || hasActiveChild);

  useEffect(() => {
    if (hasActiveChild) {
      setIsOpen(true);
    }
  }, [hasActiveChild]);

  return (
    <div className="flex flex-col space-y-0.5">
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        className="mx-1.5 flex h-9 items-center justify-between rounded-[6px] px-1.5 text-[14px] leading-[22px] text-[#1F2329] transition-colors duration-150 select-none hover:bg-[rgba(31,35,41,0.04)] cursor-pointer"
      >
        <div className="flex min-w-0 items-center gap-1.5">
          {/* 小三角折叠 caret (图一 1:1 标准样式) */}
          <span className="flex w-3.5 h-3.5 items-center justify-center">
            <svg
              className={cn(
                "w-2.5 h-2.5 text-[#8F959E] fill-current transition-transform duration-150",
                isOpen ? "rotate-0" : "-rotate-90"
              )}
              viewBox="0 0 10 10"
            >
              <polygon points="1,3 9,3 5,8" />
            </svg>
          </span>
          <Icon className="w-4 h-4 shrink-0 text-[#646A73] stroke-[1.6]" />
          <span className="truncate font-normal">{label}</span>
        </div>
      </button>

      {isOpen && (
        <div className="flex flex-col space-y-0.5">
          {items.map((item) => (
            <NavItem
              key={item.to}
              to={item.to}
              label={item.label}
              icon={item.icon}
              isSubItem
              onClick={onItemClick}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export const AppShell: React.FC<AppShellProps> = ({ children }) => {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [isHovered, setIsHovered] = useState(false);
  const location = useLocation();
  const currentPath = location.pathname;
  const hoverTimerRef = useRef<NodeJS.Timeout | null>(null);

  const handleMouseEnter = () => {
    if (!sidebarOpen) {
      if (hoverTimerRef.current) clearTimeout(hoverTimerRef.current);
      setIsHovered(true);
    }
  };

  const handleMouseLeave = () => {
    if (!sidebarOpen) {
      if (hoverTimerRef.current) clearTimeout(hoverTimerRef.current);
      hoverTimerRef.current = setTimeout(() => {
        setIsHovered(false);
      }, 100);
    }
  };

  const { hasPermission } = useAuthStore();

  // 收起时的一级图标列表 (图二 1:1 对标，依据权限动态过滤)
  const collapsedNavItems = [
    { id: "home", label: "首页", icon: Home, path: "/home", active: currentPath === "/home" || currentPath === "/" },
    { id: "chat", label: "对话", icon: MessageSquare, path: "/chat", active: currentPath.startsWith("/chat") },
    { id: "knowledge", label: "知识库", icon: BookOpen, path: "/knowledge-base", active: currentPath.startsWith("/knowledge") || currentPath.startsWith("/documents") },
    ...(hasPermission("model:manage")
      ? [
          { id: "models", label: "模型网关", icon: Cpu, path: "/models/configs", active: currentPath.startsWith("/models") },
        ]
      : []),
    ...(hasPermission("prompt:manage")
      ? [
          { id: "prompts", label: "提示词管理", icon: Sliders, path: "/prompts", active: currentPath.startsWith("/prompts") },
        ]
      : []),
    ...(hasPermission("crm:view")
      ? [
          { id: "crm", label: "CRM 表格演示", icon: TableProperties, path: "/crm", active: currentPath.startsWith("/crm") },
        ]
      : []),
  ];

  // 渲染完整的侧边栏内容
  const renderFullSidebarContent = (onItemClick?: () => void) => (
    <div className="flex flex-col h-full bg-[#F8F9FA]">
      {/* 飞书 1:1 顶部搜索栏 (图一规范) */}
      <div className="p-2 pb-1.5">
        <div className="flex h-8 w-full items-center gap-2 rounded-[6px] bg-[rgba(31,35,41,0.05)] px-2.5 text-[13px] text-[#646A73] transition-colors focus-within:bg-white focus-within:ring-1 focus-within:ring-[#3370FF]">
          <Search className="w-3.5 h-3.5 text-[#8F959E] shrink-0" />
          <input
            type="text"
            placeholder="搜索"
            className="w-full bg-transparent outline-none placeholder:text-[#8F959E] text-[#1F2329]"
          />
        </div>
      </div>

      {/* 垂直树状菜单导航 */}
      <nav className="p-1.5 pt-0.5 space-y-0.5 overflow-y-auto flex-1 text-[14px]">
        {/* 🏠 首页 */}
        <NavItem to="/home" label="首页" icon={Home} onClick={onItemClick} />

        {/* 💬 对话 */}
        <NavItem to="/chat" label="对话" icon={MessageSquare} onClick={onItemClick} />

        {/* 📚 知识库 */}
        <NavItem to="/knowledge-base" label="知识库" icon={BookOpen} onClick={onItemClick} />

        {hasPermission("model:manage") && (
          <>
            {/* ▾ 🖧 模型网关 */}
            <NavGroup
              label="模型网关"
              icon={Cpu}
              prefix="/models"
              onItemClick={onItemClick}
              items={[
                { to: "/models/configs", label: "LLM 模型配置", icon: Settings2 },
                { to: "/models/routes", label: "智能路由决策", icon: GitFork },
                { to: "/models/governance", label: "熔断限流治理", icon: ShieldCheck },
              ]}
            />

          </>
        )}

        {hasPermission("prompt:manage") && (
          <>
            {/* ▾ 📝 提示词管理 */}
            <NavGroup
              label="提示词管理"
              icon={Sliders}
              prefix="/prompts"
              onItemClick={onItemClick}
              items={[
                { to: "/prompts", label: "Prompt 在线工坊", icon: Sparkles },
              ]}
            />

          </>
        )}

        {hasPermission("crm:view") && (
          <>
            {/* ⚡ CRM 表格演示 */}
            <NavItem to="/crm" label="CRM 表格演示" icon={TableProperties} onClick={onItemClick} />
          </>
        )}
      </nav>
    </div>
  );

  return (
    <div className="flex flex-col h-screen w-screen overflow-hidden bg-white text-[#1F2329]">
      {/* 1:1 飞书官方纯白顶栏 */}
      <FeishuTopNav
        sidebarOpen={sidebarOpen}
        onToggleSidebar={() => {
          setSidebarOpen((prev) => !prev);
          setIsHovered(false);
        }}
      />

      {/* 主布局 */}
      <div className="relative flex flex-1 overflow-hidden bg-white">
        {/* A. 固化展开状态 (宽度 220px，常态推移主内容区) */}
        {sidebarOpen && (
          <aside className="w-[220px] border-r border-[#DEE0E3] bg-[#F8F9FA] flex flex-col shrink-0 transition-all duration-200 select-none overflow-hidden z-20">
            {renderFullSidebarContent()}
          </aside>
        )}

        {/* B. 收起状态 (宽度 52px 纯图标窄栏 + 鼠标悬浮弹出 220px 完整侧栏) */}
        {!sidebarOpen && (
          <div
            className="relative flex shrink-0 z-30"
            onMouseEnter={handleMouseEnter}
            onMouseLeave={handleMouseLeave}
          >
            {/* 52px 收起纯图标侧栏 (1:1 图二飞书规范) */}
            <aside className="w-[52px] border-r border-[#DEE0E3] bg-[#F8F9FA] flex flex-col items-center py-2 space-y-1.5 shrink-0 select-none h-full">
              {/* 顶部搜索圆形小图标 */}
              <div className="flex items-center justify-center w-8 h-8 rounded-full bg-[rgba(31,35,41,0.06)] text-[#646A73] mb-1 cursor-pointer hover:bg-[rgba(31,35,41,0.1)] transition-colors">
                <Search className="w-4 h-4 text-[#646A73]" />
              </div>

              {/* 垂直一级图标 */}
              <TooltipProvider delayDuration={150}>
                {collapsedNavItems.map((item) => {
                  const Icon = item.icon;
                  return (
                    <Tooltip key={item.id}>
                      <TooltipTrigger asChild>
                        <Link
                          to={item.path}
                          className={cn(
                            "w-8 h-8 rounded-[6px] flex items-center justify-center transition-colors duration-150 cursor-pointer",
                            item.active
                              ? "bg-[#EBEDF0] text-[#1F2329]"
                              : "text-[#646A73] hover:bg-[rgba(31,35,41,0.04)] hover:text-[#1F2329]"
                          )}
                        >
                          <Icon className="w-4 h-4 stroke-[1.6]" />
                        </Link>
                      </TooltipTrigger>
                      <TooltipContent side="right" sideOffset={8} className="text-xs bg-[#1F2329] text-white">
                        {item.label}
                      </TooltipContent>
                    </Tooltip>
                  );
                })}
              </TooltipProvider>
            </aside>

            {/* 鼠标悬浮时弹出的完整侧栏浮层 (宽度 220px，覆盖在主内容区上方，离开即消失) */}
            {isHovered && (
              <div
                className="absolute left-0 top-0 bottom-0 w-[220px] bg-[#F8F9FA] border-r border-[#DEE0E3] shadow-[4px_0_24px_rgba(0,0,0,0.08)] z-50 flex flex-col animate-in fade-in slide-in-from-left-2 duration-150"
                onMouseEnter={handleMouseEnter}
                onMouseLeave={handleMouseLeave}
              >
                {renderFullSidebarContent(() => setIsHovered(false))}
              </div>
            )}
          </div>
        )}

        {/* 主内容区 (chat 对话页全屏铺满 p-0，其他页面保持 p-5) */}
        <main
          className={cn(
            "flex-1 bg-white",
            location.pathname.startsWith("/chat") ? "p-0 overflow-hidden flex flex-col" : "overflow-auto p-5"
          )}
        >
          {children}
        </main>
      </div>
    </div>
  );
};

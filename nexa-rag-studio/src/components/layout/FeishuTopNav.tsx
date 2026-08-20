import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "../ui/tooltip";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "../ui/dropdown-menu";

// --- 飞书原装 1:1 Universe Design 顶栏矢量图标 ---

export const RightBoldOutlinedIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" data-icon="RightBoldOutlined" className="shrink-0 text-[#8F959E]">
    <path d="m7.586 20.486.707.707a1 1 0 0 0 1.414 0l7.778-7.778a2 2 0 0 0 0-2.829L9.707 2.808a1 1 0 0 0-1.414 0l-.707.707a1 1 0 0 0 0 1.414l7.07 7.072-7.07 7.07a1 1 0 0 0 0 1.415Z" fill="currentColor" />
  </svg>
);

export const CheckOutlinedIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" className="shrink-0 text-[#3370FF]">
    <path d="m9 16.17 9.59-9.59L20 8l-11 11-5-5 1.41-1.41L9 16.17Z" fill="currentColor" />
  </svg>
);

export const SideExpandOutlinedIcon = ({ className = "" }: { className?: string }) => (
  <svg
    width="18"
    height="18"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    data-icon="SideExpandOutlined"
    className={`shrink-0 ${className}`}
  >
    <path
      d="M22 4a1 1 0 0 0-1-1H3a1 1 0 0 0 0 2h18a1 1 0 0 0 1-1Zm-11.111 7c.614 0 1.111.448 1.111 1s-.498 1-1.111 1H3.11C2.497 13 2 12.552 2 12s.497-1 1.111-1h7.778ZM12 20c0-.552-.498-1-1.111-1H3.11C2.497 19 2 19.448 2 20s.497 1 1.111 1h7.778c.614 0 1.111-.448 1.111-1Zm3.41-3.136a1.117 1.117 0 0 1 0-1.729l4.951-3.917c.675-.534 1.639-.026 1.639.865v7.834c0 .89-.964 1.4-1.639.865l-4.951-3.918Z"
      fill="currentColor"
    />
  </svg>
);

export const FeishuBitableLogo = () => (
  <svg width="22" height="22" viewBox="0 0 22 22" fill="none" className="shrink-0">
    <path
      d="M4.88 11L2 14.72 7.28 20 11 17.12 14.72 20 20 14.72 17.12 11 20 7.28 14.72 2 11 4.88 7.28 2 2 7.28 4.88 11zM11 4.88L17.12 11 11 17.12 4.88 11 11 4.88z"
      fill="#8046F3"
    />
  </svg>
);

export const FileLinkBitableOutlinedIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" data-icon="FileLinkBitableOutlined" className="shrink-0 text-[#8046F3]">
    <path
      d="M16.595 3H4v14.823c0 .814.337 1.613.966 2.216a3.53 3.53 0 0 0 2.44.961H20V6.176a3.07 3.07 0 0 0-.966-2.215A3.53 3.53 0 0 0 16.593 3ZM2 2a1 1 0 0 1 1-1h13.595a5.53 5.53 0 0 1 3.822 1.516A5.068 5.068 0 0 1 22 6.176V22a1 1 0 0 1-1 1H7.405a5.529 5.529 0 0 1-3.822-1.516A5.068 5.068 0 0 1 2 17.824V2Zm13.74 10L12 8.26l2.275-1.76L17.5 9.725 15.74 12ZM12 15.735 15.74 12l1.76 2.275-3.225 3.225L12 15.735ZM8.26 12 6.5 9.725 9.725 6.5 12 8.26 8.26 12Zm0 0L6.5 14.275 9.725 17.5 12 15.735 8.26 12Z"
      fill="currentColor"
    />
  </svg>
);

export const BaseAppDefaultOutlinedIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" data-icon="BaseAppDefaultOutlined" className="shrink-0 text-[#1456F0]">
    <path
      d="M7.432 15.04c.391.153.843.304 1.346.438l-.883 1.77a1 1 0 0 1-1.79-.896l.773-1.545c.17.076.354.155.554.233Zm4.121-9.135a1.001 1.001 0 0 1 1.341.448l5 10a1 1 0 0 1-1.788.894l-5-10a1 1 0 0 1 .447-1.342Zm-5.879 6.331a1 1 0 0 1 1.396-.259l.055.036a8.357 8.357 0 0 0 1.21.589l.041.016a9.99 9.99 0 0 0 1.597.47l-.001.002a9.74 9.74 0 0 0 3.436.106l.935 1.87c-.703.14-1.489.234-2.343.234a11.982 11.982 0 0 1-3.823-.63l-.173-.059a10.866 10.866 0 0 1-1.588-.699 7.473 7.473 0 0 1-.436-.257l-.028-.018-.01-.007-.003-.002-.002-.001v-.001a1 1 0 0 1-.263-1.39Zm11.264-.263a1 1 0 0 1 1.126 1.653l-.002.001h-.002l-.002.003-.01.007-.028.018a7.5 7.5 0 0 1-.436.257 7.663 7.663 0 0 1-.19.1l-.896-1.791c.045-.024.09-.044.128-.065.112-.061.196-.11.249-.143l.055-.036.008-.004Z"
      fill="currentColor"
    />
    <path d="m10.567 7.516 1.096 2.192-1.412 2.823a9.453 9.453 0 0 1-1.941-.589l2.237-4.473.02.047Z" fill="currentColor" />
    <path
      d="M16.595 1a5.53 5.53 0 0 1 3.822 1.517A5.068 5.068 0 0 1 22 6.177V22a1 1 0 0 1-1 1H7.405a5.53 5.53 0 0 1-3.822-1.517A5.068 5.068 0 0 1 2 17.823V2a1 1 0 0 1 1-1h13.595ZM4 17.823c0 .814.337 1.613.967 2.216A3.526 3.526 0 0 0 7.405 21H20V6.177c0-.814-.337-1.613-.967-2.216A3.529 3.529 0 0 0 16.595 3H4v14.823Z"
      fill="currentColor"
    />
  </svg>
);

export const BaseAgentChatbotOutlinedIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" data-icon="BaseAgentChatbotOutlined" className="shrink-0 text-[#10A893]">
    <path d="M10 12.5a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0Zm6 0a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0Zm2 1.5c.153 0 .278.12.298.273.048.381.136.75.261 1.1l.175.43a5.182 5.182 0 0 0 2.487 2.518l.423.177.138.048c.324.108.663.184 1.012.225A.232.232 0 0 1 23 19l-.004.043a.232.232 0 0 1-.202.186l-.053.007a5.01 5.01 0 0 0-1.097.266l-.423.177a5.182 5.182 0 0 0-2.487 2.519l-.175.428a5.22 5.22 0 0 0-.261 1.101l-.012.056A.303.303 0 0 1 18 24l-.056-.006a.306.306 0 0 1-.23-.211l-.012-.056a5.22 5.22 0 0 0-.261-1.1l-.175-.43a5.182 5.182 0 0 0-2.487-2.518l-.423-.177a5.048 5.048 0 0 0-1.001-.254l-.15-.02A.232.232 0 0 1 13 19c0-.118.09-.215.206-.229.349-.04.688-.117 1.012-.225l.138-.048.423-.177a5.182 5.182 0 0 0 2.487-2.519l.175-.428a5.22 5.22 0 0 0 .261-1.101A.306.306 0 0 1 18 14Z" fill="currentColor" />
    <path d="M15 1a1 1 0 0 1 1 1v2h1a5 5 0 0 1 5 5v5a1 1 0 1 1-2 0V9a3 3 0 0 0-3-3H6a3 3 0 0 0-3 3v8a3 3 0 0 0 3 3h5a1 1 0 1 1 0 2H6a5 5 0 0 1-5-5V9a5 5 0 0 1 5-5V2a1 1 0 0 1 2 0v2h6V2a1 1 0 0 1 1-1Z" fill="currentColor" />
  </svg>
);

export const MoreAddOutlinedIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" data-icon="MoreAddOutlined" className="shrink-0 text-[#1F2329]">
    <path d="M11 11V8a1 1 0 1 1 2 0v3h3a1 1 0 1 1 0 2h-3v3a1 1 0 1 1-2 0v-3H8a1 1 0 1 1 0-2h3Zm1 10a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0 2C5.925 23 1 18.075 1 12S5.925 1 12 1s11 4.925 11 11-4.925 11-11 11Z" fill="currentColor" />
  </svg>
);

export const MaybeOutlinedIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" data-icon="MaybeOutlined" className="shrink-0 text-[#646A75] hover:text-[#1F2329]">
    <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0 2C5.925 23 1 18.075 1 12S5.925 1 12 1s11 4.925 11 11-4.925 11-11 11Zm-1-6a1 1 0 1 1 2 0 1 1 0 0 1-2 0ZM8.05 9.282a5.17 5.17 0 0 1 .039-.28c.195-1.085.689-1.883 1.481-2.394.62-.405 1.383-.608 2.288-.608 1.189 0 2.176.288 2.962.864.787.575 1.18 1.428 1.18 2.558 0 .693-.17 1.277-.513 1.752-.2.287-.584.655-1.152 1.103l-.56.44c-.305.24-.507.52-.607.84a2.742 2.742 0 0 0-.072.486.5.5 0 0 1-.498.457h-1.12a.5.5 0 0 1-.498-.546c.065-.696.134-1.136.207-1.321.137-.344.49-.74 1.058-1.188l.575-.455c.19-.144 1.166-.831 1.166-1.44 0-.608-.106-.832-.412-1.166-.305-.333-.993-.44-1.613-.44-.61 0-1.132.161-1.387.572-.118.19-.215.393-.284.6a2.097 2.097 0 0 0-.073.307.5.5 0 0 1-.493.415H8.547a.5.5 0 0 1-.497-.556Z" fill="currentColor" />
  </svg>
);

interface FeishuTopNavProps {
  sidebarOpen: boolean;
  onToggleSidebar: () => void;
}

// 1:1 飞书官方顶栏 (根据 docs/design/飞书页面/index.html & style.css 64px 纯白 + 0.5px 细边框)
export const FeishuTopNav: React.FC<FeishuTopNavProps> = ({
  sidebarOpen,
  onToggleSidebar,
}) => {
  const navigate = useNavigate();
  const [theme, setTheme] = useState<"light" | "dark" | "system">("light");
  const [lang, setLang] = useState<string>("zh-CN");

  // 绑定 Ctrl + ] / Cmd + ] 快捷键收起/展开侧边栏
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === "]") {
        e.preventDefault();
        onToggleSidebar();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onToggleSidebar]);

  return (
    <header className="h-16 w-full bg-white border-b border-[rgba(31,35,41,0.15)] pl-5 pr-4 flex items-center justify-between select-none z-30 shrink-0">
      {/* 左侧：抽屉折叠展开按钮 + 飞书多维表格紫色 Logo + Nexa-RAG 品牌名称 */}
      <div className="flex items-center gap-3">
        <TooltipProvider disableHoverableContent={true} delayDuration={120} skipDelayDuration={0}>
          <Tooltip disableHoverableContent={true}>
            <TooltipTrigger asChild>
              <button
                type="button"
                onClick={onToggleSidebar}
                aria-label={sidebarOpen ? "收起侧边栏" : "展开侧边栏"}
                className="w-8 h-8 rounded-[6px] text-[#1F2329] hover:bg-[rgba(31,35,41,0.08)] active:bg-[rgba(31,35,41,0.12)] flex items-center justify-center transition-colors cursor-pointer"
              >
                <SideExpandOutlinedIcon className={sidebarOpen ? "" : "scale-x-[-1]"} />
              </button>
            </TooltipTrigger>
            <TooltipContent side="bottom" align="center" sideOffset={6}>
              {sidebarOpen ? "收起 (Ctrl + ])" : "展开 (Ctrl + ])"}
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>

        <Link to="/" className="flex items-center gap-1 text-[#1F2329] hover:opacity-90 transition-opacity">
          <FeishuBitableLogo />
          <span className="text-[18px] font-medium leading-[28px] text-[#1F2329] tracking-tight">
            飞书多维表格
          </span>
          <span className="text-xs text-[#8F959E] font-normal ml-1">
            · Nexa-RAG Studio
          </span>
        </Link>
      </div>

      {/* 右侧：多维表格 / 应用 / 智能体 / 新建 快捷入口 + 26px分割线 + 问号帮助 + 头像 */}
      <div className="flex items-center gap-2">
        {/* 多维表格 */}
        <button
          type="button"
          onClick={() => navigate("/crm")}
          className="h-8 px-2 rounded-[6px] hover:bg-[rgba(31,35,41,0.08)] active:bg-[rgba(31,35,41,0.12)] text-[#1F2329] text-[14px] font-medium leading-[22px] flex items-center gap-1 transition-colors cursor-pointer"
        >
          <FileLinkBitableOutlinedIcon />
          <span>多维表格</span>
        </button>

        {/* 应用 */}
        <button
          type="button"
          onClick={() => navigate("/documents")}
          className="h-8 px-2 rounded-[6px] hover:bg-[rgba(31,35,41,0.08)] active:bg-[rgba(31,35,41,0.12)] text-[#1F2329] text-[14px] font-medium leading-[22px] flex items-center gap-1 transition-colors cursor-pointer"
        >
          <BaseAppDefaultOutlinedIcon />
          <span>应用</span>
        </button>

        {/* 智能体 */}
        <button
          type="button"
          onClick={() => navigate("/chat")}
          className="h-8 px-2 rounded-[6px] hover:bg-[rgba(31,35,41,0.08)] active:bg-[rgba(31,35,41,0.12)] text-[#1F2329] text-[14px] font-medium leading-[22px] flex items-center gap-1 transition-colors cursor-pointer"
        >
          <BaseAgentChatbotOutlinedIcon />
          <span>智能体</span>
        </button>

        {/* 新建 */}
        <button
          type="button"
          onClick={() => navigate("/crm")}
          className="h-8 px-2 rounded-[6px] hover:bg-[rgba(31,35,41,0.08)] active:bg-[rgba(31,35,41,0.12)] text-[#1F2329] text-[14px] font-medium leading-[22px] flex items-center gap-1 transition-colors cursor-pointer"
        >
          <MoreAddOutlinedIcon />
          <span>新建</span>
        </button>

        {/* 垂直分割线 (官方 26px 高度) */}
        <div className="h-[26px] w-[1px] bg-[rgba(31,35,41,0.15)] mx-1 pointer-events-none" />

        {/* 帮助中心 */}
        <TooltipProvider disableHoverableContent={true} delayDuration={120} skipDelayDuration={0}>
          <Tooltip disableHoverableContent={true}>
            <TooltipTrigger asChild>
              <button
                type="button"
                className="w-8 h-8 rounded-[6px] text-[#646A75] hover:text-[#1F2329] hover:bg-[rgba(31,35,41,0.08)] active:bg-[rgba(31,35,41,0.12)] flex items-center justify-center transition-colors cursor-pointer"
              >
                <MaybeOutlinedIcon />
              </button>
            </TooltipTrigger>
            <TooltipContent side="bottom" align="center" sideOffset={6}>
              帮助中心
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>

        {/* 垂直分割线 (官方 26px 高度) */}
        <div className="h-[26px] w-[1px] bg-[rgba(31,35,41,0.15)] mx-1 pointer-events-none" />

        {/* 用户头像 (官方 32px 直径 + 点击弹出 240px 个人资料卡片) */}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              aria-label="个人信息与设置"
              className="w-8 h-8 rounded-full overflow-hidden border border-[rgba(31,35,41,0.15)] hover:opacity-85 active:opacity-75 transition-opacity cursor-pointer select-none outline-none focus:ring-2 focus:ring-[#3370FF] focus:ring-offset-1 shrink-0 p-0 bg-white"
            >
              <img
                src="https://s3-imfile.feishucdn.com/static-resource/v1/v3_00va_9e2a25c5-90f3-4115-bd7d-82c8706f732g~?image_size=noop&cut_type=&quality=&format=image&sticker_format=.webp"
                alt="用户头像"
                className="w-full h-full object-cover"
              />
            </button>
          </DropdownMenuTrigger>

          <DropdownMenuContent
            sideOffset={6}
            align="end"
            className="w-[240px] p-0 rounded-[12px] bg-white border border-[rgba(31,35,41,0.12)] shadow-[0_8px_24px_rgba(31,35,41,0.12),0_2px_8px_rgba(31,35,41,0.04)] overflow-hidden z-50 text-[#1F2329] select-none"
          >
            {/* 顶部个人信息区 (padding: 16px 16px 12px 16px) */}
            <div className="pt-4 px-4 pb-3 flex items-center gap-3 bg-white">
              <div className="w-10 h-10 rounded-full overflow-hidden border border-[rgba(31,35,41,0.1)] shrink-0">
                <img
                  src="https://s3-imfile.feishucdn.com/static-resource/v1/v3_00va_9e2a25c5-90f3-4115-bd7d-82c8706f732g~?image_size=noop&cut_type=&quality=&format=image&sticker_format=.webp"
                  alt="用户头像"
                  className="w-full h-full object-cover"
                />
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-[16px] font-medium text-[#1F2329] truncate leading-6">
                  朱亮(小小亮)
                </div>
                <div className="text-[12px] text-[#8F959E] truncate leading-[18px] mt-0.5">
                  飞书个人用户
                </div>
              </div>
            </div>

            <DropdownMenuSeparator className="my-0" />

            {/* 菜单列表区 */}
            <div className="p-1.5">
              {/* 外观 (二级展开项) */}
              <DropdownMenuSub>
                <DropdownMenuSubTrigger className="flex items-center justify-between px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] text-[#1F2329] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer">
                  <span>外观</span>
                  <div className="flex items-center gap-1">
                    <span className="text-[14px] text-[#646A75] leading-[22px]">
                      {theme === "light" ? "浅色" : theme === "dark" ? "深色" : "跟随系统"}
                    </span>
                    <RightBoldOutlinedIcon />
                  </div>
                </DropdownMenuSubTrigger>
                <DropdownMenuSubContent
                  sideOffset={4}
                  alignOffset={-6}
                  className="w-[160px] p-1.5 rounded-[12px] bg-white border border-[rgba(31,35,41,0.12)] shadow-[0_8px_24px_rgba(31,35,41,0.12),0_2px_8px_rgba(31,35,41,0.04)]"
                >
                  <DropdownMenuItem
                    onClick={() => setTheme("light")}
                    className="flex items-center justify-between px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer"
                  >
                    <span>浅色</span>
                    {theme === "light" && <CheckOutlinedIcon />}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={() => setTheme("dark")}
                    className="flex items-center justify-between px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer"
                  >
                    <span>深色</span>
                    {theme === "dark" && <CheckOutlinedIcon />}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={() => setTheme("system")}
                    className="flex items-center justify-between px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer"
                  >
                    <span>跟随系统</span>
                    {theme === "system" && <CheckOutlinedIcon />}
                  </DropdownMenuItem>
                </DropdownMenuSubContent>
              </DropdownMenuSub>

              {/* 语言 (二级展开项) */}
              <DropdownMenuSub>
                <DropdownMenuSubTrigger className="flex items-center justify-between px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] text-[#1F2329] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer">
                  <span>语言</span>
                  <RightBoldOutlinedIcon />
                </DropdownMenuSubTrigger>
                <DropdownMenuSubContent
                  sideOffset={4}
                  alignOffset={-6}
                  className="w-[160px] p-1.5 rounded-[12px] bg-white border border-[rgba(31,35,41,0.12)] shadow-[0_8px_24px_rgba(31,35,41,0.12),0_2px_8px_rgba(31,35,41,0.04)]"
                >
                  <DropdownMenuItem
                    onClick={() => setLang("zh-CN")}
                    className="flex items-center justify-between px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer"
                  >
                    <span>简体中文</span>
                    {lang === "zh-CN" && <CheckOutlinedIcon />}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={() => setLang("en-US")}
                    className="flex items-center justify-between px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer"
                  >
                    <span>English</span>
                    {lang === "en-US" && <CheckOutlinedIcon />}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={() => setLang("ja-JP")}
                    className="flex items-center justify-between px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer"
                  >
                    <span>日本語</span>
                    {lang === "ja-JP" && <CheckOutlinedIcon />}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={() => setLang("zh-TW")}
                    className="flex items-center justify-between px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer"
                  >
                    <span>繁體中文</span>
                    {lang === "zh-TW" && <CheckOutlinedIcon />}
                  </DropdownMenuItem>
                </DropdownMenuSubContent>
              </DropdownMenuSub>

              <DropdownMenuSeparator className="my-1" />

              {/* 切换账号 (二级展开项) */}
              <DropdownMenuSub>
                <DropdownMenuSubTrigger className="flex items-center justify-between px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] text-[#1F2329] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer">
                  <span>切换账号</span>
                  <RightBoldOutlinedIcon />
                </DropdownMenuSubTrigger>
                <DropdownMenuSubContent
                  sideOffset={4}
                  alignOffset={-6}
                  className="w-[190px] p-1.5 rounded-[12px] bg-white border border-[rgba(31,35,41,0.12)] shadow-[0_8px_24px_rgba(31,35,41,0.12),0_2px_8px_rgba(31,35,41,0.04)]"
                >
                  <DropdownMenuItem className="flex items-center justify-between px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer">
                    <div className="flex items-center gap-2 min-w-0">
                      <div className="w-5 h-5 rounded-full overflow-hidden border border-[rgba(31,35,41,0.1)] shrink-0">
                        <img
                          src="https://s3-imfile.feishucdn.com/static-resource/v1/v3_00va_9e2a25c5-90f3-4115-bd7d-82c8706f732g~?image_size=noop&cut_type=&quality=&format=image&sticker_format=.webp"
                          alt="用户头像"
                          className="w-full h-full object-cover"
                        />
                      </div>
                      <span className="truncate">朱亮(小小亮)</span>
                    </div>
                    <CheckOutlinedIcon />
                  </DropdownMenuItem>
                  <DropdownMenuSeparator className="my-1" />
                  <DropdownMenuItem className="px-3 py-2 rounded-[6px] h-10 text-[13px] text-[#3370FF] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer">
                    <span>+ 添加其他账号</span>
                  </DropdownMenuItem>
                </DropdownMenuSubContent>
              </DropdownMenuSub>

              {/* 飞书管理后台 */}
              <DropdownMenuItem className="px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] text-[#1F2329] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer">
                <span>飞书管理后台</span>
              </DropdownMenuItem>

              <DropdownMenuSeparator className="my-1" />

              {/* 开发者中心 */}
              <DropdownMenuItem className="px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] text-[#1F2329] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer">
                <span>开发者中心</span>
              </DropdownMenuItem>

              {/* 订单管理 */}
              <DropdownMenuItem className="px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] text-[#1F2329] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer">
                <span>订单管理</span>
              </DropdownMenuItem>

              <DropdownMenuSeparator className="my-1" />

              {/* 回收站 */}
              <DropdownMenuItem className="px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] text-[#1F2329] hover:bg-[rgba(31,35,41,0.06)] cursor-pointer">
                <span>回收站</span>
              </DropdownMenuItem>
            </div>

            <DropdownMenuSeparator className="my-0" />

            {/* 底部退出登录 */}
            <div className="p-1.5">
              <DropdownMenuItem className="px-3 py-2 rounded-[6px] h-10 text-[14px] leading-[22px] text-[#1F2329] hover:bg-[rgba(31,35,41,0.06)] hover:text-[#F54A45] transition-colors cursor-pointer">
                <span>退出登录</span>
              </DropdownMenuItem>
            </div>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
};

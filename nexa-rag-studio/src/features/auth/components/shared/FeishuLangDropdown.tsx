import React, { useState, useRef, useEffect } from "react";
import { SupportedLang } from "../../types";

interface FeishuLangDropdownProps {
  currentLang?: SupportedLang;
  onSelectLang?: (lang: SupportedLang) => void;
  className?: string;
}

interface LangItem {
  key: SupportedLang;
  label: string;
}

const ALL_LANGUAGES: LangItem[] = [
  { key: "zh-CN", label: "简体中文" },
  { key: "en-US", label: "English" },
  { key: "ja-JP", label: "日本語" },
  { key: "zh-CN", label: "繁體中文（台灣）" },
  { key: "zh-CN", label: "繁體中文（香港）" },
  { key: "en-US", label: "Bahasa Indonesia" },
  { key: "en-US", label: "Bahasa Melayu" },
  { key: "en-US", label: "Deutsch" },
  { key: "en-US", label: "Español" },
  { key: "en-US", label: "Français" },
  { key: "en-US", label: "Italiano" },
  { key: "en-US", label: "Português (Brasil)" },
  { key: "en-US", label: "Русский" },
  { key: "en-US", label: "Tiếng Việt" },
  { key: "en-US", label: "ภาษาไทย" },
  { key: "en-US", label: "한국어" },
];

/**
 * 飞书/Lark 1:1 语言选择按钮与下拉弹出层
 *
 * 严格按照原版 CSS 规则实现：
 * - 容器位置：.login-com-lang { position: absolute; bottom: 20px; left: 20px; }
 * - 触发按钮：高度 24px, 左右内边距 4px, 悬浮底块 rgba(31,35,41,0.08), 圆角 4px
 * - 地球图标：精确 15px x 15px, 无额外 margin
 * - 语言文字：高度 22px, 行高 22px, 字号 14px, margin: 0 4px (与地球和箭头严格各相距 4px)
 * - 下拉箭头：精确 10px x 10px, 无额外 margin
 * - 下拉菜单：width: 185px, max-height: 250px, border-radius: 8px, box-shadow: 0 5px 13px rgba(31,35,41,0.12), 无外边框
 * - 列表选项：height: 32.5px, padding-left: 8px, margin: 0 4px, hover底块 rgba(31,35,41,0.08), 圆角 4px
 * - 选中状态：右侧绝对定位 20px 蓝色对勾图标
 */
export const FeishuLangDropdown: React.FC<FeishuLangDropdownProps> = ({
  currentLang = "zh-CN",
  onSelectLang,
  className = "",
}) => {
  const [open, setOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const currentLabel =
    ALL_LANGUAGES.find((o) => o.key === currentLang)?.label || "简体中文";

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleSelect = (lang: SupportedLang) => {
    onSelectLang?.(lang);
    setOpen(false);
  };

  return (
    <div
      ref={dropdownRef}
      className={`login-lang-box login-com-lang absolute bottom-[20px] left-[20px] z-30 select-none ${
        open ? "newLogin-lang-show" : ""
      } ${className}`}
    >
      <div className="newLogin_lang-box inline-block h-[24px]">
        <div className="newLogin_lang-selected-box">
          {/* 触发按钮：高度 24px，左右内边距 4px，圆角 4px */}
          <div
            onClick={() => setOpen(!open)}
            className="newLogin_lang-selected flex items-center h-[24px] px-[4px] rounded-[4px] cursor-pointer transition-colors duration-200 hover:bg-[rgba(31,35,41,0.08)] active:bg-[rgba(31,35,41,0.12)]"
          >
            {/* 地球图标：严格 15x15px，无额外 margin */}
            <i className="inline-flex items-center justify-center w-[15px] h-[15px] shrink-0">
              <svg width="15" height="15" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path
                  d="M7 .583a6.417 6.417 0 110 12.833A6.417 6.417 0 017 .583zM5.5 9.479c.129.602.3 1.135.5 1.575.492 1.083.933 1.196 1 1.196.067 0 .508-.113 1-1.196.2-.44.371-.973.5-1.575h-3zm-3.128 0A5.267 5.267 0 005.117 11.9c-.35-.65-.63-1.48-.81-2.422H2.373zm7.32 0c-.18.943-.46 1.771-.81 2.422a5.267 5.267 0 002.747-2.422H9.69zM1.914 5.687a5.26 5.26 0 000 2.625h2.23a13.97 13.97 0 010-2.625h-2.23zm3.403 0a12.739 12.739 0 000 2.625h3.364a12.736 12.736 0 000-2.625H5.318zm4.537 0a13.97 13.97 0 010 2.625h2.23a5.258 5.258 0 000-2.625h-2.23zm-4.737-3.59A5.268 5.268 0 002.37 4.522H4.31c.18-.944.459-1.772.809-2.423zM7 1.75c-.067 0-.508.113-1 1.195-.2.44-.371.973-.5 1.576h3A7.758 7.758 0 008 2.945C7.508 1.863 7.067 1.75 7 1.75zm1.882.348c.35.65.63 1.48.81 2.423h1.937a5.267 5.267 0 00-2.747-2.423z"
                  fill="#646A73"
                />
              </svg>
            </i>

            {/* 当前语言文字：高度 22px，行高 22px，左右外边距严格 4px */}
            <span className="h-[22px] leading-[22px] mx-[4px] text-[14px] text-[#646a73] whitespace-nowrap">
              {currentLabel}
            </span>

            {/* 下拉箭头：严格 10x10px，无额外 margin */}
            <i
              className={`inline-flex items-center justify-center w-[10px] h-[10px] shrink-0 transition-transform duration-200 origin-center ${
                open ? "rotate-180" : ""
              }`}
            >
              <svg width="10" height="10" viewBox="0 0 10 10" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path
                  d="M5 6.61l3.388-3.388a.208.208 0 01.295 0l.294.295a.208.208 0 010 .294L5.295 7.494a.417.417 0 01-.59 0L1.023 3.811a.208.208 0 010-.294l.294-.295a.208.208 0 01.295 0L5 6.61z"
                  fill="#646A73"
                />
              </svg>
            </i>
          </div>
        </div>
      </div>

      {/* 向上展开的语言选择气泡菜单 */}
      {open && (
        <div className="newLogin_lang-options newLogin_lang-options-up absolute bottom-[30px] left-0 w-[185px] overflow-hidden text-[14px] text-[#1f2329] bg-white rounded-[8px] shadow-[0_5px_13px_0_rgba(31,35,41,0.12)] z-50 animate-in fade-in zoom-in-95 duration-150">
          <ul className="newLogin_lang-options-list h-auto max-h-[250px] py-[5px] m-0 overflow-y-auto rounded-[4px] list-none">
            {ALL_LANGUAGES.map((item, index) => {
              const isSelected = item.label === currentLabel;
              return (
                <li
                  key={`${item.label}-${index}`}
                  onClick={() => handleSelect(item.key)}
                  className={`relative flex items-center h-[32.5px] pl-[8px] pr-[28px] mx-[4px] my-0 rounded-[4px] cursor-pointer transition-colors duration-150 ${
                    isSelected
                      ? "text-[#1f2329] font-normal"
                      : "text-[#1f2329] hover:bg-[rgba(31,35,41,0.08)]"
                  }`}
                >
                  <span className="text-[14px] truncate">{item.label}</span>

                  {/* 选中项的蓝色对勾 (原版 SVG) */}
                  {isSelected && (
                    <i className="language-check-icon absolute top-1/2 right-[5px] -translate-y-1/2 w-[20px] h-[20px] flex items-center justify-center">
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path
                          d="M9.718 15.41l9.192-9.192a.5.5 0 01.707 0l.707.707a.5.5 0 010 .707l-9.9 9.9a1 1 0 01-1.414 0l-5.303-5.304a.5.5 0 010-.707l.707-.707a.5.5 0 01.707 0l4.597 4.596z"
                          fill="#3370FF"
                        />
                      </svg>
                    </i>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </div>
  );
};

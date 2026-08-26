import React from "react";
import { FeishuAuthButton } from "./FeishuAuthButton";
import { OAuthProvider, BrandVariant } from "../../types";

interface FeishuThirdPartyLoginProps {
  variant?: BrandVariant;
  onSelectProvider?: (provider: OAuthProvider) => void;
  className?: string;
}

/**
 * 飞书 1:1 第三方 / 快捷登录入口组件 (.enter-credential__doubao_more_entry)
 *
 * 原版 CSS 规格：
 * - 按钮高度：40px
 * - 按钮字号：16px
 * - 按钮边框：1px solid #dee0e3
 * - 按钮圆角：6px
 * - 按钮间距：gap: 12px
 */
export const FeishuThirdPartyLogin: React.FC<FeishuThirdPartyLoginProps> = ({
  onSelectProvider,
  className = "",
}) => {
  const handleClick = (provider: OAuthProvider) => {
    if (onSelectProvider) {
      onSelectProvider(provider);
    }
  };

  return (
    <div className={`enter-credential__doubao_more_entry flex flex-col gap-[12px] w-full ${className}`}>
      {/* 1. 使用谷歌登录 (图标直接取自 D:/下载/飞书Lark登录/assets/asset-008.svg) */}
      <FeishuAuthButton
        variant="outlined"
        size="lg"
        onClick={() => handleClick("google")}
        icon={
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 32 32"
            className="mr-[4px] shrink-0"
          >
            <g fillRule="nonzero" fill="none">
              <path
                fill="#FBBC05"
                d="M8.43 15.813c0-.825.14-1.617.39-2.358l-4.373-3.27a12.45 12.45 0 00-1.332 5.627c0 2.023.478 3.932 1.33 5.625l4.37-3.276a7.357 7.357 0 01-.385-2.349"
              />
              <path
                fill="#EA4335"
                d="M16.11 8.305c1.83 0 3.484.635 4.784 1.674l3.78-3.696c-2.303-1.963-5.256-3.176-8.564-3.176-5.135 0-9.55 2.875-11.663 7.077l4.375 3.27c1.007-2.995 3.879-5.149 7.288-5.149"
              />
              <path
                fill="#34A853"
                d="M16.11 23.322c-3.41 0-6.282-2.154-7.29-5.15l-4.373 3.27c2.113 4.203 6.527 7.078 11.663 7.078 3.169 0 6.195-1.102 8.467-3.167l-4.152-3.143c-1.171.723-2.646 1.112-4.316 1.112"
              />
              <path
                fill="#4285F4"
                d="M28.514 15.813c0-.75-.119-1.56-.295-2.31h-12.11v4.91h6.97c-.348 1.674-1.296 2.96-2.653 3.797l4.15 3.143c2.387-2.168 3.938-5.397 3.938-9.54"
              />
            </g>
          </svg>
        }
      >
        使用 Google 登录
      </FeishuAuthButton>

      {/* 2. 使用Github登录 */}
      <FeishuAuthButton
        variant="outlined"
        size="lg"
        onClick={() => handleClick("github")}
        icon={
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="#1f2329"
            className="mr-[4px] shrink-0"
          >
            <path
              fillRule="evenodd"
              clipRule="evenodd"
              d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"
            />
          </svg>
        }
      >
        使用 Github 登录
      </FeishuAuthButton>

      {/* 3. 使用QQ登录 */}
      <FeishuAuthButton
        variant="outlined"
        size="lg"
        onClick={() => handleClick("qq")}
        icon={
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="#1EBAFC"
            className="mr-[4px] shrink-0"
          >
            <path d="M21.395 15.035a40 40 0 0 0-.803-2.264l-1.079-2.695c.001-.032.014-.562.014-.836C19.526 4.632 17.351 0 12 0S4.474 4.632 4.474 9.241c0 .274.013.804.014.836l-1.08 2.695a39 39 0 0 0-.802 2.264c-1.021 3.283-.69 4.643-.438 4.673.54.065 2.103-2.472 2.103-2.472 0 1.469.756 3.387 2.394 4.771-.612.188-1.363.479-1.845.835-.434.32-.379.646-.301.778.343.578 5.883.369 7.482.189 1.6.18 7.14.389 7.483-.189.078-.132.132-.458-.301-.778-.483-.356-1.233-.646-1.846-.836 1.637-1.384 2.393-3.302 2.393-4.771 0 0 1.563 2.537 2.103 2.472.251-.03.581-1.39-.438-4.673" />
          </svg>
        }
      >
        使用 QQ 登录
      </FeishuAuthButton>
    </div>
  );
};

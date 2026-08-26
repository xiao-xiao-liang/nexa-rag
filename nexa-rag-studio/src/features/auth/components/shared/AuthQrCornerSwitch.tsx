import React from "react";

interface AuthQrCornerSwitchProps {
  mode: "form" | "qr";
  onToggle: () => void;
}

/**
 * 飞书卡片右上角折角切换角标与气泡 Tooltip
 *
 * 1:1 完整复刻原版 DOM 与 CSS 规则
 */
export const AuthQrCornerSwitch: React.FC<AuthQrCornerSwitchProps> = ({ mode, onToggle }) => {
  const isForm = mode === "form";
  const tooltipText = isForm ? "扫码登录" : "密码登录";

  return (
    <>
      <style>{`
        .login-qr-switch-box {
          position: absolute;
          top: 4px;
          right: 4px;
          border-radius: 8px;
          width: 400px;
          height: 80px;
          overflow: hidden;
          pointer-events: none;
          z-index: 20;
        }
        .switch-login-mode-wrapper {
          position: absolute;
          top: 0;
          right: 0;
          width: 0;
          height: 0;
          z-index: 2;
          font-size: 14px;
          pointer-events: auto;
        }
        .switch-login-mode-wrapper .web-v3-custom-tooltip {
          height: 100%;
          position: relative;
        }
        .switch-login-mode-wrapper .switch-login-mode-container .switch-login-mode-box {
          width: 100px;
          height: 100px;
          transform: translate(-50px, -50px) rotate(45deg);
          cursor: pointer;
          background-color: #82a7fc;
          transition: background-color .3s;
          overflow: hidden;
        }
        .switch-login-mode-wrapper .switch-login-mode-container .switch-login-mode-box:hover {
          background-color: #4e83fd;
        }
        .switch-login-mode-wrapper .switch-login-mode-container .switch-login-mode-box .switch-icon {
          position: absolute;
          font-size: 40px;
          color: #fff;
          bottom: -8px;
          left: 30px;
          transform: rotate(-45deg);
          display: flex;
          align-items: center;
          justify-content: center;
        }
        .switch-login-mode-wrapper .web-v3-custom-tooltip .tooltip-content {
          display: none;
          position: absolute;
          padding: 0 12px;
          top: 30px;
          right: 80px;
          height: 44px;
          line-height: 44px;
          white-space: nowrap;
          border-radius: 8px;
          background: #3370ff;
          color: #fff;
          opacity: 0;
          font-size: 14px;
          transform: translateY(-60%);
          box-shadow: 0 4px 10px 0 rgba(36,91,219,.24);
          pointer-events: none;
          user-select: none;
        }
        .switch-login-mode-wrapper .web-v3-custom-tooltip .tooltip-content:after {
          content: "";
          display: block;
          width: 0;
          height: 0;
          right: -6px;
          top: 16px;
          background: #3370ff;
          position: absolute;
          border-color: #3370ff #3370ff transparent transparent;
          border-style: solid;
          border-width: 6px;
          transform: rotate(45deg) translate(0);
        }
        .switch-login-mode-wrapper .switch-login-mode-container:hover + .tooltip-content {
          display: block;
          animation: delay .5s .3s forwards;
          -webkit-animation: delay .5s .3s forwards;
        }
        @keyframes delay {
          0% { opacity: .2; }
          to { opacity: 1; }
        }
        @-webkit-keyframes delay {
          0% { opacity: .2; }
          to { opacity: 1; }
        }
      `}</style>
      <div className="login-qr-switch-box">
        <div className="switch-login-mode-wrapper">
          <div className="web-v3-custom-tooltip">
            {/* 折角触发区域 */}
            <div
              className="switch-login-mode-container"
              onClick={onToggle}
            >
              <div className="switch-login-mode-box">
                <span className="switch-icon">
                  {isForm ? (
                    // QrOutlined SVG
                    <svg width="40" height="40" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <path d="M6.5 7.5a1 1 0 0 1 1-1h1a1 1 0 0 1 1 1v1a1 1 0 0 1-1 1h-1a1 1 0 0 1-1-1v-1Z" fill="currentColor" />
                      <path d="M4.5 2.5c-1.1 0-2 .9-2 2v7c0 1.1.9 2 2 2h7c1.1 0 2-.9 2-2v-7c0-1.1-.9-2-2-2h-7Zm0 2h7v7h-7v-7ZM11 16a1 1 0 1 1 2 0 1 1 0 0 1-2 0Zm0 3.5a1 1 0 1 1 2 0v1a1 1 0 1 1-2 0v-1Zm4-7.5a1 1 0 1 1 2 0 1 1 0 0 1-2 0Zm3.5 0a1 1 0 0 1 1-1h1a1 1 0 1 1 0 2h-1a1 1 0 0 1-1-1ZM15 17c0-1.1.9-2 2-2h2.5c1.1 0 2 .9 2 2v2.5c0 1.1-.9 2-2 2H17c-1.1 0-2-.9-2-2V17Zm4.5 0H17v2.5h2.5V17Zm-15-2c-1.1 0-2 .9-2 2v2.5c0 1.1.9 2 2 2H7c1.1 0 2-.9 2-2V17c0-1.1-.9-2-2-2H4.5Zm0 2H7v2.5H4.5V17ZM15 4.5c0-1.1.9-2 2-2h2.5c1.1 0 2 .9 2 2V7c0 1.1-.9 2-2 2H17c-1.1 0-2-.9-2-2V4.5Zm4.5 0H17V7h2.5V4.5Z" fill="currentColor" />
                    </svg>
                  ) : (
                    // PC / Monitor SVG
                    <svg width="40" height="40" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <path d="M4 4h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm0 2v10h16V6H4Zm4 13h8v2H8v-2Z" fill="currentColor" />
                    </svg>
                  )}
                </span>
              </div>
            </div>

            {/* 悬浮蓝色气泡 Tooltip */}
            <div className="tooltip-content">
              {tooltipText}
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

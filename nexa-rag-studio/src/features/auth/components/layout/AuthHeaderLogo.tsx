import React from "react";
import { useNavigate } from "react-router-dom";
import { BrandVariant } from "../../types";
import feishuLogo from "../../../../assets/auth/image-004.png";
import larkLogo from "../../../../assets/auth/lark-logo.svg";

interface AuthHeaderLogoProps {
  variant?: BrandVariant;
  className?: string;
  onClick?: () => void;
}

/**
 * 飞书/Lark 登录页左上角品牌 Logo 区域
 *
 * 1:1 还原自原版 CSS：
 * - Lark: .web-v3-common-logo { position: absolute; top: 0; left: 0; height: 73px; padding-left: 35px; }
 *         .web-v3-common-logo > .logo-image { width: auto; height: 38px; cursor: pointer; }
 * - 飞书: .with-doubao-logo { height: 48px; margin-left: 29px; margin-top: 32px; padding-left: 0; }
 *         .with-doubao-logo > .logo-image { width: 89px; height: 56px; cursor: pointer; }
 */
export const AuthHeaderLogo: React.FC<AuthHeaderLogoProps> = ({
  variant = "feishu",
  className = "",
  onClick,
}) => {
  const navigate = useNavigate();

  const handleClick = () => {
    if (onClick) {
      onClick();
    } else {
      navigate("/");
    }
  };

  if (variant === "lark") {
    return (
      <div
        className={`absolute top-0 left-0 z-10 box-border flex items-center w-full h-[73px] pl-[35px] select-none ${className}`}
      >
        <img
          src={larkLogo}
          alt="Lark Logo"
          onClick={handleClick}
          className="w-auto h-[38px] cursor-pointer transition-opacity duration-200 hover:opacity-80 active:opacity-70 object-contain"
        />
      </div>
    );
  }

  // 飞书 + 豆包 Logo
  return (
    <div
      className={`absolute top-0 left-0 z-10 box-border flex items-center h-[48px] mt-[32px] ml-[29px] select-none ${className}`}
    >
      <img
        src={feishuLogo}
        alt="飞书 / 豆包 Logo"
        onClick={handleClick}
        className="w-[89px] h-[56px] cursor-pointer transition-opacity duration-200 hover:opacity-80 active:opacity-70 object-contain"
      />
    </div>
  );
};

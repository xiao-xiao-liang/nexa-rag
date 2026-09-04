import React from "react";
import { useNavigate } from "react-router-dom";
import { BrandVariant } from "../../types";
import feishuLogo from "../../../../assets/auth/image-004.png";
import larkLogo from "../../../../assets/auth/lark-logo.svg";
import logoImg from "../../../../assets/logo.png";

interface AuthHeaderLogoProps {
  variant?: BrandVariant;
  className?: string;
  onClick?: () => void;
}

/**
 * 登录页左上角品牌 Logo 区域
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

  // 网站 Logo
  return (
    <div
      className={`absolute top-0 left-0 z-10 box-border flex items-center h-[48px] mt-[32px] ml-[29px] select-none ${className}`}
    >
      <img
        src={logoImg}
        alt="Logo"
        onClick={handleClick}
        className="w-[48px] h-[48px] cursor-pointer transition-opacity duration-200 hover:opacity-80 active:opacity-70 object-contain"
      />
    </div>
  );
};

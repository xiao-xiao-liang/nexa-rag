import React, { useState } from "react";
import { RefreshCw, CheckCircle2 } from "lucide-react";
import { BrandVariant } from "../../types";
import qrSampleImage from "../../../../assets/auth/image-005.webp";

interface QrScanLoginBoxProps {
  variant?: BrandVariant;
  onRefreshQr?: () => void;
  onSwitchBrand?: () => void;
}

/**
 * 飞书 / Lark 1:1 移动端扫码登录面板
 */
export const QrScanLoginBox: React.FC<QrScanLoginBoxProps> = ({
  variant = "feishu",
  onRefreshQr,
  onSwitchBrand,
}) => {
  const [isExpired, setIsExpired] = useState(false);
  const [isSuccess] = useState(false);

  const isLark = variant === "lark";
  const appTarget = isLark ? "Lark 移动端" : "飞书或豆包移动端";
  const switchBrandText = isLark ? "切换至飞书登录" : "切换至Lark登录";

  const handleRefresh = () => {
    setIsExpired(false);
    onRefreshQr?.();
  };

  return (
    <div className="flex flex-col items-center text-center w-full py-1">
      {/* 扫码标题 (.step-title: 22px 600字重 30px行高) */}
      <h3 className="text-[22px] font-semibold text-[#1f2329] leading-[30px]">扫码登录</h3>
      <p className="mt-1.5 text-[14px] text-[#646a73] leading-[22px]">
        请使用 <span className="text-[#1f2329] font-medium">{appTarget}</span> 扫码
      </p>

      {/* 二维码容器 (200x200 像素) */}
      <div className="relative my-6 p-2 bg-white rounded-[8px] border border-[#dee0e3] shadow-sm flex items-center justify-center">
        {/* 二维码图片 */}
        <div className="w-[180px] h-[180px] flex items-center justify-center overflow-hidden rounded-[4px] bg-[#f8f9fa]">
          <img
            src={qrSampleImage}
            alt="扫码登录二维码"
            className="w-full h-full object-cover"
          />
        </div>

        {/* 扫码成功状态覆盖层 */}
        {isSuccess && (
          <div className="absolute inset-0 bg-white/95 rounded-[8px] flex flex-col items-center justify-center p-4">
            <CheckCircle2 className="w-12 h-12 text-[#34a853] mb-2 animate-in zoom-in-75 duration-200" />
            <span className="text-[14px] font-medium text-[#1f2329]">扫码成功</span>
            <span className="text-[12px] text-[#8f959e] mt-1">请在手机端确认登录</span>
          </div>
        )}

        {/* 二维码过期遮罩与刷新按钮 */}
        {isExpired && !isSuccess && (
          <div className="absolute inset-0 bg-black/60 rounded-[8px] flex flex-col items-center justify-center p-4 text-white backdrop-blur-[1px] animate-in fade-in duration-200">
            <button
              type="button"
              onClick={handleRefresh}
              className="flex flex-col items-center gap-2 group cursor-pointer"
            >
              <div className="w-10 h-10 rounded-full bg-[#3370ff] flex items-center justify-center group-hover:scale-110 transition-transform">
                <RefreshCw className="w-5 h-5" />
              </div>
              <span className="text-[13px] font-medium">二维码已失效，点击刷新</span>
            </button>
          </div>
        )}
      </div>

      {/* 底部切换品牌选项 (飞书 <-> Lark) */}
      {onSwitchBrand && (
        <button
          type="button"
          onClick={onSwitchBrand}
          className="text-[14px] text-[#3370ff] hover:text-[#245bdb] hover:underline cursor-pointer select-none transition-colors"
        >
          {switchBrandText}
        </button>
      )}
    </div>
  );
};

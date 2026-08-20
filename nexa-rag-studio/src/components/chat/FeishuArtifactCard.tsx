import React from "react";
import { FileCode2 } from "lucide-react";
import { FeishuTooltip } from "../ui/tooltip";
import { BaseAgentToolDownloadOutlinedIcon } from "./FeishuChatIcons";

export interface FeishuArtifactCardProps {
  filename: string;
  size: string;
  onDownload?: (filename: string) => void;
}

/** 1:1 飞书附件/产物生成物卡片 */
export const FeishuArtifactCard: React.FC<FeishuArtifactCardProps> = ({
  filename,
  size,
  onDownload,
}) => {
  const handleDownload = () => {
    if (onDownload) {
      onDownload(filename);
    } else {
      alert(`下载 ${filename}`);
    }
  };

  return (
    <div className="flex items-center justify-between border border-[#DEE0E3] rounded-[8px] bg-white hover:bg-[#F8F9FA] transition-colors p-2.5 max-w-[320px] my-2 select-none">
      <div className="flex items-center gap-2.5 min-w-0">
        <div className="w-8 h-8 rounded-[6px] bg-[#F0F4FF] border border-[#DEE0E3] flex items-center justify-center shrink-0 text-[#3370FF]">
          <FileCode2 className="w-4 h-4" />
        </div>
        <div className="flex flex-col min-w-0">
          <span className="text-[13px] font-medium text-[#1F2329] truncate leading-tight">
            {filename}
          </span>
          <span className="text-[12px] text-[#8F959E] leading-tight mt-0.5">{size}</span>
        </div>
      </div>
      <FeishuTooltip title="下载附件" side="top" sideOffset={6}>
        <button
          type="button"
          className="w-7 h-7 rounded-[6px] hover:bg-[#E8ECEF] flex items-center justify-center text-[#646A73] hover:text-[#1F2329] transition-colors cursor-pointer shrink-0"
          aria-label="下载附件"
          onClick={handleDownload}
        >
          <BaseAgentToolDownloadOutlinedIcon className="w-4 h-4" />
        </button>
      </FeishuTooltip>
    </div>
  );
};

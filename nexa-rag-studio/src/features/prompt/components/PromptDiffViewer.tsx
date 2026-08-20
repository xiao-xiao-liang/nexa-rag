import React, { useMemo } from "react";
import { Plus, Minus, Check, Copy } from "lucide-react";
import { computeLineDiff } from "../utils/diffUtils";

interface PromptDiffViewerProps {
  oldContent: string;
  newContent: string;
  oldTitle?: string;
  newTitle?: string;
  className?: string;
}

export const PromptDiffViewer: React.FC<PromptDiffViewerProps> = ({
  oldContent,
  newContent,
  oldTitle = "线上稳定版本",
  newTitle = "当前编辑草稿",
  className = "",
}) => {
  const [copied, setCopied] = React.useState(false);

  const diffResult = useMemo(() => {
    return computeLineDiff(oldContent, newContent);
  }, [oldContent, newContent]);

  const handleCopyNew = () => {
    navigator.clipboard.writeText(newContent);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className={`flex flex-col h-full bg-white rounded-[8px] border border-[#DEE0E3] overflow-hidden ${className}`}>
      {/* Diff Header (纯白背景 + 细分割线) */}
      <div className="flex items-center justify-between px-4 py-2.5 bg-white border-b border-[#EFF0F1] shrink-0">
        <div className="flex items-center gap-3 text-[13px]">
          <span className="font-semibold text-[#1F2329]">变更差异比对 (Diff)</span>
          <div className="flex items-center gap-2 text-[12px]">
            <span className="inline-flex items-center gap-1 text-[#00B42A] bg-[#E6F8F5] px-2 py-0.5 rounded font-medium">
              <Plus className="size-3" /> {diffResult.additions} 新增
            </span>
            <span className="inline-flex items-center gap-1 text-[#F53F3F] bg-[#FFF2F0] px-2 py-0.5 rounded font-medium">
              <Minus className="size-3" /> {diffResult.deletions} 删除
            </span>
          </div>
        </div>

        <div className="flex items-center gap-3 text-[12px] text-[#646A73]">
          <span className="hidden sm:inline">
            比较: <strong className="text-[#1F2329] font-medium">{oldTitle}</strong> →{" "}
            <strong className="text-[#3370FF] font-medium">{newTitle}</strong>
          </span>
          <button
            type="button"
            onClick={handleCopyNew}
            className="inline-flex items-center gap-1 px-2.5 py-1 rounded-[6px] border border-[#DEE0E3] bg-white text-[#1F2329] hover:bg-[#F2F3F5] transition-colors cursor-pointer text-[12px] shadow-none"
          >
            {copied ? (
              <>
                <Check className="size-3 text-[#00B42A]" /> 已复制草稿
              </>
            ) : (
              <>
                <Copy className="size-3" /> 复制草稿
              </>
            )}
          </button>
        </div>
      </div>

      {/* Diff Content Body (纯白底色) */}
      <div className="flex-1 overflow-auto font-mono text-[12.5px] leading-relaxed p-0 bg-white">
        {!diffResult.hasChanges ? (
          <div className="h-full flex flex-col items-center justify-center py-16 text-[#8F959E] text-[13px] bg-white">
            <div className="size-10 rounded-full bg-[#E6F8F5] flex items-center justify-center text-[#00B42A] mb-3">
              <Check className="size-5" />
            </div>
            <p className="font-sans font-medium text-[#1F2329]">正文与对比目标完全一致</p>
            <p className="font-sans text-[12px] text-[#8F959E] mt-1">当前编辑内容没有发生任何变更</p>
          </div>
        ) : (
          <table className="w-full border-collapse bg-white">
            <tbody>
              {diffResult.lines.map((line, idx) => {
                const isAdded = line.type === "added";
                const isRemoved = line.type === "removed";

                return (
                  <tr
                    key={idx}
                    className={`transition-colors ${
                      isAdded
                        ? "bg-[#E6F8F5]/70 hover:bg-[#D3F5EC]"
                        : isRemoved
                        ? "bg-[#FFF2F0]/80 hover:bg-[#FFE4E1]"
                        : "bg-white hover:bg-[#F9FAFB]"
                    }`}
                  >
                    {/* Old line number */}
                    <td className="w-12 py-0.5 px-2 text-right text-[11px] text-[#8F959E] select-none border-r border-[#EFF0F1] bg-white tabular-nums">
                      {line.oldLineNumber || ""}
                    </td>
                    {/* New line number */}
                    <td className="w-12 py-0.5 px-2 text-right text-[11px] text-[#8F959E] select-none border-r border-[#EFF0F1] bg-white tabular-nums">
                      {line.newLineNumber || ""}
                    </td>
                    {/* Diff marker */}
                    <td className="w-6 py-0.5 text-center text-[12px] select-none font-bold">
                      {isAdded && <span className="text-[#00B42A]">+</span>}
                      {isRemoved && <span className="text-[#F53F3F]">-</span>}
                    </td>
                    {/* Line content */}
                    <td
                      className={`py-0.5 px-2 whitespace-pre-wrap break-all ${
                        isAdded
                          ? "text-[#00871F] font-medium"
                          : isRemoved
                          ? "text-[#CB2626] line-through opacity-80"
                          : "text-[#1F2329]"
                      }`}
                    >
                      {line.content || " "}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

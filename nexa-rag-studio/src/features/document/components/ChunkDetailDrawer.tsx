import React, { useState } from "react";
import {
  X,
  ChevronLeft,
  ChevronRight,
  Copy,
  Check,
  FileText,
  Hash,
  Layers,
  Sparkles,
} from "lucide-react";
import { DocumentChunkVO, DocumentOverviewVO } from "../../../types";

interface ChunkDetailDrawerProps {
  chunk: DocumentChunkVO | null;
  overview: DocumentOverviewVO | null;
  allChunks: DocumentChunkVO[];
  onClose: () => void;
  onSelectChunk: (chunk: DocumentChunkVO) => void;
}

export const ChunkDetailDrawer: React.FC<ChunkDetailDrawerProps> = ({
  chunk,
  overview,
  allChunks,
  onClose,
  onSelectChunk,
}) => {
  const [copied, setCopied] = useState(false);

  if (!chunk) return null;

  const currentIndex = allChunks.findIndex((c) => c.chunkId === chunk.chunkId);
  const hasPrev = currentIndex > 0;
  const hasNext = currentIndex < allChunks.length - 1;

  const handlePrev = () => {
    if (hasPrev) {
      onSelectChunk(allChunks[currentIndex - 1]);
    }
  };

  const handleNext = () => {
    if (hasNext) {
      onSelectChunk(allChunks[currentIndex + 1]);
    }
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(chunk.text || chunk.content || "");
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const formattedIndex = String(chunk.chunkOrder ?? chunk.chunkIndex ?? 1).padStart(3, "0");
  const charCount = (chunk.text || chunk.content || "").length;
  const tokenCount = chunk.tokenCount ?? Math.round(charCount * 0.7);

  return (
    <div className="fixed inset-0 z-50 overflow-hidden flex justify-end">
      {/* 半透明遮罩 */}
      <div
        className="fixed inset-0 bg-[#1F2329]/30 backdrop-blur-[1px] transition-opacity animate-in fade-in"
        onClick={onClose}
      />

      {/* 飞书 1:1 右侧滑出抽屉 (560px 宽度) */}
      <div className="relative w-full max-w-[560px] bg-white h-full shadow-[-8px_0_32px_rgba(31,35,41,0.12)] flex flex-col z-10 animate-in slide-in-from-right duration-200">
        {/* 1. 抽屉头部 */}
        <div className="p-5 border-b border-[#DEE0E3] bg-white flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-[16px] font-bold font-mono text-[#1F2329]">
                Chunk #{formattedIndex}
              </span>
              <span className="text-[12px] font-mono text-[#646A75] bg-[#F2F3F5] px-2 py-0.5 rounded-[4px]">
                {tokenCount} Tokens · {charCount} 字符
              </span>
            </div>
            <p className="text-[12px] text-[#8F959E] mt-1">
              所属文档: {overview?.title || overview?.originalFileName || overview?.fileName || `文档 #${chunk.documentId}`}
            </p>
          </div>

          <div className="flex items-center gap-1.5">
            {/* 上一块 / 下一块 翻页器 */}
            <div className="flex items-center rounded-[6px] border border-[#D0D3D6] bg-white p-0.5">
              <button
                type="button"
                onClick={handlePrev}
                disabled={!hasPrev}
                className="p-1 rounded-[4px] hover:bg-[#EFF0F1] text-[#646A75] hover:text-[#1F2329] disabled:opacity-30 disabled:hover:bg-transparent cursor-pointer disabled:cursor-not-allowed transition-colors"
                title="上一块"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <div className="w-[1px] h-3.5 bg-[#DEE0E3]" />
              <button
                type="button"
                onClick={handleNext}
                disabled={!hasNext}
                className="p-1 rounded-[4px] hover:bg-[#EFF0F1] text-[#646A75] hover:text-[#1F2329] disabled:opacity-30 disabled:hover:bg-transparent cursor-pointer disabled:cursor-not-allowed transition-colors"
                title="下一块"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>

            <button
              onClick={onClose}
              className="w-7 h-7 rounded-[6px] hover:bg-[#EFF0F1] flex items-center justify-center text-[#646A75] hover:text-[#1F2329] transition-colors cursor-pointer ml-1"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* 2. 抽屉滚动内容区 */}
        <div className="flex-1 overflow-y-auto p-5 space-y-4 custom-scrollbar bg-[#F8F9FA]/40">
          {/* 切片完整正文视窗 */}
          <div className="bg-white rounded-[12px] border border-[#DEE0E3] shadow-2xs overflow-hidden">
            <div className="flex items-center justify-between px-4 py-2.5 bg-white border-b border-[#DEE0E3]">
              <div className="flex items-center gap-1.5 text-[12px] font-medium text-[#646A75]">
                <FileText className="w-3.5 h-3.5 text-[#3370FF]" />
                <span>切片正文完整内容</span>
              </div>

              <button
                onClick={handleCopy}
                className="h-6 px-2 rounded-[4px] border border-[#D0D3D6] bg-white hover:bg-[#EFF0F1] text-[11px] font-medium text-[#646A75] hover:text-[#1F2329] inline-flex items-center gap-1 transition-colors cursor-pointer"
              >
                {copied ? (
                  <>
                    <Check className="w-3 h-3 text-[#00B42A]" />
                    <span className="text-[#00B42A]">已复制</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3 h-3" />
                    <span>复制全文</span>
                  </>
                )}
              </button>
            </div>

            <div className="p-4 text-[14px] text-[#1F2329] leading-[24px] whitespace-pre-wrap break-words font-sans selection:bg-[#E8F3FF]">
              {chunk.text || chunk.content}
            </div>
          </div>

          {/* 分块元数据卡片 */}
          <div className="bg-white rounded-[12px] border border-[#DEE0E3] p-4 shadow-2xs space-y-2.5 text-[13px]">
            <h4 className="text-[13px] font-semibold text-[#1F2329] pb-2 border-b border-[#DEE0E3] flex items-center gap-1.5">
              <Layers className="w-3.5 h-3.5 text-[#8D55ED]" />
              <span>分块元数据属性</span>
            </h4>

            <div className="grid grid-cols-3 gap-2">
              <span className="text-[#646A75]">切片唯一 ID:</span>
              <span className="col-span-2 font-mono text-[#1F2329]">#{chunk.chunkId}</span>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <span className="text-[#646A75]">分块相对序号:</span>
              <span className="col-span-2 font-mono text-[#1F2329]">Chunk #{formattedIndex}</span>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <span className="text-[#646A75]">所属文档 ID:</span>
              <span className="col-span-2 font-mono text-[#1F2329]">#{chunk.documentId}</span>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <span className="text-[#646A75]">Token 评估值:</span>
              <span className="col-span-2 font-mono text-[#3370FF] font-medium">{tokenCount} Tokens</span>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <span className="text-[#646A75]">字符总长度:</span>
              <span className="col-span-2 font-mono text-[#1F2329]">{charCount} 字符</span>
            </div>
          </div>
        </div>

        {/* 3. 抽屉底部操作条 */}
        <div className="p-4 border-t border-[#DEE0E3] bg-white flex items-center justify-between">
          <span className="text-[12px] text-[#8F959E] font-mono">
            {currentIndex + 1} / {allChunks.length} 块
          </span>

          <button
            onClick={onClose}
            className="h-8 px-4 rounded-[6px] border border-[#D0D3D6] bg-white hover:bg-[#EFF0F1] text-[13px] font-medium text-[#1F2329] transition-colors cursor-pointer"
          >
            关闭
          </button>
        </div>
      </div>
    </div>
  );
};

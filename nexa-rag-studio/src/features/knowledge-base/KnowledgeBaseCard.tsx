import React from "react";
import {
  BookOpen,
  Folder,
  MoreVertical,
  Pencil,
  Trash2,
} from "lucide-react";
import { KnowledgeBaseSummaryVO } from "../../types";
import { FeishuPill, FEISHU_FONT_FAMILY } from "../../components/ui/feishu-table";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "../../components/ui/dropdown-menu";

interface KnowledgeBaseCardProps {
  knowledgeBase: KnowledgeBaseSummaryVO;
  onOpen: (id: number | string) => void;
  onEdit: (kb: KnowledgeBaseSummaryVO) => void;
  onDelete: (kb: KnowledgeBaseSummaryVO) => void;
}

export const KnowledgeBaseCard: React.FC<KnowledgeBaseCardProps> = ({
  knowledgeBase,
  onOpen,
  onEdit,
  onDelete,
}) => {
  const isDefault = knowledgeBase.isDefault === 1;

  const stats = knowledgeBase.statistics || {
    totalCount: 0,
    pendingCount: 0,
    processingCount: 0,
    indexedCount: 0,
    failedCount: 0,
  };

  const total = stats.totalCount || 0;
  const indexedPercent = total > 0 ? Math.round((stats.indexedCount / total) * 100) : 0;
  const processingPercent = total > 0 ? Math.round((stats.processingCount / total) * 100) : 0;
  const failedPercent = total > 0 ? Math.round((stats.failedCount / total) * 100) : 0;

  const formattedTime = knowledgeBase.updatedTime
    ? new Date(knowledgeBase.updatedTime).toLocaleDateString("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      })
    : "刚刚";

  return (
    <div
      onClick={() => onOpen(knowledgeBase.knowledgeBaseId)}
      style={{ fontFamily: FEISHU_FONT_FAMILY }}
      className="group relative flex flex-col justify-between rounded-[12px] border border-[#DEE0E3] bg-white p-5 shadow-2xs transition-all duration-200 hover:border-[#3370FF]/60 hover:shadow-[0_8px_24px_rgba(31,35,41,0.06)] hover:-translate-y-0.5 cursor-pointer"
    >
      {/* 顶部 Header */}
      <div>
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3.5 min-w-0">
            <div
              className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] transition-transform duration-200 group-hover:scale-105 ${
                isDefault
                  ? "bg-[#E8F3FF] text-[#3370FF] shadow-2xs"
                  : "bg-[#F0F4FF] text-[#3370FF]"
              }`}
            >
              {isDefault ? <BookOpen className="h-5 w-5" /> : <Folder className="h-5 w-5" />}
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h3 className="truncate text-[16px] font-semibold text-[#1F2329] group-hover:text-[#3370FF] transition-colors">
                  {knowledgeBase.name}
                </h3>
                {isDefault && (
                  <FeishuPill variant="blue" showDot={false} className="text-[11px] px-2 py-0">
                    默认
                  </FeishuPill>
                )}
              </div>
            </div>
          </div>

          {/* 更多操作下拉菜单 */}
          <div onClick={(e) => e.stopPropagation()} className="shrink-0">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button
                  type="button"
                  aria-label="更多操作"
                  className="flex h-7 w-7 items-center justify-center rounded-[6px] text-[#8F959E] hover:bg-[#F2F3F5] hover:text-[#1F2329] transition-colors"
                >
                  <MoreVertical className="h-4 w-4" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-32 bg-white shadow-lg border border-[#DEE0E3] rounded-[8px] p-1 animate-in fade-in duration-100">
                <DropdownMenuItem
                  onClick={() => onEdit(knowledgeBase)}
                  className="flex items-center gap-2 rounded-[6px] px-2.5 py-1.5 text-xs text-[#1F2329] hover:bg-[#F2F3F5] cursor-pointer transition-colors"
                >
                  <Pencil className="h-3.5 w-3.5 text-[#646A73]" />
                  {isDefault ? "查看属性" : "编辑知识库"}
                </DropdownMenuItem>
                {!isDefault && (
                  <>
                    <DropdownMenuSeparator className="my-1 border-t border-[#DEE0E3]" />
                    <DropdownMenuItem
                      onClick={() => onDelete(knowledgeBase)}
                      className="flex items-center gap-2 rounded-[6px] px-2.5 py-1.5 text-xs text-[#F53F3F] hover:bg-[#FFF2F0] cursor-pointer transition-colors"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                      删除知识库
                    </DropdownMenuItem>
                  </>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>

        {/* 描述信息 */}
        <p className="mt-3 line-clamp-2 min-h-[38px] text-[13px] leading-relaxed text-[#646A73]">
          {knowledgeBase.description || "暂无描述信息"}
        </p>
      </div>

      {/* 底部信息栏 (无分割线、左下角状态胶囊+青绿色文档数、右下角更新时间) */}
      <div className="mt-4 flex items-center justify-between text-[12px]">
        <div className="flex items-center gap-3">
          {/* 状态胶囊 (已就绪 / 处理中 / 异常，无圆点) */}
          <span className="inline-flex items-center justify-center h-[22px] px-2 rounded-full text-[11px] font-medium bg-[#E6F8F5] text-[#10A893] select-none">
            已就绪
          </span>

          {/* 文档数 (数字采用飞书 CRM 青绿色) */}
          <span className="text-[#646A73]">
            <span className="font-bold text-[#10A893] text-[14px] tabular-nums mr-0.5">
              {stats.totalCount}
            </span>
            篇文档
          </span>
        </div>

        {/* 更新时间 */}
        <span className="text-[#8F959E]">
          更新于 {formattedTime}
        </span>
      </div>
    </div>
  );
};

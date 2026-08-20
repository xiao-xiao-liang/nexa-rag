import React, { useState } from "react";
import { BaseAgentChatbotOutlinedIcon, DownBoldOutlinedIcon } from "./FeishuChatIcons";
import { FeishuShinyText } from "./FeishuShinyText";
import { ChatToolOperation } from "../../types";
import { getToolExecutionStatus } from "./tool-execution-state";

export interface AgentToolExecutionBoxProps {
  operations: ChatToolOperation[];
  defaultOpen?: boolean;
  messageStatus?: string;
}

/** 1:1 飞书原版 Agent Tool Execution Box (折叠微交互与工具调用列表) */
export const AgentToolExecutionBox: React.FC<AgentToolExecutionBoxProps> = ({
  operations,
  defaultOpen = false,
  messageStatus,
}) => {
  const [isExpanded, setIsExpanded] = useState(defaultOpen);
  const statusText = getToolExecutionStatus(operations, messageStatus);
  const isRunning = messageStatus === "GENERATING" || operations.some((operation) => operation.status === "RUNNING");

  return (
    <div
      className={`group transition-all select-none mb-1.5 ${
        isExpanded
          ? "w-full rounded-[8px] border border-[#DEE0E3] bg-white p-3"
          : "inline-flex items-center w-full rounded-[6px] hover:bg-[#F8F9FA] p-0.5"
      }`}
    >
      {isExpanded ? (
        <div className="w-full space-y-2">
          <div
            onClick={() => setIsExpanded(false)}
            className="flex items-center justify-between text-[12px] text-[#646A73] cursor-pointer"
          >
            <div className="flex items-center gap-1.5">
              <BaseAgentChatbotOutlinedIcon className="w-3.5 h-3.5 text-[#646A73] shrink-0" />
              <FeishuShinyText
                disabled={!isRunning}
                secondaryColor="#646A73"
                contrastColor="#1F2329"
                className="font-normal text-[#646A73]"
              >
                {statusText}
              </FeishuShinyText>
            </div>

            <div className="flex items-center text-[#8F959E]">
              <DownBoldOutlinedIcon className="w-3 h-3 rotate-180 transition-transform duration-200" />
            </div>
          </div>

          <div className="space-y-1.5 pt-0.5">
            {operations.map((operation) => (
              <div
                key={operation.opId}
                className="text-[12px] leading-[20px] text-[#646A73] font-sans break-all"
              >
                工具调用：{operation.name}
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div
          onClick={() => setIsExpanded(true)}
          className="flex items-center justify-between w-full text-[12px] text-[#646A73] cursor-pointer"
        >
          <div className="flex items-center gap-2">
            <div className="w-6 h-6 rounded-[6px] border border-[#DEE0E3] bg-white flex items-center justify-center shrink-0 shadow-2xs">
              <BaseAgentChatbotOutlinedIcon className="w-3.5 h-3.5 text-[#646A73]" />
            </div>
            <FeishuShinyText
              disabled={!isRunning}
              secondaryColor="#646A73"
              contrastColor="#1F2329"
              className="font-normal text-[#646A73]"
            >
              {statusText}
            </FeishuShinyText>
          </div>


          <div className="flex items-center text-[#8F959E] opacity-0 group-hover:opacity-100 transition-opacity pr-1.5">
            <DownBoldOutlinedIcon className="w-3 h-3 rotate-0" />
          </div>
        </div>
      )}
    </div>
  );
};

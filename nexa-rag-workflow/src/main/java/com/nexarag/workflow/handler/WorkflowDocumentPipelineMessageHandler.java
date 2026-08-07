package com.nexarag.workflow.handler;

import com.nexarag.document.model.entity.Document;
import com.nexarag.document.service.DocumentService;
import com.nexarag.infra.messaging.document.DocumentPipelineMessageHandler;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import com.nexarag.workflow.service.WorkflowGraphRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.DOCUMENT_ID;

/**
 * 工作流文档消息处理器，校验处理轮次后启动文档入库Graph。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowDocumentPipelineMessageHandler implements DocumentPipelineMessageHandler {

    private final DocumentService documentService;
    private final WorkflowGraphRunner workflowGraphRunner;

    @Override
    public void handle(DocumentPipelineMessage message) {
        // 1. 再次校验数据库当前处理轮次，避免消费状态更新后发生人工重试
        Document document = documentService.getRequiredDocument(message.documentId());
        if (!message.processId().equals(document.getProcessId())) {
            log.info("忽略已过期的工作流消息，documentId={}，messageProcessId={}，currentProcessId={}",
                    message.documentId(), message.processId(), document.getProcessId());
            return;
        }

        // 2. 启动Graph，由状态路由节点从数据库真实阶段继续执行
        workflowGraphRunner.run(Map.of(DOCUMENT_ID, message.documentId()));
    }
}

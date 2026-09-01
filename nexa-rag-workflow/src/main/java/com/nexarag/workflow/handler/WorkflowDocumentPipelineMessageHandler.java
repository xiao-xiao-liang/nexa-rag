package com.nexarag.workflow.handler;

import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.infra.messaging.document.DocumentPipelineMessageHandler;
import com.nexarag.infra.messaging.document.model.DocumentPipelineMessage;
import com.nexarag.workflow.service.WorkflowGraphRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.nexarag.workflow.constants.DocumentIngestionStateKeys.*;

/**
 * 工作流文档消息处理器，校验处理轮次后启动文档入库Graph。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowDocumentPipelineMessageHandler implements DocumentPipelineMessageHandler {

    private final DocumentVersionService documentVersionService;
    private final WorkflowGraphRunner workflowGraphRunner;

    @Override
    public void handle(DocumentPipelineMessage message) {
        // 1. 拒绝缺少版本边界的旧消息，禁止回退到 document 级处理链路。
        if (message == null || message.documentId() == null || message.documentVersionId() == null
                || message.processId() == null || message.processId().isBlank()) {
            log.warn("拒绝缺少文档版本边界的工作流消息，documentId={}，documentVersionId={}，processId={}",
                    message == null ? null : message.documentId(),
                    message == null ? null : message.documentVersionId(),
                    message == null ? null : message.processId());
            return;
        }

        // 2. 再次校验数据库当前处理轮次，避免消费状态更新后发生人工重试。
        DocumentVersionDO documentVersion = documentVersionService.getRequiredVersion(message.documentId(),
                message.documentVersionId());
        if (!message.processId().equals(documentVersion.getProcessId())) {
            log.info("忽略已过期的工作流消息，documentId={}，documentVersionId={}，messageProcessId={}，currentProcessId={}",
                    message.documentId(), message.documentVersionId(), message.processId(), documentVersion.getProcessId());
            return;
        }

        // 3. 启动Graph，由状态路由节点从数据库真实阶段继续执行。
        workflowGraphRunner.run(Map.of(DOCUMENT_ID, message.documentId(),
                DOCUMENT_VERSION_ID, message.documentVersionId(), PROCESS_ID, message.processId()));
    }
}

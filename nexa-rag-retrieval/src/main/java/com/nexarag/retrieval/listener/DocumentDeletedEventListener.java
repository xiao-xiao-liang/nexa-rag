package com.nexarag.retrieval.listener;

import com.nexarag.document.event.DocumentDeletedEvent;
import com.nexarag.retrieval.dto.res.DocumentIndexCleanupResult;
import com.nexarag.retrieval.service.DocumentIndexCleaner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文档删除后清理检索派生索引，确保文档模块不依赖检索模块实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentDeletedEventListener {

    private final DocumentIndexCleaner documentIndexCleaner;

    /**
     * 在删除事务提交后清理正文、向量和章节导航索引；清理失败不得影响已提交的文档删除。
     *
     * @param event 文档删除事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentDeleted(DocumentDeletedEvent event) {
        if (event == null || event.documentId() == null) {
            log.warn("忽略无效的文档删除事件，event={}", event);
            return;
        }

        // 1. 由清理器统一清理正文、向量和章节导航索引
        try {
            DocumentIndexCleanupResult result = documentIndexCleaner.cleanup(event.documentId());
            if (!result.success()) {
                log.error("文档删除后索引清理未完成，documentId={}，failureReason={}；需根据日志人工重试",
                        event.documentId(), result.failureReason());
                return;
            }
            log.info("文档删除后索引清理完成，documentId={}，vectorDeletedCount={}，keywordDeletedCount={}",
                    event.documentId(), result.vectorDeletedCount(), result.keywordDeletedCount());
        } catch (RuntimeException exception) {
            log.error("文档删除后索引清理异常，documentId={}；需根据日志人工重试", event.documentId(), exception);
        }
    }
}

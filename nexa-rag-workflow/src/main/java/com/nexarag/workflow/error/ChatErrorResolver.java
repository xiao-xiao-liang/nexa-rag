package com.nexarag.workflow.error;

import com.nexarag.common.exception.AbstractException;
import lombok.NoArgsConstructor;

import java.util.concurrent.TimeoutException;

/**
 * 针对具体中间件与异常根因的精准解析器。
 */
@NoArgsConstructor
public final class ChatErrorResolver {

    public static ChatErrorDetail resolve(Throwable throwable) {
        if (throwable == null) {
            return new ChatErrorDetail("CHAT_WORKFLOW_ERROR", "对话工作流执行失败，请稍后重试");
        }

        // 1. 保留项目内既有结构化业务异常
        if (throwable instanceof AbstractException abstractException) {
            return new ChatErrorDetail(abstractException.getErrorCode(), abstractException.getErrorMessage());
        }

        // 2. 自顶向下递归查找根因
        Throwable current = throwable;
        boolean hasEsClue = false;
        boolean hasMilvusClue = false;
        boolean hasTimeoutClue = false;
        boolean hasModelClue = false;

        while (current != null) {
            String className = current.getClass().getName();
            String msg = current.getMessage() == null ? "" : current.getMessage().toLowerCase();

            if (className.contains("Elasticsearch") || className.contains("co.elastic") || msg.contains("elasticsearch")
                    || className.contains("ConnectionClosedException") || msg.contains("connection is closed")) {
                hasEsClue = true;
            }
            if (className.contains("Milvus") || msg.contains("milvus")) {
                hasMilvusClue = true;
            }
            if (current instanceof TimeoutException || className.contains("Timeout") || msg.contains("timed out") || msg.contains("timeout")) {
                hasTimeoutClue = true;
            }
            if (className.contains("ModelException") || msg.contains("model gateway")) {
                hasModelClue = true;
            }
            current = current.getCause();
        }

        if (hasMilvusClue) {
            return new ChatErrorDetail("MILVUS_SERVICE_ERROR", "向量检索服务（Milvus）连接异常，请稍后重试");
        }
        if (hasEsClue) {
            return new ChatErrorDetail("ELASTICSEARCH_SERVICE_ERROR", "关键词检索服务（Elasticsearch）连接异常，请稍后重试");
        }
        if (hasModelClue) {
            return new ChatErrorDetail("MODEL_SERVICE_ERROR", "大模型服务调用异常，请稍后重试");
        }
        if (hasTimeoutClue) {
            return new ChatErrorDetail("TIMEOUT_ERROR", "系统服务响应超时，请稍后重试");
        }

        return new ChatErrorDetail("CHAT_WORKFLOW_ERROR", "对话工作流执行失败，请稍后重试");
    }
}

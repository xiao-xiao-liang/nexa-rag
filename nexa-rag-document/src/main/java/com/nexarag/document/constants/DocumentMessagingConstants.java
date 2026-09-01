package com.nexarag.document.constants;

/**
 * 文档领域 RocketMQ 配置与消息处理常量。
 */
public final class DocumentMessagingConstants {

    public static final String PIPELINE_TOPIC = "${nexa.document.pipeline.messaging.topic:nexa-document-pipeline}";
    public static final String PIPELINE_CONSUMER_GROUP = "${nexa.document.pipeline.messaging.consumer-group:nexa-document-pipeline-worker}";
    public static final String PIPELINE_FAILURE_TOPIC = "${nexa.document.pipeline.messaging.failure-topic:nexa-document-pipeline-failure}";
    public static final String PIPELINE_FAILURE_CONSUMER_GROUP = "${nexa.document.pipeline.messaging.failure-consumer-group:nexa-document-pipeline-failure-handler}";
    public static final String PIPELINE_DEAD_LETTER_TOPIC = "%DLQ%" + PIPELINE_CONSUMER_GROUP;
    public static final String PIPELINE_DEAD_LETTER_CONSUMER_GROUP = PIPELINE_FAILURE_CONSUMER_GROUP + "-dlq";
    public static final String STORAGE_CLEANUP_TOPIC = "${nexa.document.task.storage-cleanup-topic:nexa-document-storage-cleanup}";
    public static final String STORAGE_CLEANUP_CONSUMER_GROUP = "${nexa.document.task.storage-cleanup-consumer-group:nexa-document-storage-cleanup-worker}";
    public static final String STORAGE_CLEANUP_DEAD_LETTER_TOPIC = "%DLQ%" + STORAGE_CLEANUP_CONSUMER_GROUP;
    public static final String STORAGE_CLEANUP_DEAD_LETTER_CONSUMER_GROUP = "${nexa.document.task.storage-cleanup-dead-letter-consumer-group:nexa-document-storage-cleanup-dead-letter-worker}";
    public static final String INDEX_CLEANUP_TOPIC = "${nexa.document.task.cleanup-topic:nexa-document-index-cleanup}";
    public static final String INDEX_CLEANUP_CONSUMER_GROUP = "${nexa.document.task.cleanup-consumer-group:nexa-document-index-cleanup-worker}";
    public static final String INDEX_CLEANUP_DEAD_LETTER_TOPIC = "%DLQ%" + INDEX_CLEANUP_CONSUMER_GROUP;
    public static final String INDEX_CLEANUP_DEAD_LETTER_CONSUMER_GROUP = "${nexa.document.task.cleanup-failure-consumer-group:nexa-document-index-cleanup-dead-letter-worker}";
    public static final int MESSAGE_SCHEMA_VERSION = 2;
    public static final int MAX_FAILURE_DETAIL_LENGTH = 4000;
    public static final int MAX_RECONSUME_TIMES = 5;

    private DocumentMessagingConstants() {
    }
}

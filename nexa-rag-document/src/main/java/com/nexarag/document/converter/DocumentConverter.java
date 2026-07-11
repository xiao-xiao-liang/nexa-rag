package com.nexarag.document.converter;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexarag.document.entity.Document;
import com.nexarag.document.entity.DocumentChunk;
import com.nexarag.document.service.DocumentQueueInfo;
import com.nexarag.document.vo.DocumentChunkVO;
import com.nexarag.document.vo.DocumentDetailVO;
import com.nexarag.document.vo.DocumentProcessStatusVO;
import com.nexarag.document.vo.DocumentSummaryVO;
import com.nexarag.document.vo.PageVO;

/**
 * 文档对象转换器。
 */
public final class DocumentConverter {

    private DocumentConverter() {
    }

    /**
     * 转换文档摘要。
     *
     * @param document 文档实体
     * @return 文档摘要响应
     */
    public static DocumentSummaryVO toSummaryVO(Document document) {
        return new DocumentSummaryVO(document.getDocumentId(), document.getTitle(), document.getOriginalFileName(),
                document.getFileType(), document.getStatus());
    }

    /**
     * 转换文档详情。
     *
     * @param document 文档实体
     * @return 文档详情响应
     */
    public static DocumentDetailVO toDetailVO(Document document) {
        return new DocumentDetailVO(document.getDocumentId(), document.getTitle(), document.getDescription(),
                document.getOriginalFileName(), document.getFileType(), document.getFileSize(),
                document.getOriginalFileUrl(), document.getParsedFileUrl(), document.getStatus(),
                document.getProcessConfigJson());
    }

    /**
     * 转换文档处理状态。
     *
     * @param document 文档实体
     * @return 文档处理状态响应
     */
    public static DocumentProcessStatusVO toProcessStatusVO(Document document) {
        return new DocumentProcessStatusVO(document.getDocumentId(), document.getStatus(), document.getRetryCount(),
                document.getFailureStage(), document.getFailureReason());
    }

    /**
     * 转换文档处理状态并附加实时队列信息。
     *
     * @param document  文档实体
     * @param queueInfo 文档队列信息
     * @return 文档处理状态响应
     */
    public static DocumentProcessStatusVO toProcessStatusVO(Document document, DocumentQueueInfo queueInfo) {
        return new DocumentProcessStatusVO(document.getDocumentId(), document.getStatus(), document.getRetryCount(),
                document.getFailureStage(), document.getFailureReason(), queueInfo.queuePosition(),
                queueInfo.waitingCount(), queueInfo.running(), queueInfo.workerId(), queueInfo.leaseTtlSeconds());
    }

    /**
     * 转换文档片段。
     *
     * @param chunk 文档片段实体
     * @return 文档片段响应
     */
    public static DocumentChunkVO toChunkVO(DocumentChunk chunk) {
        return new DocumentChunkVO(chunk.getChunkId(), chunk.getDocumentId(), chunk.getChunkOrder(),
                chunk.getText(), chunk.getStatus());
    }

    /**
     * 将文档片段分页数据转换为分页响应。
     *
     * @param page 文档片段分页数据
     * @return 文档片段分页响应
     */
    public static PageVO<DocumentChunkVO> toChunkPageVO(IPage<DocumentChunk> page) {
        return PageVO.<DocumentChunkVO>builder()
                .records(page.getRecords().stream().map(DocumentConverter::toChunkVO).toList())
                .total(page.getTotal())
                .current(page.getCurrent())
                .size(page.getSize())
                .pages(page.getPages())
                .build();
    }
}

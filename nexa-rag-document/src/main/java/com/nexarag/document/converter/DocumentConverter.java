package com.nexarag.document.converter;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexarag.common.web.PageVO;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.model.vo.DocumentChunkStatisticsVO;
import com.nexarag.document.model.vo.DocumentChunkVO;
import com.nexarag.document.model.vo.DocumentDetailVO;
import com.nexarag.document.model.vo.DocumentOverviewVO;
import com.nexarag.document.model.vo.DocumentProcessStatusVO;
import com.nexarag.document.model.vo.DocumentSummaryVO;

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
                document.getFileType(), document.getStatus(), document.getCreateBy(), document.getUpdateTime());
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
     * 转换文档诊断概览。
     *
     * @param document   文档实体
     * @param statistics 片段状态统计
     * @return 文档诊断概览响应
     */
    public static DocumentOverviewVO toOverviewVO(Document document, DocumentChunkStatisticsVO statistics) {
        return new DocumentOverviewVO(document.getDocumentId(), document.getTitle(), document.getDescription(),
                document.getOriginalFileName(), document.getFileType(), document.getFileSize(), document.getStatus(),
                document.getSourceType(), document.getSourceUrl(), document.getProcessConfigJson(),
                document.getCreateTime(), document.getUpdateTime(), statistics);
    }

    /**
     * 转换文档处理状态。
     *
     * @param document 文档实体
     * @return 文档处理状态响应
     */
    public static DocumentProcessStatusVO toProcessStatusVO(Document document) {
        return new DocumentProcessStatusVO(document.getDocumentId(), document.getProcessId(), document.getStatus(),
                document.getMessageStatus(), document.getConsumedTimes(), document.getFailureStage(),
                document.getFailureReason());
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
        return new PageVO<>(page.getRecords().stream().map(DocumentConverter::toChunkVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }
}

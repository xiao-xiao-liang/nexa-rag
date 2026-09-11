package com.nexarag.document.converter;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.web.PageVO;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.model.vo.*;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档对象转换器。
 */
public final class DocumentConverter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DocumentConverter() {
    }

    /**
     * 将稳定文档身份和当前生效版本投影为文档摘要。
     *
     * @param document      文档稳定身份记录
     * @param activeVersion 当前生效版本，可为空
     * @return 文档摘要响应
     */
    public static DocumentSummaryVO toSummaryVO(Document document, DocumentVersionDO activeVersion) {
        return new DocumentSummaryVO(document.getDocumentId(), document.getTitle(),
                activeVersion == null ? null : activeVersion.getOriginalFileName(),
                activeVersion == null ? null : activeVersion.getFileType(),
                activeVersion == null ? null : activeVersion.getFileSize(),
                toDocumentStatus(activeVersion),
                ownerAccountName(document, activeVersion), activeVersion == null ? null : activeVersion.getUpdateTime());
    }

    /**
     * 将稳定文档身份和当前生效版本投影为文档详情。
     *
     * @param document      文档稳定身份记录
     * @param activeVersion 当前生效版本，可为空
     * @return 文档详情响应
     */
    public static DocumentDetailVO toDetailVO(Document document, DocumentVersionDO activeVersion) {
        return new DocumentDetailVO(document.getDocumentId(), document.getTitle(), document.getDescription(),
                activeVersion == null ? null : activeVersion.getOriginalFileName(),
                activeVersion == null ? null : activeVersion.getFileType(),
                activeVersion == null ? null : activeVersion.getFileSize(),
                activeVersion == null ? null : activeVersion.getOriginalFileUrl(),
                activeVersion == null ? null : activeVersion.getParsedFileUrl(), toDocumentStatus(activeVersion),
                activeVersion == null ? null : activeVersion.getProcessConfigJson());
    }

    /**
     * 将稳定文档身份、当前生效版本和版本片段统计投影为诊断概览。
     *
     * @param document      文档稳定身份记录
     * @param activeVersion 当前生效版本，可为空
     * @param statistics    当前版本片段状态统计
     * @return 文档诊断概览响应
     */
    public static DocumentOverviewVO toOverviewVO(Document document, DocumentVersionDO activeVersion,
                                                  DocumentChunkStatisticsVO statistics) {
        return new DocumentOverviewVO(document.getDocumentId(), document.getTitle(), document.getDescription(),
                activeVersion == null ? null : activeVersion.getOriginalFileName(),
                activeVersion == null ? null : activeVersion.getFileType(),
                activeVersion == null ? null : activeVersion.getFileSize(), toDocumentStatus(activeVersion),
                activeVersion == null ? null : activeVersion.getSourceType(),
                activeVersion == null ? null : activeVersion.getSourceUrl(),
                activeVersion == null ? null : activeVersion.getProcessConfigJson(),
                activeVersion == null ? null : activeVersion.getCreateTime(),
                activeVersion == null ? null : activeVersion.getUpdateTime(), statistics);
    }

    /**
     * 将当前生效版本的处理信息投影为兼容的文档处理状态。
     *
     * @param document      文档稳定身份记录
     * @param activeVersion 当前生效版本，可为空
     * @return 文档处理状态响应
     */
    public static DocumentProcessStatusVO toProcessStatusVO(Document document, DocumentVersionDO activeVersion) {
        return new DocumentProcessStatusVO(document.getDocumentId(),
                activeVersion == null ? null : activeVersion.getProcessId(), toDocumentStatus(activeVersion),
                activeVersion == null ? null : activeVersion.getMessageStatus(),
                activeVersion == null ? null : activeVersion.getConsumedTimes(),
                activeVersion == null ? null : activeVersion.getFailureStage(),
                activeVersion == null ? null : activeVersion.getFailureReason());
    }

    /**
     * 将版本状态映射为保留接口使用的文档状态。
     *
     * @param documentVersion 文档版本，可为空
     * @return 兼容文档状态；无法对外展示的版本状态返回 null
     */
    public static DocumentStatus toDocumentStatus(DocumentVersionDO documentVersion) {
        if (documentVersion == null || documentVersion.getStatus() == null) {
            return null;
        }
        return switch (documentVersion.getStatus()) {
            case UPLOADED -> DocumentStatus.UPLOADED;
            case QUEUED -> DocumentStatus.QUEUED;
            case PARSING -> DocumentStatus.PARSING;
            case PARSED -> DocumentStatus.PARSED;
            case CHUNKING -> DocumentStatus.CHUNKING;
            case CHUNKED -> DocumentStatus.CHUNKED;
            case INDEXING -> DocumentStatus.INDEXING;
            case INDEX_READY -> DocumentStatus.INDEXED;
            case FAILED -> DocumentStatus.FAILED;
            case DELETING -> null;
        };
    }

    /**
     * 解析文档所有者账号名。
     *
     * <p>历史稳定文档可能未保存创建人；这类记录回退至其当前生效版本的创建人，
     * 保证列表仍展示实际提交账号，而不伪造系统账户。</p>
     *
     * @param document 文档稳定身份记录
     * @param activeVersion 当前生效版本
     * @return 所有者账号名；无法确认时返回 {@code null}
     */
    private static String ownerAccountName(Document document, DocumentVersionDO activeVersion) {
        if (StringUtils.hasText(document.getCreateBy())) {
            return document.getCreateBy();
        }
        return activeVersion == null ? null : activeVersion.getCreateBy();
    }

    /**
     * 转换文档片段。
     *
     * @param chunk 文档片段实体
     * @return 文档片段响应
     */
    public static DocumentChunkVO toChunkVO(DocumentChunk chunk) {
        ChunkDisplayMetadata metadata = parseChunkDisplayMetadata(chunk);
        return new DocumentChunkVO(chunk.getChunkId(), chunk.getDocumentId(), chunk.getChunkOrder(),
                chunk.getText(), chunk.getStatus(), chunk.getParentChunkId(),
                Integer.valueOf(1).equals(chunk.getSkipIndex()) && metadata.parent(), metadata.displayOnlyHeading(),
                metadata.childIndex(), metadata.headingPath());
    }

    private static ChunkDisplayMetadata parseChunkDisplayMetadata(DocumentChunk chunk) {
        if (chunk == null || !StringUtils.hasText(chunk.getMetadataJson())) {
            return ChunkDisplayMetadata.empty();
        }
        try {
            JsonNode metadata = OBJECT_MAPPER.readTree(chunk.getMetadataJson());
            List<String> headingPath = new ArrayList<>();
            metadata.path("titlePath").forEach(node -> {
                String title = node.asText().trim();
                if (StringUtils.hasText(title)) {
                    headingPath.add(title);
                }
            });
            return new ChunkDisplayMetadata(metadata.path("parent").asBoolean(false),
                    metadata.path("displayOnlyHeading").asBoolean(false),
                    metadata.hasNonNull("childIndex") ? metadata.path("childIndex").asInt() : null,
                    List.copyOf(headingPath));
        } catch (Exception exception) {
            return ChunkDisplayMetadata.empty();
        }
    }

    private record ChunkDisplayMetadata(boolean parent, boolean displayOnlyHeading, Integer childIndex,
                                        List<String> headingPath) {

        private static ChunkDisplayMetadata empty() {
            return new ChunkDisplayMetadata(false, false, null, List.of());
        }
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

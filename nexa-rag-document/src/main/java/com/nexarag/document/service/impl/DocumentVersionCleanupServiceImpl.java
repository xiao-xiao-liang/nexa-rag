package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.mapper.DocumentSectionMapper;
import com.nexarag.document.mapper.DocumentVersionMapper;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentVersionCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档版本永久删除数据清理实现，始终以文档ID和版本ID为边界执行物理删除。
 */
@Service
@RequiredArgsConstructor
public class DocumentVersionCleanupServiceImpl implements DocumentVersionCleanupService {

    private final DocumentVersionMapper documentVersionMapper;
    private final DocumentSectionMapper documentSectionMapper;
    private final DocumentChunkService documentChunkService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanup(Long documentId, Long documentVersionId) {
        // 1. 查询版本并将重复消息视为幂等成功。
        DocumentVersionDO documentVersion = documentVersionMapper.selectOne(new LambdaQueryWrapper<DocumentVersionDO>()
                .eq(DocumentVersionDO::getDocumentId, documentId)
                .eq(DocumentVersionDO::getDocumentVersionId, documentVersionId));
        if (documentVersion == null) {
            return;
        }
        if (documentVersion.getStatus() != DocumentVersionStatus.DELETING) {
            throw new ServiceException("文档版本未处于删除中状态，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId);
        }

        // 2. 按版本边界物理删除派生数据，禁止影响同文档的其他版本。
        documentChunkService.deleteByDocumentVersionId(documentVersionId);
        documentSectionMapper.physicalDeleteByDocumentVersionId(documentVersionId);
        documentVersionMapper.deleteById(documentVersionId);
    }
}

package com.nexarag.document.service.impl;

import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.mapper.DocumentSectionMapper;
import com.nexarag.document.mapper.DocumentVersionMapper;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentChunkService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 文档版本数据物理清理服务测试。 */
class DocumentVersionCleanupServiceImplTest {

    @Test
    void cleanupShouldPhysicallyDeleteOnlySpecifiedDeletingVersion() {
        DocumentVersionMapper versionMapper = mock(DocumentVersionMapper.class);
        DocumentSectionMapper sectionMapper = mock(DocumentSectionMapper.class);
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        DocumentVersionCleanupServiceImpl service = new DocumentVersionCleanupServiceImpl(versionMapper, sectionMapper,
                chunkService);
        when(versionMapper.selectOne(any())).thenReturn(DocumentVersionDO.builder().documentId(1L)
                .documentVersionId(2L).status(DocumentVersionStatus.DELETING).build());

        service.cleanup(1L, 2L);

        verify(chunkService).deleteByDocumentVersionId(2L);
        verify(sectionMapper).physicalDeleteByDocumentVersionId(2L);
        verify(versionMapper).deleteById(2L);
    }
}

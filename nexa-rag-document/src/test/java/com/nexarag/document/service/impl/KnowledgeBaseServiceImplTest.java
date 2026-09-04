package com.nexarag.document.service.impl;

import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.mapper.DocumentMapper;
import com.nexarag.document.mapper.KnowledgeBaseMapper;
import com.nexarag.document.model.dataobject.KnowledgeBaseDO;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.DocumentVersionService;
import com.nexarag.document.tenant.CurrentTenantProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 知识库服务实现测试。
 */
class KnowledgeBaseServiceImplTest {

    @Test
    void statisticsShouldUseActiveVersionStatusInsteadOfDocumentLegacyStatus() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        CurrentTenantProvider tenantProvider = mock(CurrentTenantProvider.class);
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(knowledgeBaseMapper, documentMapper,
                tenantProvider);
        KnowledgeBaseDO knowledgeBase = KnowledgeBaseDO.builder().knowledgeBaseId(1L).tenantId("tenant-1")
                .name("知识库").isDefault(0).build();
        when(tenantProvider.getRequiredTenantId()).thenReturn("tenant-1");
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(knowledgeBase);
        when(documentMapper.aggregateStatisticsByKnowledgeBaseIds(any())).thenReturn(List.of(
                new com.nexarag.document.model.bo.KnowledgeBaseDocumentStatisticsBO(1L, 1L, 0L, 0L, 1L, 0L)));

        var detail = service.getDetail(1L);

        assertThat(detail.statistics().totalCount()).isEqualTo(1L);
        assertThat(detail.statistics().indexedCount()).isEqualTo(1L);
        assertThat(detail.statistics().failedCount()).isZero();
    }
}

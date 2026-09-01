package com.nexarag.boot.command;

import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.mapper.DocumentVersionMapper;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.retrieval.service.DocumentIndexService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 文档版本索引回填启动任务测试。 */
class DocumentVersionIndexBackfillRunnerTest {

    @Test
    void runShouldRebuildReadyFirstVersionIndexesWhenEnabled() throws Exception {
        DocumentVersionIndexBackfillProperties properties = new DocumentVersionIndexBackfillProperties();
        properties.setEnabled(true);
        properties.setBatchSize(10);
        DocumentVersionMapper versionMapper = mock(DocumentVersionMapper.class);
        DocumentIndexService indexService = mock(DocumentIndexService.class);
        Page<DocumentVersionDO> page = Page.of(1, 10);
        page.setRecords(List.of(DocumentVersionDO.builder().documentId(1L)
                .documentVersionId(2L).revisionNo(1L).status(DocumentVersionStatus.INDEX_READY).build()));
        when(versionMapper.selectPage(any(), any())).thenReturn(page);
        DocumentVersionIndexBackfillRunner runner = new DocumentVersionIndexBackfillRunner(properties, versionMapper,
                indexService);

        runner.run(null);

        verify(indexService).rebuildDocumentVersionIndex(1L, 2L);
    }
}

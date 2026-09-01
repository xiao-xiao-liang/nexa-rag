package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexarag.common.exception.ClientException;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.enums.FileType;
import com.nexarag.document.mapper.DocumentMapper;
import com.nexarag.document.mapper.DocumentVersionMapper;
import com.nexarag.document.mapper.DocumentVersionOperationLogMapper;
import com.nexarag.document.model.dto.DocumentVersionUploadDTO;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.model.entity.DocumentVersionOperationLogDO;
import com.nexarag.document.model.vo.DocumentVersionVO;
import com.nexarag.document.model.vo.DocumentVersionOperationLogVO;
import com.nexarag.document.service.DocumentDeleteTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档版本服务实现测试。
 */
class DocumentVersionServiceImplTest {

    @Test
    void createNextVersionShouldRejectWhenDocumentHasBuildingVersion() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionServiceImpl service = newService(documentMapper, mock(DocumentVersionMapper.class));
        when(documentMapper.selectById(1L)).thenReturn(Document.builder()
                .documentId(1L)
                .buildingVersionId(2L)
                .build());

        assertThatThrownBy(() -> service.createNextVersion(1L, upload(), "process-1", "user-1"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("存在处理中版本");
    }

    @Test
    void createNextVersionShouldPersistNextRevisionAndSetBuildingPointer() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionServiceImpl service = newService(documentMapper, documentVersionMapper);
        when(documentMapper.selectById(1L)).thenReturn(Document.builder().documentId(1L).build());
        when(documentVersionMapper.selectCount(any())).thenReturn(2L);
        when(documentMapper.trySetBuildingVersionId(org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(1);

        DocumentVersionDO version = service.createNextVersion(1L, upload(), "process-1", "user-1");

        assertThat(version.getDocumentId()).isEqualTo(1L);
        assertThat(version.getRevisionNo()).isEqualTo(3L);
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.UPLOADED);
        assertThat(version.getProcessId()).isEqualTo("process-1");
        assertThat(version.getDocumentVersionId()).isNotNull();
        ArgumentCaptor<DocumentVersionDO> versionCaptor = ArgumentCaptor.forClass(DocumentVersionDO.class);
        verify(documentVersionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue()).isSameAs(version);
        verify(documentMapper).trySetBuildingVersionId(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(version.getDocumentVersionId()));
    }

    @Test
    void listVersionsShouldReturnDescendingRevisions() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionServiceImpl service = newService(documentMapper, documentVersionMapper);
        IPage<DocumentVersionDO> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20, 3);
        page.setRecords(List.of(version(3L), version(2L), version(1L)));
        when(documentVersionMapper.selectPage(any(), any())).thenReturn(page);
        when(documentMapper.selectById(1L)).thenReturn(Document.builder().documentId(1L).activeVersionId(2L).build());

        List<DocumentVersionVO> versions = service.listVersions(1L, 1, 20).getRecords();

        assertThat(versions).extracting(DocumentVersionVO::revisionNo).containsExactly(3L, 2L, 1L);
        assertThat(versions).extracting(DocumentVersionVO::active).containsExactly(false, true, false);
    }

    @Test
    void findActiveVersionsShouldOnlyReturnVersionsMatchingDocumentPointers() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionServiceImpl service = newService(documentMapper, documentVersionMapper);
        Document firstDocument = Document.builder().documentId(1L).activeVersionId(11L).build();
        Document secondDocument = Document.builder().documentId(2L).activeVersionId(22L).build();
        when(documentVersionMapper.selectList(any())).thenReturn(List.of(
                DocumentVersionDO.builder().documentId(1L).documentVersionId(11L).build(),
                DocumentVersionDO.builder().documentId(1L).documentVersionId(22L).build()));

        var activeVersions = service.findActiveVersions(List.of(firstDocument, secondDocument));

        assertThat(activeVersions).containsOnlyKeys(1L);
        assertThat(activeVersions.get(1L).getDocumentVersionId()).isEqualTo(11L);
    }

    @Test
    void markAllVersionsDeletingShouldPrepareEveryNonDeletingVersionForDocumentCleanup() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionOperationLogMapper operationLogMapper = mock(DocumentVersionOperationLogMapper.class);
        DocumentVersionServiceImpl service = new DocumentVersionServiceImpl(documentMapper, documentVersionMapper,
                operationLogMapper, mock(DocumentDeleteTaskService.class));
        DocumentVersionDO readyVersion = version(1L).toBuilder().documentVersionId(11L)
                .status(DocumentVersionStatus.INDEX_READY).build();
        DocumentVersionDO processingVersion = version(2L).toBuilder().documentVersionId(12L)
                .status(DocumentVersionStatus.PARSING).build();
        when(documentVersionMapper.selectList(any())).thenReturn(List.of(readyVersion, processingVersion));
        when(documentVersionMapper.markDeletingForDocument(1L, 11L, "operator-1")).thenReturn(1);
        when(documentVersionMapper.markDeletingForDocument(1L, 12L, "operator-1")).thenReturn(1);

        List<DocumentVersionDO> deletingVersions = service.markAllVersionsDeleting(1L, "operator-1");

        assertThat(deletingVersions).extracting(DocumentVersionDO::getDocumentVersionId).containsExactly(11L, 12L);
        assertThat(deletingVersions).allMatch(version -> version.getStatus() == DocumentVersionStatus.DELETING);
        verify(documentVersionMapper).markDeletingForDocument(1L, 11L, "operator-1");
        verify(documentVersionMapper).markDeletingForDocument(1L, 12L, "operator-1");
        verify(operationLogMapper, org.mockito.Mockito.times(2))
                .insert(org.mockito.ArgumentMatchers.<DocumentVersionOperationLogDO>any());
    }

    @Test
    void listOperationLogsShouldKeepDeletedVersionAuditRecordsQueryable() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionOperationLogMapper operationLogMapper = mock(DocumentVersionOperationLogMapper.class);
        DocumentVersionServiceImpl service = new DocumentVersionServiceImpl(documentMapper, documentVersionMapper,
                operationLogMapper, mock(DocumentDeleteTaskService.class));
        IPage<DocumentVersionOperationLogDO> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20, 1);
        page.setRecords(List.of(DocumentVersionOperationLogDO.builder().operationLogId(10L).documentId(1L)
                .documentVersionId(2L).operationType(com.nexarag.document.enums.DocumentVersionOperationType.DELETE)
                .operationDetail("永久删除历史版本").build()));
        when(operationLogMapper.selectPage(any(), any())).thenReturn(page);

        List<DocumentVersionOperationLogVO> records = service.listOperationLogs(1L, 1, 20).getRecords();

        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.documentVersionId()).isEqualTo(2L);
            assertThat(record.operationType()).isEqualTo(com.nexarag.document.enums.DocumentVersionOperationType.DELETE);
        });
    }

    @Test
    void recordMessageConsumptionShouldUpdateOnlySpecifiedVersionAndProcess() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionServiceImpl service = newService(documentMapper, documentVersionMapper);
        when(documentVersionMapper.recordMessageConsumption(1L, 2L, "process-1", "message-1", 3))
                .thenReturn(1);

        assertThat(service.recordMessageConsumption(1L, 2L, "process-1", "message-1", 3)).isTrue();

        verify(documentVersionMapper).recordMessageConsumption(1L, 2L, "process-1", "message-1", 3);
    }

    @Test
    void recordRetryableFailureShouldUpdateOnlySpecifiedVersionAndProcess() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionServiceImpl service = newService(documentMapper, documentVersionMapper);
        when(documentVersionMapper.recordRetryableFailure(1L, 2L, "process-1", "PARSING",
                "临时网络异常", "detail")).thenReturn(1);

        assertThat(service.recordRetryableFailure(1L, 2L, "process-1", "PARSING", "临时网络异常", "detail"))
                .isTrue();

        verify(documentVersionMapper).recordRetryableFailure(1L, 2L, "process-1", "PARSING",
                "临时网络异常", "detail");
    }

    @Test
    void markMessageCompletedShouldUpdateOnlySpecifiedVersionAndProcess() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionServiceImpl service = newService(documentMapper, documentVersionMapper);
        when(documentVersionMapper.markMessageCompleted(1L, 2L, "process-1")).thenReturn(1);

        assertThat(service.markMessageCompleted(1L, 2L, "process-1")).isTrue();

        verify(documentVersionMapper).markMessageCompleted(1L, 2L, "process-1");
    }

    @Test
    void markIndexingShouldOnlyAdvanceTheSpecifiedVersionAndProcess() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionServiceImpl service = newService(documentMapper, documentVersionMapper);
        when(documentVersionMapper.markIndexing(1L, 2L, "process-1")).thenReturn(1);

        assertThat(service.markIndexing(1L, 2L, "process-1")).isTrue();

        verify(documentVersionMapper).markIndexing(1L, 2L, "process-1");
    }

    @Test
    void markIndexReadyShouldActivateTheCompletedVersionAndRecordAudit() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionOperationLogMapper operationLogMapper = mock(DocumentVersionOperationLogMapper.class);
        DocumentVersionServiceImpl service = new DocumentVersionServiceImpl(documentMapper, documentVersionMapper,
                operationLogMapper, mock(DocumentDeleteTaskService.class));
        when(documentVersionMapper.markIndexReady(1L, 2L, "process-1")).thenReturn(1);
        when(documentMapper.activateVersion(1L, 2L)).thenReturn(1);
        when(documentMapper.selectById(1L)).thenReturn(Document.builder().documentId(1L)
                .activeVersionId(2L).activationGeneration(3L).build());

        assertThat(service.markIndexReady(1L, 2L, "process-1")).isTrue();

        verify(documentVersionMapper).markIndexReady(1L, 2L, "process-1");
        verify(documentMapper).activateVersion(1L, 2L);
        verify(operationLogMapper).insert(org.mockito.ArgumentMatchers.<DocumentVersionOperationLogDO>any());
    }

    @Test
    void activateReadyVersionShouldSwitchHistoricalReadyVersionAndWriteRollbackAudit() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionOperationLogMapper operationLogMapper = mock(DocumentVersionOperationLogMapper.class);
        DocumentVersionServiceImpl service = new DocumentVersionServiceImpl(documentMapper, documentVersionMapper,
                operationLogMapper, mock(DocumentDeleteTaskService.class));
        when(documentMapper.selectById(1L)).thenReturn(
                Document.builder().documentId(1L).activeVersionId(10L).activationGeneration(2L).build(),
                Document.builder().documentId(1L).activeVersionId(20L).activationGeneration(3L).build());
        when(documentVersionMapper.selectOne(any())).thenReturn(DocumentVersionDO.builder()
                .documentId(1L).documentVersionId(20L).status(DocumentVersionStatus.INDEX_READY).build());
        when(documentMapper.activateReadyVersion(1L, 20L)).thenReturn(1);

        service.activateReadyVersion(1L, 20L, "user-1");

        verify(documentMapper).activateReadyVersion(1L, 20L);
        verify(operationLogMapper).insert(org.mockito.ArgumentMatchers.<DocumentVersionOperationLogDO>argThat(log ->
                log.getOperationType() == com.nexarag.document.enums.DocumentVersionOperationType.ROLLBACK
                        && log.getDocumentVersionId().equals(20L)
                        && log.getActivationGeneration().equals(3L)));
    }

    @Test
    void retryFailedVersionShouldRenewProcessAndOccupyBuildingSlot() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionOperationLogMapper operationLogMapper = mock(DocumentVersionOperationLogMapper.class);
        DocumentVersionServiceImpl service = new DocumentVersionServiceImpl(documentMapper, documentVersionMapper,
                operationLogMapper, mock(DocumentDeleteTaskService.class));
        when(documentMapper.selectById(1L)).thenReturn(Document.builder().documentId(1L).build());
        when(documentVersionMapper.selectOne(any())).thenReturn(DocumentVersionDO.builder().documentId(1L)
                .documentVersionId(2L).status(DocumentVersionStatus.FAILED).build());
        when(documentMapper.trySetBuildingVersionId(1L, 2L)).thenReturn(1);
        when(documentVersionMapper.retryFailedVersion(1L, 2L, "process-2", "user-1")).thenReturn(1);

        DocumentVersionDO retried = service.retryFailedVersion(1L, 2L, "process-2", "user-1");

        assertThat(retried.getStatus()).isEqualTo(DocumentVersionStatus.QUEUED);
        assertThat(retried.getProcessId()).isEqualTo("process-2");
        verify(operationLogMapper).insert(org.mockito.ArgumentMatchers.<DocumentVersionOperationLogDO>argThat(log ->
                log.getOperationType() == com.nexarag.document.enums.DocumentVersionOperationType.RETRY));
    }

    @Test
    void requestPermanentDeleteShouldMarkInactiveReadyVersionAndCreateIndexCleanupTask() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionOperationLogMapper operationLogMapper = mock(DocumentVersionOperationLogMapper.class);
        DocumentDeleteTaskService deleteTaskService = mock(DocumentDeleteTaskService.class);
        DocumentVersionServiceImpl service = new DocumentVersionServiceImpl(documentMapper, documentVersionMapper,
                operationLogMapper, deleteTaskService);
        when(documentMapper.selectById(1L)).thenReturn(Document.builder().documentId(1L)
                .activeVersionId(10L).build());
        when(documentVersionMapper.selectOne(any())).thenReturn(DocumentVersionDO.builder().documentId(1L)
                .documentVersionId(20L).status(DocumentVersionStatus.INDEX_READY).build());
        when(documentVersionMapper.markDeleting(1L, 20L, "user-1")).thenReturn(1);

        service.requestPermanentDelete(1L, 20L, "user-1");

        verify(documentVersionMapper).markDeleting(1L, 20L, "user-1");
        verify(deleteTaskService).createVersionIndexCleanupTask(1L, 20L);
        verify(operationLogMapper).insert(org.mockito.ArgumentMatchers.<DocumentVersionOperationLogDO>argThat(log ->
                log.getOperationType() == com.nexarag.document.enums.DocumentVersionOperationType.DELETE
                        && log.getDocumentVersionId().equals(20L)));
    }

    @Test
    void requestPermanentDeleteShouldRejectActiveVersion() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentVersionMapper documentVersionMapper = mock(DocumentVersionMapper.class);
        DocumentVersionServiceImpl service = newService(documentMapper, documentVersionMapper);
        when(documentMapper.selectById(1L)).thenReturn(Document.builder().documentId(1L)
                .activeVersionId(20L).build());

        assertThatThrownBy(() -> service.requestPermanentDelete(1L, 20L, "user-1"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("当前生效版本");
    }

    private DocumentVersionServiceImpl newService(DocumentMapper documentMapper,
                                                   DocumentVersionMapper documentVersionMapper) {
        return new DocumentVersionServiceImpl(documentMapper, documentVersionMapper,
                mock(DocumentVersionOperationLogMapper.class), mock(DocumentDeleteTaskService.class));
    }

    private DocumentVersionUploadDTO upload() {
        return DocumentVersionUploadDTO.builder()
                .originalFileName("demo.pdf")
                .fileType(FileType.PDF)
                .fileSize(128L)
                .originalObjectName("original/demo.pdf")
                .originalFileUrl("minio://demo.pdf")
                .build();
    }

    private DocumentVersionDO version(long revisionNo) {
        return DocumentVersionDO.builder()
                .documentVersionId(revisionNo)
                .documentId(1L)
                .revisionNo(revisionNo)
                .originalFileName("demo.pdf")
                .fileType(FileType.PDF)
                .status(DocumentVersionStatus.INDEX_READY)
                .build();
    }
}

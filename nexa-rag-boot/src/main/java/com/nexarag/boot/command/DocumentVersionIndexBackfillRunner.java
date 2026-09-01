package com.nexarag.boot.command;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.mapper.DocumentVersionMapper;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.retrieval.service.DocumentIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;


/**
 * 应用启动后的受控 V1 索引元数据回填任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.document.version.backfill", name = "enabled", havingValue = "true")
public class DocumentVersionIndexBackfillRunner implements ApplicationRunner {

    private final DocumentVersionIndexBackfillProperties properties;
    private final DocumentVersionMapper documentVersionMapper;
    private final DocumentIndexService documentIndexService;

    @Override
    public void run(ApplicationArguments args) {
        // 1. 按稳定版本ID分页读取所有已就绪 V1，避免依赖数据库方言的 LIMIT 片段。
        long pageNum = 1;
        long processedCount = 0;
        while (true) {
            Page<DocumentVersionDO> page = documentVersionMapper.selectPage(Page.of(pageNum, properties.getBatchSize()),
                    new LambdaQueryWrapper<DocumentVersionDO>()
                            .eq(DocumentVersionDO::getRevisionNo, 1L)
                            .eq(DocumentVersionDO::getStatus, DocumentVersionStatus.INDEX_READY)
                            .orderByAsc(DocumentVersionDO::getDocumentVersionId));
            for (DocumentVersionDO version : page.getRecords()) {
                documentIndexService.rebuildDocumentVersionIndex(version.getDocumentId(), version.getDocumentVersionId());
                processedCount++;
            }
            if (page.getRecords().size() < properties.getBatchSize()) {
                break;
            }
            pageNum++;
        }
        log.info("文档版本索引元数据回填完成，处理数量={}", processedCount);
    }
}

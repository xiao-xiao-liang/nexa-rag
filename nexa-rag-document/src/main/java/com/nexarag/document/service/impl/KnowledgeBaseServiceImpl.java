package com.nexarag.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.web.PageVO;
import com.nexarag.document.converter.KnowledgeBaseConverter;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.mapper.DocumentMapper;
import com.nexarag.document.mapper.KnowledgeBaseMapper;
import com.nexarag.document.model.bo.KnowledgeBaseDocumentStatisticsBO;
import com.nexarag.document.model.dataobject.KnowledgeBaseDO;
import com.nexarag.document.model.dto.CreateKnowledgeBaseDTO;
import com.nexarag.document.model.dto.UpdateKnowledgeBaseDTO;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.vo.KnowledgeBaseDetailVO;
import com.nexarag.document.model.vo.KnowledgeBaseStatisticsVO;
import com.nexarag.document.model.vo.KnowledgeBaseSummaryVO;
import com.nexarag.document.service.KnowledgeBaseService;
import com.nexarag.document.tenant.CurrentTenantProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 知识库服务实现，确保知识库和文档访问始终限制在当前租户内。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBaseDO>
        implements KnowledgeBaseService {

    private static final long DEFAULT_PAGE_SIZE = 20;
    private static final long MAX_PAGE_SIZE = 100;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final CurrentTenantProvider currentTenantProvider;

    /**
     * 在当前租户创建知识库。
     *
     * @param request 创建请求
     * @return 创建后的知识库详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseDetailVO create(CreateKnowledgeBaseDTO request) {
        String tenantId = currentTenantProvider.getRequiredTenantId();
        String name = normalizeName(request.name());

        // 1. 预先校验租户内名称，提供稳定的业务错误码
        ensureNameAvailable(tenantId, name, null);

        // 2. 写入新的知识库记录
        KnowledgeBaseDO knowledgeBase = KnowledgeBaseDO.builder()
                .knowledgeBaseId(IdWorker.getId())
                .tenantId(tenantId)
                .name(name)
                .activeNameKey(name)
                .description(normalizeDescription(request.description()))
                .isDefault(0)
                .build();
        try {
            knowledgeBaseMapper.insert(knowledgeBase);
        } catch (DuplicateKeyException exception) {
            throw duplicateNameException(name, exception);
        }
        log.info("创建知识库成功，tenantId={}，knowledgeBaseId={}", tenantId, knowledgeBase.getKnowledgeBaseId());
        return KnowledgeBaseConverter.toDetailVO(knowledgeBase, emptyStatistics());
    }

    /**
     * 分页查询当前租户的知识库摘要。
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 知识库分页结果
     */
    @Override
    public PageVO<KnowledgeBaseSummaryVO> pageKnowledgeBases(long pageNum, long pageSize) {
        long safePageNum = pageNum <= 0 ? 1 : pageNum;
        long safePageSize = normalizePageSize(pageSize);
        String tenantId = currentTenantProvider.getRequiredTenantId();

        // 1. 在当前租户范围内分页查询知识库
        IPage<KnowledgeBaseDO> page = knowledgeBaseMapper.selectPage(Page.of(safePageNum, safePageSize),
                new LambdaQueryWrapper<KnowledgeBaseDO>()
                        .eq(KnowledgeBaseDO::getTenantId, tenantId)
                        .orderByDesc(KnowledgeBaseDO::getUpdateTime));

        // 2. 批量统计当前页知识库中的文档状态，避免逐库查询。
        Map<Long, KnowledgeBaseStatisticsVO> statisticsByKnowledgeBaseId = calculateStatistics(page.getRecords()
                .stream()
                .map(KnowledgeBaseDO::getKnowledgeBaseId)
                .toList());

        // 3. 组装包含文档处理状态统计的摘要结果
        List<KnowledgeBaseSummaryVO> records = page.getRecords().stream()
                .map(knowledgeBase -> KnowledgeBaseConverter.toSummaryVO(knowledgeBase,
                        statisticsByKnowledgeBaseId.getOrDefault(knowledgeBase.getKnowledgeBaseId(), emptyStatistics())))
                .toList();
        return new PageVO<>(records, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /**
     * 查询当前租户内知识库详情。
     *
     * @param knowledgeBaseId 知识库ID
     * @return 知识库详情
     */
    @Override
    public KnowledgeBaseDetailVO getDetail(Long knowledgeBaseId) {
        KnowledgeBaseDO knowledgeBase = getRequiredKnowledgeBase(knowledgeBaseId);
        return KnowledgeBaseConverter.toDetailVO(knowledgeBase, calculateStatistics(knowledgeBaseId));
    }

    /**
     * 更新当前租户内知识库的名称和描述；默认知识库不可改名。
     *
     * @param knowledgeBaseId 知识库ID
     * @param request         更新请求
     * @return 更新后的知识库详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseDetailVO update(Long knowledgeBaseId, UpdateKnowledgeBaseDTO request) {
        KnowledgeBaseDO knowledgeBase = getRequiredKnowledgeBase(knowledgeBaseId);
        String name = normalizeName(request.name());
        if (isDefaultKnowledgeBase(knowledgeBase) && !knowledgeBase.getName().equals(name)) {
            throw new ClientException("默认知识库不可重命名，knowledgeBaseId=" + knowledgeBaseId,
                    DocumentErrorCode.DEFAULT_KNOWLEDGE_BASE_PROTECTED);
        }
        ensureNameAvailable(knowledgeBase.getTenantId(), name, knowledgeBaseId);

        // 1. 更新知识库基础信息，名称变更时同步更新唯一键
        KnowledgeBaseDO updatedKnowledgeBase = knowledgeBase.toBuilder()
                .name(name)
                .activeNameKey(name)
                .description(normalizeDescription(request.description()))
                .build();
        try {
            knowledgeBaseMapper.updateById(updatedKnowledgeBase);
        } catch (DuplicateKeyException exception) {
            throw duplicateNameException(name, exception);
        }
        log.info("更新知识库成功，tenantId={}，knowledgeBaseId={}",
                updatedKnowledgeBase.getTenantId(), knowledgeBaseId);
        return KnowledgeBaseConverter.toDetailVO(updatedKnowledgeBase, calculateStatistics(knowledgeBaseId));
    }

    /**
     * 删除当前租户内为空且非默认的知识库。
     *
     * @param knowledgeBaseId 知识库ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long knowledgeBaseId) {
        // 1. 锁定知识库记录，和文档创建操作串行化。
        KnowledgeBaseDO knowledgeBase = getRequiredActiveKnowledgeBaseForUpdate(knowledgeBaseId);
        if (isDefaultKnowledgeBase(knowledgeBase)) {
            throw new ClientException("默认知识库不可删除，knowledgeBaseId=" + knowledgeBaseId,
                    DocumentErrorCode.DEFAULT_KNOWLEDGE_BASE_PROTECTED);
        }
        if (countDocuments(knowledgeBaseId) > 0) {
            throw new ClientException("知识库仍包含文档，knowledgeBaseId=" + knowledgeBaseId,
                    DocumentErrorCode.KNOWLEDGE_BASE_NOT_EMPTY);
        }

        // 2. 逻辑删除并释放活跃名称唯一键，允许后续创建同名知识库。
        LocalDateTime now = LocalDateTime.now();
        int updated = knowledgeBaseMapper.update(null, new LambdaUpdateWrapper<KnowledgeBaseDO>()
                .eq(KnowledgeBaseDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeBaseDO::getTenantId, knowledgeBase.getTenantId())
                .eq(KnowledgeBaseDO::getDelFlag, 0)
                .set(KnowledgeBaseDO::getActiveNameKey, null)
                .set(KnowledgeBaseDO::getDefaultTenantKey, null)
                .set(KnowledgeBaseDO::getDelFlag, 1)
                .set(KnowledgeBaseDO::getDeleteTime, now));
        if (updated != 1) {
            throw new ClientException("知识库状态已变化，请重试，knowledgeBaseId=" + knowledgeBaseId,
                    DocumentErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        log.info("删除空知识库成功，tenantId={}，knowledgeBaseId={}", knowledgeBase.getTenantId(), knowledgeBaseId);
    }

    /**
     * 获取当前租户内的知识库，不存在时抛出业务异常。
     *
     * @param knowledgeBaseId 知识库ID
     * @return 知识库数据对象
     */
    @Override
    public KnowledgeBaseDO getRequiredKnowledgeBase(Long knowledgeBaseId) {
        String tenantId = currentTenantProvider.getRequiredTenantId();
        KnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseDO>()
                .eq(KnowledgeBaseDO::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeBaseDO::getTenantId, tenantId));
        if (knowledgeBase == null) {
            throw new ClientException("知识库不存在或不属于当前租户，knowledgeBaseId=" + knowledgeBaseId,
                    DocumentErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        return knowledgeBase;
    }

    /**
     * 锁定当前租户内仍有效的知识库，防止文档创建与删除操作交错。
     *
     * @param knowledgeBaseId 知识库ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockRequiredActiveKnowledgeBase(Long knowledgeBaseId) {
        getRequiredActiveKnowledgeBaseForUpdate(knowledgeBaseId);
    }

    /**
     * 获取归属于指定知识库的文档，不允许跨知识库访问。
     *
     * @param knowledgeBaseId 知识库ID
     * @param documentId      文档ID
     * @return 文档实体
     */
    @Override
    public Document getRequiredDocument(Long knowledgeBaseId, Long documentId) {
        getRequiredKnowledgeBase(knowledgeBaseId);
        Document document = documentMapper.selectOne(new LambdaQueryWrapper<Document>()
                .eq(Document::getDocumentId, documentId)
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId));
        if (document == null) {
            throw new ClientException("文档不存在或不属于知识库，knowledgeBaseId=" + knowledgeBaseId + "，documentId=" + documentId,
                    DocumentErrorCode.DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    /**
     * 校验指定知识库集合均归属于当前租户。
     *
     * @param knowledgeBaseIds 待校验知识库ID集合；为空表示检索全部知识库
     * @return 去重后的知识库ID集合
     */
    @Override
    public Set<Long> validateRequestedKnowledgeBases(Collection<Long> knowledgeBaseIds) {
        return validateRequestedKnowledgeBases(currentTenantProvider.getRequiredTenantId(), knowledgeBaseIds);
    }

    /**
     * 校验指定租户范围内的知识库，避免异步任务依赖线程绑定的当前租户。
     *
     * @param tenantId         可信租户ID
     * @param knowledgeBaseIds 待校验知识库ID集合；为空表示检索全部知识库
     * @return 去重后的知识库ID集合
     */
    @Override
    public Set<Long> validateRequestedKnowledgeBases(String tenantId, Collection<Long> knowledgeBaseIds) {
        requireTenantId(tenantId);
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> distinctIds = new LinkedHashSet<>(knowledgeBaseIds);
        if (distinctIds.contains(null)) {
            throw new ClientException("知识库ID不能为空", DocumentErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        Long count = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseDO>()
                .eq(KnowledgeBaseDO::getTenantId, tenantId)
                .in(KnowledgeBaseDO::getKnowledgeBaseId, distinctIds));
        if (count == null || count != distinctIds.size()) {
            throw new ClientException("指定知识库不存在或不属于当前租户", DocumentErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        return Set.copyOf(distinctIds);
    }

    /**
     * 判断文档是否属于当前租户，并且满足可选知识库范围。
     *
     * @param documentId       文档ID
     * @param knowledgeBaseIds 已校验的知识库范围；为空表示全部知识库
     * @return true 表示文档在当前可访问范围内
     */
    @Override
    public boolean isDocumentInCurrentTenantScope(Long documentId, Set<Long> knowledgeBaseIds) {
        if (documentId == null) {
            return false;
        }
        return filterDocumentIdsInCurrentTenantScope(List.of(documentId), knowledgeBaseIds).contains(documentId);
    }

    /**
     * 批量校验文档与当前租户知识库的归属关系，避免召回结果逐条触发数据库查询。
     *
     * @param documentIds      待校验的文档ID集合
     * @param knowledgeBaseIds 已校验的知识库范围；为空表示当前租户全部知识库
     * @return 当前请求可访问的文档ID集合
     */
    @Override
    public Set<Long> filterDocumentIdsInCurrentTenantScope(Collection<Long> documentIds,
                                                           Set<Long> knowledgeBaseIds) {
        return filterDocumentIdsInTenantScope(currentTenantProvider.getRequiredTenantId(), documentIds, knowledgeBaseIds);
    }

    /**
     * 批量校验文档与指定租户知识库的归属关系，避免异步任务依赖线程绑定的当前租户。
     *
     * @param tenantId         可信租户ID
     * @param documentIds      待校验的文档ID集合
     * @param knowledgeBaseIds 已校验的知识库范围；为空表示该租户全部知识库
     * @return 当前请求可访问的文档ID集合
     */
    @Override
    public Set<Long> filterDocumentIdsInTenantScope(String tenantId, Collection<Long> documentIds,
                                                    Set<Long> knowledgeBaseIds) {
        requireTenantId(tenantId);
        if (documentIds == null || documentIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> distinctDocumentIds = new LinkedHashSet<>(documentIds);
        distinctDocumentIds.remove(null);
        if (distinctDocumentIds.isEmpty()) {
            return Set.of();
        }

        // 1. 批量查询召回文档的知识库归属。
        List<Document> documents = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .in(Document::getDocumentId, distinctDocumentIds));
        Set<Long> documentKnowledgeBaseIds = documents.stream()
                .map(Document::getKnowledgeBaseId)
                .filter(java.util.Objects::nonNull)
                .filter(knowledgeBaseId -> knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()
                        || knowledgeBaseIds.contains(knowledgeBaseId))
                .collect(java.util.stream.Collectors.toSet());
        if (documentKnowledgeBaseIds.isEmpty()) {
            return Set.of();
        }

        // 2. 批量确认知识库均属于当前租户且未被逻辑删除。
        Set<Long> activeKnowledgeBaseIds = knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseDO>()
                        .eq(KnowledgeBaseDO::getTenantId, tenantId)
                        .in(KnowledgeBaseDO::getKnowledgeBaseId, documentKnowledgeBaseIds))
                .stream()
                .map(KnowledgeBaseDO::getKnowledgeBaseId)
                .collect(java.util.stream.Collectors.toSet());

        // 3. 返回同时满足文档归属、指定范围和租户范围的文档ID。
        return documents.stream()
                .filter(document -> document.getKnowledgeBaseId() != null)
                .filter(document -> activeKnowledgeBaseIds.contains(document.getKnowledgeBaseId()))
                .filter(document -> knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()
                        || knowledgeBaseIds.contains(document.getKnowledgeBaseId()))
                .map(Document::getDocumentId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public Map<Long, Long> findActiveVersionIdsInTenantScope(String tenantId, Collection<Long> documentIds,
                                                             Set<Long> knowledgeBaseIds) {
        Set<Long> accessibleDocumentIds = filterDocumentIdsInTenantScope(tenantId, documentIds, knowledgeBaseIds);
        if (accessibleDocumentIds.isEmpty()) {
            return Map.of();
        }
        return documentMapper.selectList(new LambdaQueryWrapper<Document>()
                        .in(Document::getDocumentId, accessibleDocumentIds)
                        .isNotNull(Document::getActiveVersionId))
                .stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Document::getDocumentId,
                        Document::getActiveVersionId));
    }

    @Override
    public Set<Long> listActiveVersionIdsInTenantScope(String tenantId, Set<Long> knowledgeBaseIds) {
        requireTenantId(tenantId);
        List<KnowledgeBaseDO> knowledgeBases = knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseDO>()
                .eq(KnowledgeBaseDO::getTenantId, tenantId));
        Set<Long> allowedKnowledgeBaseIds = knowledgeBases.stream().map(KnowledgeBaseDO::getKnowledgeBaseId)
                .filter(id -> knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || knowledgeBaseIds.contains(id))
                .collect(java.util.stream.Collectors.toSet());
        if (allowedKnowledgeBaseIds.isEmpty()) return Set.of();
        return documentMapper.selectList(new LambdaQueryWrapper<Document>()
                        .in(Document::getKnowledgeBaseId, allowedKnowledgeBaseIds)
                        .isNotNull(Document::getActiveVersionId))
                .stream().map(Document::getActiveVersionId).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * 确保异步调用方传入已在入口处验证的可信租户ID。
     *
     * @param tenantId 租户ID
     */
    private void requireTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
    }

    private void ensureNameAvailable(String tenantId, String name, Long excludedKnowledgeBaseId) {
        LambdaQueryWrapper<KnowledgeBaseDO> wrapper = new LambdaQueryWrapper<KnowledgeBaseDO>()
                .eq(KnowledgeBaseDO::getTenantId, tenantId)
                .eq(KnowledgeBaseDO::getActiveNameKey, name);
        if (excludedKnowledgeBaseId != null) {
            wrapper.ne(KnowledgeBaseDO::getKnowledgeBaseId, excludedKnowledgeBaseId);
        }
        if (knowledgeBaseMapper.selectCount(wrapper) > 0) {
            throw duplicateNameException(name, null);
        }
    }

    private KnowledgeBaseStatisticsVO calculateStatistics(Long knowledgeBaseId) {
        return calculateStatistics(List.of(knowledgeBaseId)).getOrDefault(knowledgeBaseId, emptyStatistics());
    }

    private Map<Long, KnowledgeBaseStatisticsVO> calculateStatistics(Collection<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, KnowledgeBaseStatisticsVO> statisticsByKnowledgeBaseId = new HashMap<>();
        for (KnowledgeBaseDocumentStatisticsBO statistics
                : documentMapper.aggregateStatisticsByKnowledgeBaseIds(knowledgeBaseIds)) {
            statisticsByKnowledgeBaseId.put(statistics.knowledgeBaseId(), new KnowledgeBaseStatisticsVO(
                    statistics.totalCount(), statistics.pendingCount(), statistics.processingCount(),
                    statistics.indexedCount(), statistics.failedCount()));
        }
        return statisticsByKnowledgeBaseId;
    }

    private KnowledgeBaseDO getRequiredActiveKnowledgeBaseForUpdate(Long knowledgeBaseId) {
        String tenantId = currentTenantProvider.getRequiredTenantId();
        KnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectActiveByIdForUpdate(knowledgeBaseId, tenantId);
        if (knowledgeBase == null) {
            throw new ClientException("知识库不存在或不属于当前租户，knowledgeBaseId=" + knowledgeBaseId,
                    DocumentErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        return knowledgeBase;
    }

    private long countDocuments(Long knowledgeBaseId) {
        Long count = documentMapper.selectCount(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId));
        return count == null ? 0 : count;
    }

    private boolean isDefaultKnowledgeBase(KnowledgeBaseDO knowledgeBase) {
        return Integer.valueOf(1).equals(knowledgeBase.getIsDefault());
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private String normalizeDescription(String description) {
        return description == null ? null : description.trim();
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private KnowledgeBaseStatisticsVO emptyStatistics() {
        return new KnowledgeBaseStatisticsVO(0, 0, 0, 0, 0);
    }

    private ClientException duplicateNameException(String name, Throwable cause) {
        return new ClientException("知识库名称已存在，name=" + name, cause,
                DocumentErrorCode.KNOWLEDGE_BASE_NAME_CONFLICT);
    }
}

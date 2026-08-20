package com.nexarag.document.converter;

import com.nexarag.document.model.dataobject.KnowledgeBaseDO;
import com.nexarag.document.model.vo.KnowledgeBaseDetailVO;
import com.nexarag.document.model.vo.KnowledgeBaseStatisticsVO;
import com.nexarag.document.model.vo.KnowledgeBaseSummaryVO;

/**
 * 知识库数据对象与展示对象转换器。
 */
public final class KnowledgeBaseConverter {

    private KnowledgeBaseConverter() {
    }

    public static KnowledgeBaseSummaryVO toSummaryVO(KnowledgeBaseDO knowledgeBase,
                                                      KnowledgeBaseStatisticsVO statistics) {
        return new KnowledgeBaseSummaryVO(knowledgeBase.getKnowledgeBaseId(), knowledgeBase.getName(),
                knowledgeBase.getDescription(), knowledgeBase.getIsDefault(), statistics, knowledgeBase.getUpdateTime());
    }

    public static KnowledgeBaseDetailVO toDetailVO(KnowledgeBaseDO knowledgeBase,
                                                    KnowledgeBaseStatisticsVO statistics) {
        return new KnowledgeBaseDetailVO(knowledgeBase.getKnowledgeBaseId(), knowledgeBase.getName(),
                knowledgeBase.getDescription(), knowledgeBase.getIsDefault(), statistics,
                knowledgeBase.getCreateTime(), knowledgeBase.getUpdateTime());
    }
}

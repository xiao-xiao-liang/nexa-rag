package com.nexarag.workflow.util;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.common.exception.ServiceException;
import org.springframework.util.StringUtils;

/**
 * 文档入库 Graph State 读取工具，统一处理类型转换和必填校验。
 */
public final class DocumentIngestionStateUtil {

    private DocumentIngestionStateUtil() {
    }

    /**
     * 从 Graph State 中读取必填 Long 值。
     *
     * @param state Graph 状态
     * @param key   状态键
     * @return Long 值
     */
    public static Long requiredLong(OverAllState state, String key) {
        // 1. 从状态中读取原始值
        Object value = state.value(key).orElse(null);

        // 2. 支持数字类型直接转换
        if (value instanceof Number number) {
            return number.longValue();
        }

        // 3. 支持纯数字字符串转换
        if (value instanceof String text && text.matches("\\d+")) {
            return Long.valueOf(text);
        }

        throw new ServiceException("工作流状态缺少有效字段，key=" + key);
    }

    /**
     * 从 Graph State 中读取必填字符串值。
     *
     * @param state Graph 状态
     * @param key   状态键
     * @return 字符串值
     */
    public static String requiredString(OverAllState state, String key) {
        // 1. 从状态中读取原始值
        Object value = state.value(key).orElse(null);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }

        throw new ServiceException("工作流状态缺少有效字段，key=" + key);
    }
}

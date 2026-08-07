package com.nexarag.document.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * Excel/CSV 切分参数。
 *
 * @param mode            表格渲染模式
 * @param firstRowAsHeader 是否将第一行作为表头
 * @param charset         CSV 字符集，为空时自动识别
 * @param maxRowsPerChunk 每个片段最多包含的数据行数
 */
public record ExcelSplitOptions(ExcelSplitMode mode,
                                Boolean firstRowAsHeader,
                                String charset,
                                @Min(value = 1, message = "每个片段最多行数不能小于1")
                                @Max(value = 10000, message = "每个片段最多行数不能超过10000")
                                Integer maxRowsPerChunk) {
}

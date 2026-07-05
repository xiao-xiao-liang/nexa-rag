package com.nexarag.model.dto;

import java.util.List;

/**
 * 模型连接测试请求。
 *
 * @param input     向量化测试输入
 * @param query     重排序测试查询
 * @param documents 重排序测试文档
 */
public record ModelConnectionTestRequest(String input, String query, List<String> documents) {
}

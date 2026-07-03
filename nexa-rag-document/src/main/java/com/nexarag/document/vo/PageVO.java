package com.nexarag.document.vo;

import lombok.Builder;

import java.util.List;

/**
 * 分页响应对象，用于承载列表数据和分页元信息。
 *
 * @param records 当前页数据
 * @param total   总记录数
 * @param current 当前页码
 * @param size    每页数量
 * @param pages   总页数
 * @param <T>     数据类型
 */
@Builder
public record PageVO<T>(List<T> records, long total, long current, long size, long pages) {
}

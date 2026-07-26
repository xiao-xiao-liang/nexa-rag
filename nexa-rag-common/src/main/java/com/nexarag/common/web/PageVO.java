package com.nexarag.common.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 页码分页响应对象，用于承载列表数据和分页元信息。
 *
 * @param <T> 列表数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {

    /** 当前页数据。 */
    private List<T> records;

    /** 总记录数。 */
    private long total;

    /** 当前页码。 */
    private long current;

    /** 每页数量。 */
    private long size;

    /** 总页数。 */
    private long pages;
}

package com.nexarag.common.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 游标分页响应对象，用于承载按游标向前加载的列表数据。
 *
 * @param <T> 列表数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CursorPageVO<T> {

    /** 当前批次数据。 */
    private List<T> records;

    /** 是否还存在更早的数据。 */
    private boolean hasMore;

    /** 下次请求使用的 sequence 游标。 */
    private Long nextBeforeSequence;
}

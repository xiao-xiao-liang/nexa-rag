package com.nexarag.boot.observability;

import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 模型观测查询条件。
 *
 * @param from         起始时间（含）
 * @param to           结束时间（不含）
 * @param modelName    模型名称筛选
 * @param routeKey     路由键筛选
 * @param status       Langfuse 级别筛选
 * @param limit        返回上限
 */
public record ModelObservabilityQuery(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                      String modelName, String routeKey, String status,
                                      Integer limit) {
}

package com.nexarag.infra.observability.langfuse;

import com.nexarag.infra.observability.langfuse.model.LangfuseGenerationResult;
import io.opentelemetry.context.Context;

/**
 * Langfuse Generation 的生命周期句柄。
 */
public interface LangfuseGenerationScope extends AutoCloseable {

    /**
     * 返回当前 Generation 的 OTel 上下文。
     *
     * @return Generation 上下文
     */
    Context context();

    /**
     * 以成功或取消等终态结束 Generation。
     *
     * @param result 终态指标
     */
    void finish(LangfuseGenerationResult result);

    /**
     * 以异常终止 Generation。
     *
     * @param throwable 调用异常
     */
    void fail(Throwable throwable);

    /**
     * 结束 Generation；重复调用必须安全。
     */
    @Override
    void close();
}

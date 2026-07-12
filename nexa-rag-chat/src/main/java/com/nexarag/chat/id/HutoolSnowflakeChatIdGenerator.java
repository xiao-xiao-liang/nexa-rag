package com.nexarag.chat.id;

import cn.hutool.core.util.IdUtil;
import org.springframework.stereotype.Component;

/**
 * 基于 Hutool 雪花算法生成聊天领域 ID。
 */
@Component
public class HutoolSnowflakeChatIdGenerator implements ChatIdGenerator {

    /**
     * 生成趋势递增的雪花字符串 ID。
     *
     * @return 雪花 ID 字符串
     */
    @Override
    public String nextId() {
        return IdUtil.getSnowflakeNextIdStr();
    }
}

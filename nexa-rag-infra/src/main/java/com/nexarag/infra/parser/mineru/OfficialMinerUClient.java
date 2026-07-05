package com.nexarag.infra.parser.mineru;

import com.nexarag.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 官方 MinerU 客户端边界，预留 API Key 模式的接入点。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.parser.mineru", name = "mode", havingValue = "official")
public class OfficialMinerUClient implements MinerUClient {

    private final MinerUProperties properties;

    /**
     * 调用官方 MinerU 服务。
     *
     * @param command 解析命令
     * @return MinerU ZIP 解析响应
     */
    @Override
    public MinerUParseResponse parse(MinerUParseCommand command) {
        // 1. 校验 API Key，避免未配置时发起无效请求
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new ServiceException("官方MinerU API Key不能为空，documentId=" + command.documentId());
        }

        // 2. 官方接口协议尚未在本批落定，不能伪造成功结果
        throw new ServiceException("官方MinerU接口尚未完成适配，documentId=" + command.documentId());
    }
}

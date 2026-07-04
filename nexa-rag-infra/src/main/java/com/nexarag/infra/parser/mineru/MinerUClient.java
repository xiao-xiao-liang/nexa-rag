package com.nexarag.infra.parser.mineru;

/**
 * MinerU 客户端接口，屏蔽本地服务和官方服务调用差异。
 */
public interface MinerUClient {

    /**
     * 调用 MinerU 解析文件。
     *
     * @param command 解析命令
     * @return 解析响应
     */
    MinerUParseResponse parse(MinerUParseCommand command);
}
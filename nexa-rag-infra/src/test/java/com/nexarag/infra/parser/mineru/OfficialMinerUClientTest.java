package com.nexarag.infra.parser.mineru;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.config.MinerUProperties;
import com.nexarag.infra.enums.MinerUClientMode;
import com.nexarag.infra.parser.mineru.client.OfficialMinerUClient;
import com.nexarag.infra.parser.model.MinerUParseCommand;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 官方 MinerU 客户端测试。
 */
class OfficialMinerUClientTest {

    @Test
    void parseShouldRejectBlankApiKey() {
        MinerUProperties properties = new MinerUProperties();
        properties.setMode(MinerUClientMode.OFFICIAL);
        OfficialMinerUClient client = new OfficialMinerUClient(properties);

        assertThatThrownBy(() -> client.parse(MinerUParseCommand.builder()
                .documentId(1L)
                .fileName("demo.pdf")
                .inputStream(new ByteArrayInputStream("pdf".getBytes(StandardCharsets.UTF_8)))
                .enableOcr(true)
                .build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("API Key");
    }

    @Test
    void parseShouldFailClearlyBeforeOfficialApiIsConfirmed() {
        MinerUProperties properties = new MinerUProperties();
        properties.setMode(MinerUClientMode.OFFICIAL);
        properties.setApiKey("test-api-key");
        OfficialMinerUClient client = new OfficialMinerUClient(properties);

        assertThatThrownBy(() -> client.parse(MinerUParseCommand.builder()
                .documentId(1L)
                .fileName("demo.pdf")
                .inputStream(new ByteArrayInputStream("pdf".getBytes(StandardCharsets.UTF_8)))
                .enableOcr(true)
                .build()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("官方MinerU接口尚未完成适配");
    }
}

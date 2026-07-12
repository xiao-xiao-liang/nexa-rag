package com.nexarag.infra.parser.mineru;

import com.nexarag.infra.config.MinerUProperties;
import com.nexarag.infra.enums.MinerUClientMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MinerU 配置属性测试。
 */
class MinerUPropertiesTest {

    @Test
    void shouldUseLocalMinerUAsDefaultMode() {
        MinerUProperties properties = new MinerUProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMode()).isEqualTo(MinerUClientMode.LOCAL);
        assertThat(properties.getLocalEndpoint()).isEqualTo("http://127.0.0.1:8000");
        assertThat(properties.getLocalParsePath()).isEqualTo("/file_parse");
        assertThat(properties.getOfficialEndpoint()).isEqualTo("https://mineru.net");
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.getConcurrencyLimit()).isEqualTo(5);
        assertThat(properties.getSemaphoreName()).isEqualTo("nexa:mineru:parse");
        assertThat(properties.getMaxWaitSeconds()).isEqualTo(30);
        assertThat(properties.getLeaseSeconds()).isEqualTo(900);
    }
}

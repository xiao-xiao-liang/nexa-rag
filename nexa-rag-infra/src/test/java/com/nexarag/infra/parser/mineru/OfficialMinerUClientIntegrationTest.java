package com.nexarag.infra.parser.mineru;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.infra.config.MinerUProperties;
import com.nexarag.infra.enums.MinerUClientMode;
import com.nexarag.infra.parser.mineru.client.OfficialMinerUClient;
import com.nexarag.infra.parser.mineru.extract.MinerUExtractedResult;
import com.nexarag.infra.parser.mineru.extract.MinerUZipResultExtractor;
import com.nexarag.infra.parser.model.MinerUParseCommand;
import com.nexarag.infra.parser.model.MinerUParseResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MinerU 官方服务真实集成测试，验证签名上传、异步解析和 ZIP 产物下载。
 */
@Tag("integration")
@EnabledIfSystemProperty(named = "nexa.integration.mineru.enabled", matches = "true")
class OfficialMinerUClientIntegrationTest {

    private static final String EXAMPLE_PDF_URL =
            "https://cdn-mineru.openxlab.org.cn/demo/example.pdf";

    @Test
    void parseShouldReturnOfficialZip() throws Exception {
        String apiKey = System.getenv("MINERU_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "未配置 MinerU Token");

        // 1. 下载官方公开示例文件，避免上传用户私有数据
        byte[] pdfBytes = downloadExamplePdf();

        // 2. 调用官方 MinerU 完成真实解析
        MinerUProperties properties = new MinerUProperties();
        properties.setMode(MinerUClientMode.OFFICIAL);
        properties.setApiKey(apiKey);
        properties.setMaxPollCount(300);
        properties.setPollInterval(Duration.ofSeconds(2));
        OfficialMinerUClient client = new OfficialMinerUClient(properties, new ObjectMapper());
        MinerUParseResponse response = client.parse(MinerUParseCommand.builder()
                .documentId(System.currentTimeMillis())
                .fileName("mineru-official-example.pdf")
                .inputStream(new ByteArrayInputStream(pdfBytes))
                .enableOcr(false)
                .build());

        // 3. 校验官方 ZIP 中存在可用 Markdown
        MinerUExtractedResult extractedResult = new MinerUZipResultExtractor()
                .extract(response.zipInputStream());
        assertThat(extractedResult.markdownContent()).isNotBlank();
        assertThat(response.metadata()).containsEntry("clientMode", "official");
    }

    private byte[] downloadExamplePdf() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(EXAMPLE_PDF_URL))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.body()).isNotEmpty();
        return response.body();
    }
}

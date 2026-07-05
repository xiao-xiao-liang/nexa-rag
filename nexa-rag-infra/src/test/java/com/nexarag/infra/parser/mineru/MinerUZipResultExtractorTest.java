package com.nexarag.infra.parser.mineru;

import com.nexarag.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MinerU ZIP 解析结果提取器测试。
 */
class MinerUZipResultExtractorTest {

    @Test
    void extractShouldRejectZipSlipEntry() throws Exception {
        MinerUZipResultExtractor extractor = new MinerUZipResultExtractor();

        assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(zip(Map.of("../evil.txt", "bad")))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("非法路径");
    }

    @Test
    void extractShouldFailWhenMarkdownMissing() throws Exception {
        MinerUZipResultExtractor extractor = new MinerUZipResultExtractor();

        assertThatThrownBy(() -> extractor.extract(new ByteArrayInputStream(zip(Map.of("images/a.png", "fake")))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未找到 Markdown");
    }

    @Test
    void extractShouldReadMarkdownAndAssets() throws Exception {
        MinerUZipResultExtractor extractor = new MinerUZipResultExtractor();

        MinerUExtractedResult result = extractor.extract(new ByteArrayInputStream(zip(Map.of(
                "result/content.md", "# 标题\n![图](images/a.png)",
                "result/images/a.png", "fake-image"
        ))));

        assertThat(result.markdownContent()).contains("# 标题");
        assertThat(result.assetFiles()).hasSize(1);
        assertThat(result.assetFiles().getFirst().relativePath()).isEqualTo("images/a.png");
    }

    private byte[] zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }
}

package com.nexarag.infra.source.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 飞书 Block Markdown 转换器测试。
 */
class FeishuBlockMarkdownConverterTest {

    private final FeishuBlockMarkdownConverter converter = new FeishuBlockMarkdownConverter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requiresFallbackShouldNotFallbackForMediaOrUnknownBlocks() throws Exception {
        List<JsonNode> supportedBlocks = List.of(
                objectMapper.readTree("{\"block_type\":1}"),
                objectMapper.readTree("{\"block_type\":3}"),
                objectMapper.readTree("{\"block_type\":12}"));

        assertThat(converter.requiresFallback(supportedBlocks)).isFalse();
        assertThat(converter.requiresFallback(
                List.of(objectMapper.readTree("{\"block_type\":27}")))).isFalse();
        assertThat(converter.requiresFallback(
                List.of(objectMapper.readTree("{\"block_type\":23}")))).isFalse();
        assertThat(converter.requiresFallback(
                List.of(objectMapper.readTree("{\"block_type\":99}")))).isFalse();
    }

    @Test
    void convertShouldSkipEmptySupportedTextBlock() throws Exception {
        List<JsonNode> blocks = List.of(
                objectMapper.readTree("{\"block_id\":\"root\",\"block_type\":1,\"children\":[\"empty\",\"text\"]}"),
                objectMapper.readTree("{\"block_id\":\"empty\",\"block_type\":2,\"text\":{\"elements\":[]}}"),
                objectMapper.readTree("{\"block_id\":\"text\",\"block_type\":2,\"text\":{\"elements\":[{\"text_run\":{\"content\":\"保留正文\"}}]}}"));

        assertThat(converter.convert(blocks)).isEqualTo("保留正文");
    }

    @Test
    void convertShouldRenderDownloadedImageAsMarkdownAssetReference() throws Exception {
        List<JsonNode> blocks = List.of(
                objectMapper.readTree("{\"block_id\":\"root\",\"block_type\":1,\"children\":[\"title\",\"image\"]}"),
                objectMapper.readTree("{\"block_id\":\"title\",\"block_type\":4,\"heading2\":{\"elements\":[{\"text_run\":{\"content\":\"二级标题\"}}]}}"),
                objectMapper.readTree("{\"block_id\":\"image\",\"block_type\":27}"));

        String markdown = converter.convert(blocks, java.util.Map.of("image",
                FeishuBlockMediaDownloadResultBO.downloaded("image", 27, "assets/image.png",
                        java.nio.file.Path.of("assets/image.png"), "image/png", 1L)));

        assertThat(markdown).isEqualTo("## 二级标题\n\n![飞书图片](assets/image.png)");
    }
}

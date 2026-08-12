package com.nexarag.infra.parser.publish;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Markdown 资源文件地址重写器测试。
 */
class MarkdownAssetFileRewriterTest {

    @TempDir
    Path tempDir;

    @Test
    void rewriteShouldReplaceOnlyMappedMarkdownImageTarget() throws Exception {
        Path input = tempDir.resolve("content.md");
        Path output = tempDir.resolve("rewritten.md");
        Files.writeString(input, "![架构](assets/a.png)\n[链接](assets/a.png)\n![未映射](assets/b.png)");

        new MarkdownAssetFileRewriter().rewrite(input, output,
                Map.of("assets/a.png", "https://storage/parsed/1/assets/a.png"));

        String rewrittenMarkdown = Files.readString(output);
        assertThat(rewrittenMarkdown).contains("![架构](https://storage/parsed/1/assets/a.png)")
                .contains("[链接](assets/a.png)")
                .contains("![未映射](assets/b.png)");
    }
}

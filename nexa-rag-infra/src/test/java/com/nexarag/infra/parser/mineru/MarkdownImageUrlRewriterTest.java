package com.nexarag.infra.parser.mineru;

import com.nexarag.infra.parser.mineru.extract.MarkdownImageUrlRewriter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Markdown 图片地址重写器测试。
 */
class MarkdownImageUrlRewriterTest {

    @Test
    void rewriteShouldReplaceRelativeImageUrl() {
        MarkdownImageUrlRewriter rewriter = new MarkdownImageUrlRewriter();
        String markdown = "![图](images/a.png)";
        Map<String, String> assetUrls = Map.of("images/a.png", "http://127.0.0.1:9000/nexa-rag/parsed/1/assets/a.png");

        String rewritten = rewriter.rewrite(markdown, assetUrls);

        assertThat(rewritten).contains("![图](http://127.0.0.1:9000/nexa-rag/parsed/1/assets/a.png)");
    }

    @Test
    void rewriteShouldKeepUnknownImageUrl() {
        MarkdownImageUrlRewriter rewriter = new MarkdownImageUrlRewriter();

        String rewritten = rewriter.rewrite("![图](images/missing.png)", Map.of());

        assertThat(rewritten).isEqualTo("![图](images/missing.png)");
    }
}

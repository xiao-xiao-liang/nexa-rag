package com.nexarag.document.splitter.markdown;

import com.nexarag.document.toolkit.DocumentChunkIdGenerator;
import com.nexarag.document.toolkit.DocumentSectionIdGenerator;
import com.nexarag.document.splitter.support.TextWindowSplitter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Markdown 切分器 Spring 装配测试。
 */
class MarkdownSplitterSpringContextTest {

    @Test
    void contextShouldCreateBothMarkdownSplitters() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(DocumentSectionIdGenerator.class, DocumentChunkIdGenerator.class, TextWindowSplitter.class,
                    MarkdownHeadingScanner.class, MarkdownSectionStructureBuilder.class,
                    MarkdownParentDocumentSplitter.class, MarkdownBrotherDocumentSplitter.class);

            context.refresh();

            assertThat(context.getBean(MarkdownParentDocumentSplitter.class)).isNotNull();
            assertThat(context.getBean(MarkdownBrotherDocumentSplitter.class)).isNotNull();
        }
    }
}

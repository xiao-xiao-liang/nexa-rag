package com.nexarag.document.service;

import com.nexarag.document.enums.FileType;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.model.bo.split.DocumentSplitContext;
import com.nexarag.infra.storage.service.FileStorageService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 文档切分上下文构建器测试。 */
class DocumentSplitContextBuilderTest {

    @Test
    void buildShouldReadStructureArtifactWithStringSize() {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        when(fileStorageService.load("parsed/content.md"))
                .thenReturn(new ByteArrayInputStream("## 标题".getBytes(StandardCharsets.UTF_8)));
        Document document = Document.builder()
                .documentId(1L)
                .title("测试文档")
                .build();
        DocumentVersionDO documentVersion = DocumentVersionDO.builder()
                .documentId(1L)
                .documentVersionId(2L)
                .originalFileName("测试.pdf")
                .fileType(FileType.PDF)
                .originalObjectName("original/test.pdf")
                .parsedObjectName("parsed/content.md")
                .parsedMetadataJson("""
                        {"structureArtifacts":[{
                          "type":"MINERU_MIDDLE_JSON",
                          "objectKey":"parsed/1/structure/mineru-middle.json",
                          "contentType":"application/json",
                          "size":"624934"
                        }]}""")
                .build();
        DocumentSplitContextBuilder builder = new DocumentSplitContextBuilder(fileStorageService,
                new ProcessConfigDefaults());

        DocumentSplitContext context = builder.build(document, documentVersion);

        assertThat(context.structureArtifacts()).singleElement().satisfies(artifact -> {
            assertThat(artifact.type()).isEqualTo("MINERU_MIDDLE_JSON");
            assertThat(artifact.size()).isEqualTo(624934L);
        });
    }
}

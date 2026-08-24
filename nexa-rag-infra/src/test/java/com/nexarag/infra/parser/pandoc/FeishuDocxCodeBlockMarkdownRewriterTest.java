package com.nexarag.infra.parser.pandoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 飞书 DOCX 代码表格 Markdown 恢复器测试。 */
class FeishuDocxCodeBlockMarkdownRewriterTest {

    @TempDir
    Path tempDir;

    @Test
    void rewriteShouldReplaceFeishuCodeTableWithLanguageFencedCodeAndPreserveIndentation() throws Exception {
        Path docxPath = tempDir.resolve("source.docx");
        Path markdownPath = tempDir.resolve("content.md");
        writeDocx(docxPath, "if enabled:");
        Files.writeString(markdownPath, "说明\n\n"
                + "  -----------------------------------------------------------------------\n"
                + "  Python\\\n"
                + "  if enabled:\\\n"
                + "      return 42\n\n"
                + "  -----------------------------------------------------------------------\n");

        new FeishuDocxCodeBlockMarkdownRewriter().rewrite(docxPath, markdownPath);

        assertThat(Files.readString(markdownPath).replace("\r\n", "\n"))
                .isEqualTo("说明\n\n```python\nif enabled:\n    return 42\n```\n");
    }

    @Test
    void rewriteShouldMatchPandocEscapedCharactersInCodeTable() throws Exception {
        Path docxPath = tempDir.resolve("escaped-source.docx");
        Path markdownPath = tempDir.resolve("escaped-content.md");
        writeDocx(docxPath, "print(\"ok\")");
        Files.writeString(markdownPath, "  -----------------------------------------------------------------------\n"
                + "  Python\\\n"
                + "  print(\\\"ok\\\")\\\n"
                + "      return 42\n\n"
                + "  -----------------------------------------------------------------------\n");

        new FeishuDocxCodeBlockMarkdownRewriter().rewrite(docxPath, markdownPath);

        assertThat(Files.readString(markdownPath).replace("\r\n", "\n"))
                .isEqualTo("```python\nprint(\"ok\")\n    return 42\n```\n");
    }

    private void writeDocx(Path docxPath, String firstCodeLine) throws Exception {
        try (OutputStream outputStream = Files.newOutputStream(docxPath);
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("word/document.xml"));
            zipOutputStream.write(("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body><w:tbl><w:tblPr/><w:tr><w:tc><w:tcPr><w:shd w:fill="f5f6f7"/></w:tcPr>
                        <w:p><w:r><w:t>Python</w:t><w:br/><w:t>%s</w:t><w:br/>
                        <w:t xml:space="preserve">    return 42</w:t></w:r></w:p>
                      </w:tc></w:tr></w:tbl></w:body>
                    </w:document>
                    """.formatted(firstCodeLine)).getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
    }
}

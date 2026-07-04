package com.nexarag.document.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档文件类型解析测试。
 */
class FileTypeTest {

    @Test
    void shouldResolveSupportedFileTypesFromFileName() {
        // 1. 校验常见文档扩展名可以解析为对应文件类型
        assertThat(FileType.fromFileName("demo.pdf")).isEqualTo(FileType.PDF);
        assertThat(FileType.fromFileName("demo.doc")).isEqualTo(FileType.WORD);
        assertThat(FileType.fromFileName("demo.docx")).isEqualTo(FileType.WORD);
        assertThat(FileType.fromFileName("demo.xls")).isEqualTo(FileType.EXCEL);
        assertThat(FileType.fromFileName("demo.xlsx")).isEqualTo(FileType.EXCEL);
        assertThat(FileType.fromFileName("demo.csv")).isEqualTo(FileType.EXCEL);
        assertThat(FileType.fromFileName("demo.ppt")).isEqualTo(FileType.PPT);
        assertThat(FileType.fromFileName("demo.pptx")).isEqualTo(FileType.PPT);
        assertThat(FileType.fromFileName("demo.md")).isEqualTo(FileType.MARKDOWN);
        assertThat(FileType.fromFileName("demo.markdown")).isEqualTo(FileType.MARKDOWN);
        assertThat(FileType.fromFileName("demo.txt")).isEqualTo(FileType.TEXT);
    }

    @Test
    void shouldReturnUnknownWhenFileNameUnsupported() {
        // 1. 校验空文件名、无扩展名和不支持扩展名统一返回未知类型
        assertThat(FileType.fromFileName(null)).isEqualTo(FileType.UNKNOWN);
        assertThat(FileType.fromFileName("README")).isEqualTo(FileType.UNKNOWN);
        assertThat(FileType.fromFileName("demo.zip")).isEqualTo(FileType.UNKNOWN);
    }

    @Test
    void shouldResolveUpperCaseExtension() {
        // 1. 校验大写扩展名会按小写规则解析
        assertThat(FileType.fromFileName("DEMO.PDF")).isEqualTo(FileType.PDF);
    }
}

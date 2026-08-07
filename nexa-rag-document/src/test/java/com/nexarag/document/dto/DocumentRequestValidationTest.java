package com.nexarag.document.model.dto;

import com.nexarag.document.enums.SplitStrategy;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档请求参数校验测试。
 */
class DocumentRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void uploadRequestShouldValidateNestedRegexLength() {
        RegexSplitOptions regex = new RegexSplitOptions(null, "a".repeat(257), false);
        SplitConfigRequest splitConfig = new SplitConfigRequest(
                SplitStrategy.REGEX_TEXT, 1000, 100, null, regex, null);
        UploadDocumentRequest request = new UploadDocumentRequest(null, null, splitConfig, null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("splitConfig.regex.regex");
    }

    @Test
    void processRequestShouldValidateNestedExcelMaxRows() {
        ExcelSplitOptions excel = new ExcelSplitOptions(ExcelSplitMode.KEY_VALUE, true, null, 10001);
        SplitConfigRequest splitConfig = new SplitConfigRequest(
                SplitStrategy.EXCEL, 1000, 100, null, null, excel);
        ProcessDocumentRequest request = new ProcessDocumentRequest(splitConfig, null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("splitConfig.excel.maxRowsPerChunk");
    }
}

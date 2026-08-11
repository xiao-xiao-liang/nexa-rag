package com.nexarag.document.model.dto;

import com.nexarag.infra.enums.ExternalDocumentSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 外部文档统一受理参数。 */
public record ExternalDocumentSubmitDTO(
        @NotNull ExternalDocumentSourceType sourceType,
        @Size(max = 256) String title,
        @Size(max = 1024) String description,
        @NotBlank @Size(max = 1024) String sourceUrl,
        @Valid SplitConfigRequest splitConfig,
        @Valid ParseConfigRequest parseConfig,
        @Valid IndexConfigRequest indexConfig) {
}

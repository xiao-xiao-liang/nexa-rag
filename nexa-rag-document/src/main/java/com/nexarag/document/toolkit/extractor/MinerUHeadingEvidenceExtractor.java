package com.nexarag.document.toolkit.extractor;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.model.bo.structure.HeadingEvidenceBO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** 使用 Jackson 流式 API 从 MinerU 中间 JSON 中提取 title 区块。 */
@Component
public class MinerUHeadingEvidenceExtractor {
    private final JsonFactory jsonFactory = new JsonFactory();

    /** 调用方负责关闭输入流；不将整个 JSON 树读入内存。 */
    public List<HeadingEvidenceBO> extract(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return List.of();
        }
        List<HeadingEvidenceBO> headings = new ArrayList<>();
        try (JsonParser parser = jsonFactory.createParser(inputStream)) {
            Deque<MinerUObject> objects = new ArrayDeque<>();
            int sequence = 0;
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    objects.push(new MinerUObject());
                } else if (parser.currentToken() == JsonToken.FIELD_NAME && !objects.isEmpty()) {
                    objects.peek().fieldName = parser.currentName();
                } else if (parser.currentToken() == JsonToken.END_OBJECT && !objects.isEmpty()) {
                    HeadingEvidenceBO heading = objects.pop().toHeading(++sequence);
                    if (heading != null) {
                        headings.add(heading);
                    }
                } else if (parser.currentToken().isScalarValue() && !objects.isEmpty()) {
                    objects.peek().accept(parser.getValueAsString(), parser.getValueAsInt());
                }
            }
        }
        return List.copyOf(headings);
    }

    private static final class MinerUObject {
        private String fieldName;
        String type = null;
        String text = null;
        Integer level = null;
        Integer pageNumber = null;

        private void accept(String value, int numberValue) {
            if ("type".equals(fieldName)) {
                type = value;
            } else if ("text".equals(fieldName) || "content".equals(fieldName)) {
                text = value;
            } else if ("level".equals(fieldName)) {
                level = numberValue;
            } else if ("page_idx".equals(fieldName) || "page_no".equals(fieldName)) {
                pageNumber = numberValue + 1;
            }
        }

        private HeadingEvidenceBO toHeading(int sequence) {
            if (!"title".equalsIgnoreCase(type) || !StringUtils.hasText(text)) {
                return null;
            }
            return new HeadingEvidenceBO(text.strip(), level == null ? 1 : level, sequence,
                    HeadingEvidenceSource.MINERU, 0.70D, pageNumber);
        }
    }
}

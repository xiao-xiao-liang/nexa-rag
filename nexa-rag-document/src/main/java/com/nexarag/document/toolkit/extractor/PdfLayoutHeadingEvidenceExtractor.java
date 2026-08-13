package com.nexarag.document.toolkit.extractor;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.model.bo.structure.HeadingEvidenceBO;
import com.nexarag.document.model.bo.structure.PdfTitleLayoutBO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 从 MinerU 中间 JSON 中提取标题块的相对字号证据。 */
@Component
public class PdfLayoutHeadingEvidenceExtractor {
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    /** 流式提取 title 块并将较大的相对字号映射为较浅层级。 */
    public List<HeadingEvidenceBO> extract(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return List.of();
        }
        List<PdfTitleLayoutBO> titles = readTitleLayouts(inputStream);
        if (titles.isEmpty()) {
            return List.of();
        }
        Map<Double, Integer> levels = resolveRelativeLevels(titles);
        return titles.stream().filter(title -> title.fontSize() != null)
                .map(title -> new HeadingEvidenceBO(title.title(), levels.get(title.fontSize()), title.sequence(),
                        HeadingEvidenceSource.PDF_LAYOUT, 0.75D, title.pageNumber()))
                .toList();
    }

    private List<PdfTitleLayoutBO> readTitleLayouts(InputStream inputStream) throws IOException {
        List<PdfTitleLayoutBO> titles = new ArrayList<>();
        try (JsonParser parser = JSON_FACTORY.createParser(inputStream)) {
            Deque<TitleObject> objects = new ArrayDeque<>();
            int sequence = 0;
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    objects.push(new TitleObject());
                } else if (parser.currentToken() == JsonToken.FIELD_NAME && !objects.isEmpty()) {
                    objects.peek().fieldName = parser.currentName();
                } else if (parser.currentToken() == JsonToken.END_OBJECT && !objects.isEmpty()) {
                    PdfTitleLayoutBO title = objects.pop().toTitle(++sequence);
                    if (title != null) {
                        titles.add(title);
                    }
                } else if (parser.currentToken().isScalarValue() && !objects.isEmpty()) {
                    objects.peek().accept(parser.getValueAsString(), parser.getValueAsDouble(), parser.getValueAsInt());
                }
            }
        }
        return List.copyOf(titles);
    }

    private Map<Double, Integer> resolveRelativeLevels(List<PdfTitleLayoutBO> titles) {
        List<Double> sizes = titles.stream().map(PdfTitleLayoutBO::fontSize).filter(size -> size != null)
                .distinct().sorted(Comparator.reverseOrder()).toList();
        Map<Double, Integer> levels = new TreeMap<>();
        for (int index = 0; index < sizes.size(); index++) {
            levels.put(sizes.get(index), Math.min(6, index + 1));
        }
        return levels;
    }

    private static final class TitleObject {
        private String fieldName;
        private String type;
        private String text;
        private Double fontSize;
        private Integer pageNumber;

        private void accept(String stringValue, double numberValue, int integerValue) {
            if ("type".equals(fieldName)) {
                type = stringValue;
            } else if ("text".equals(fieldName) || "content".equals(fieldName)) {
                text = stringValue;
            } else if ("font_size".equals(fieldName) || "font_size_avg".equals(fieldName)) {
                fontSize = numberValue;
            } else if ("page_idx".equals(fieldName) || "page_no".equals(fieldName)) {
                pageNumber = integerValue + 1;
            }
        }

        private PdfTitleLayoutBO toTitle(int sequence) {
            if (!"title".equalsIgnoreCase(type) || !StringUtils.hasText(text) || fontSize == null || fontSize <= 0) {
                return null;
            }
            return new PdfTitleLayoutBO(text.strip(), sequence, pageNumber, fontSize);
        }
    }
}

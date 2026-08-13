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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 从官方 MinerU Content List 中提取 PDF 标题的相对版式层级。
 *
 * <p>官方 V2 产物会将标题层级普遍标记为相同值，因此不直接信任其 {@code level}，
 * 而是使用同一文档中标题边界框高度的相对差异恢复层级。V1 仅作为 V2 缺失时的兼容回退。</p>
 */
@Component
public class MinerUContentListHeadingEvidenceExtractor {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final Pattern NUMBERED_TITLE = Pattern.compile("^(\\d{1,3})(?:\\.\\d{1,3})*[、.)）\\s]");
    private static final int SAME_SIZE_BAND_MAX_DELTA = 2;

    /**
     * 从官方 Content List V2 提取标题证据。
     *
     * @param inputStream V2 JSON 输入流
     * @return 相对版式层级标题证据
     * @throws IOException 读取 JSON 失败时抛出
     */
    public List<HeadingEvidenceBO> extractV2(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return List.of();
        }
        List<TitleCandidate> candidates = new ArrayList<>();
        try (JsonParser parser = JSON_FACTORY.createParser(inputStream)) {
            int arrayDepth = 0;
            int pageNumber = 0;
            int sequence = 0;
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.START_ARRAY) {
                    arrayDepth++;
                    if (arrayDepth == 2) {
                        pageNumber++;
                    }
                    continue;
                }
                if (parser.currentToken() == JsonToken.END_ARRAY) {
                    arrayDepth--;
                    continue;
                }
                if (parser.currentToken() == JsonToken.START_OBJECT && arrayDepth == 2) {
                    TitleCandidate candidate = readV2Block(parser, ++sequence, pageNumber);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        }
        return toHeadingEvidences(candidates);
    }

    /**
     * 从旧版 Content List 提取标题证据。
     *
     * @param inputStream V1 JSON 输入流
     * @return 相对版式层级标题证据
     * @throws IOException 读取 JSON 失败时抛出
     */
    public List<HeadingEvidenceBO> extractLegacy(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return List.of();
        }
        List<TitleCandidate> candidates = new ArrayList<>();
        try (JsonParser parser = JSON_FACTORY.createParser(inputStream)) {
            int sequence = 0;
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    TitleCandidate candidate = readLegacyBlock(parser, ++sequence);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        }
        return toHeadingEvidences(candidates);
    }

    /** 读取 V2 单个页面块，调用方已将解析器定位至块起始对象。 */
    private TitleCandidate readV2Block(JsonParser parser, int sequence, int pageNumber) throws IOException {
        String type = null;
        String title = null;
        Integer height = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            if ("type".equals(fieldName) && valueToken.isScalarValue()) {
                type = parser.getValueAsString();
            } else if ("content".equals(fieldName) && valueToken == JsonToken.START_OBJECT) {
                title = readV2TitleContent(parser);
            } else if ("bbox".equals(fieldName) && valueToken == JsonToken.START_ARRAY) {
                height = readBoundingBoxHeight(parser);
            } else {
                parser.skipChildren();
            }
        }
        return toCandidate(type, title, sequence, pageNumber, height);
    }

    /** 读取 V1 单个内容块，调用方已将解析器定位至块起始对象。 */
    private TitleCandidate readLegacyBlock(JsonParser parser, int sequence) throws IOException {
        String type = null;
        String title = null;
        Integer textLevel = null;
        Integer pageNumber = null;
        Integer height = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            if ("type".equals(fieldName) && valueToken.isScalarValue()) {
                type = parser.getValueAsString();
            } else if ("text".equals(fieldName) && valueToken.isScalarValue()) {
                title = parser.getValueAsString();
            } else if ("text_level".equals(fieldName) && valueToken.isNumeric()) {
                textLevel = parser.getIntValue();
            } else if ("page_idx".equals(fieldName) && valueToken.isNumeric()) {
                pageNumber = parser.getIntValue() + 1;
            } else if ("bbox".equals(fieldName) && valueToken == JsonToken.START_ARRAY) {
                height = readBoundingBoxHeight(parser);
            } else {
                parser.skipChildren();
            }
        }
        return textLevel == null ? null : toCandidate(type, title, sequence, pageNumber, height);
    }

    /** 读取 V2 title_content 中各文本片段。 */
    private String readV2TitleContent(JsonParser parser) throws IOException {
        StringBuilder title = new StringBuilder();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            if ("title_content".equals(fieldName) && valueToken == JsonToken.START_ARRAY) {
                readTitleContentItems(parser, title);
            } else {
                parser.skipChildren();
            }
        }
        return title.toString();
    }

    /** 读取 title_content 数组内的文本 span。 */
    private void readTitleContentItems(JsonParser parser, StringBuilder title) throws IOException {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                parser.skipChildren();
                continue;
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                JsonToken valueToken = parser.nextToken();
                if ("content".equals(fieldName) && valueToken.isScalarValue()) {
                    title.append(parser.getValueAsString());
                } else {
                    parser.skipChildren();
                }
            }
        }
    }

    /** 读取 bbox 的 y 轴高度，不保留坐标数组。 */
    private Integer readBoundingBoxHeight(JsonParser parser) throws IOException {
        Integer top = null;
        Integer bottom = null;
        int index = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken().isNumeric()) {
                if (index == 1) {
                    top = parser.getIntValue();
                } else if (index == 3) {
                    bottom = parser.getIntValue();
                }
                index++;
            }
        }
        return top != null && bottom != null && bottom > top ? bottom - top : null;
    }

    /** 将标题块转为候选，过滤正文型误识别标题。 */
    private TitleCandidate toCandidate(String type, String title, int sequence, Integer pageNumber, Integer height) {
        if (!("title".equalsIgnoreCase(type) || "text".equalsIgnoreCase(type))
                || !StringUtils.hasText(title) || height == null || height <= 0) {
            return null;
        }
        String normalizedTitle = title.strip();
        if (normalizedTitle.startsWith("•") || normalizedTitle.startsWith("·")) {
            return null;
        }
        return new TitleCandidate(normalizedTitle, sequence, pageNumber, height);
    }

    /** 按同文档标题框高度形成字号带，并结合连续编号标题保留父子关系。 */
    private List<HeadingEvidenceBO> toHeadingEvidences(List<TitleCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Integer, Integer> levelsByHeight = resolveLevelsByHeight(candidates);
        Map<Integer, Integer> numberedLevels = new HashMap<>();
        List<HeadingEvidenceBO> results = new ArrayList<>();
        int previousNonNumberedBaseLevel = 0;
        int previousNonNumberedResolvedLevel = 0;
        Integer previousNumberedOrder = null;
        int previousNumberedLevel = 0;
        for (TitleCandidate candidate : candidates) {
            int baseLevel = levelsByHeight.get(candidate.height());
            Integer numberedOrder = resolveNumberedOrder(candidate.title());
            boolean numbered = numberedOrder != null;
            int level = baseLevel;
            if (numbered && previousNumberedOrder != null && numberedOrder == previousNumberedOrder + 1) {
                level = previousNumberedLevel;
            } else if (numbered && numberedLevels.containsKey(baseLevel)) {
                level = numberedLevels.get(baseLevel);
            } else if (numbered && previousNonNumberedBaseLevel == baseLevel) {
                level = Math.min(6, previousNonNumberedResolvedLevel + 1);
            }
            if (numbered) {
                numberedLevels.put(baseLevel, level);
                previousNumberedOrder = numberedOrder;
                previousNumberedLevel = level;
            } else {
                previousNonNumberedBaseLevel = baseLevel;
                previousNonNumberedResolvedLevel = level;
                previousNumberedOrder = null;
            }
            results.add(new HeadingEvidenceBO(candidate.title(), level, candidate.sequence(),
                    HeadingEvidenceSource.PDF_LAYOUT, 0.82D, candidate.pageNumber()));
        }
        return List.copyOf(results);
    }

    /** 返回编号标题的首级数字；非阿拉伯编号标题返回空。 */
    private Integer resolveNumberedOrder(String title) {
        java.util.regex.Matcher matcher = NUMBERED_TITLE.matcher(title);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /** 以最多两像素的差异聚合同一标题字号带。 */
    private Map<Integer, Integer> resolveLevelsByHeight(List<TitleCandidate> candidates) {
        List<Integer> heights = candidates.stream().map(TitleCandidate::height).distinct()
                .sorted(Comparator.reverseOrder()).toList();
        Map<Integer, Integer> levels = new HashMap<>();
        int level = 0;
        int bandStartHeight = Integer.MIN_VALUE;
        for (Integer height : heights) {
            if (bandStartHeight == Integer.MIN_VALUE || bandStartHeight - height > SAME_SIZE_BAND_MAX_DELTA) {
                level = Math.min(6, level + 1);
                bandStartHeight = height;
            }
            levels.put(height, level);
        }
        return levels;
    }

    /** 内容列表中一个已识别标题块的最小结构信息。 */
    private record TitleCandidate(String title, int sequence, Integer pageNumber, int height) {
    }
}

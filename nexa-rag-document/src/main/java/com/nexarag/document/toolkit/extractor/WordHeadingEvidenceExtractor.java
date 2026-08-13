package com.nexarag.document.toolkit.extractor;

import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.model.bo.structure.HeadingEvidenceBO;
import com.nexarag.document.toolkit.resolver.HeadingNumberingParser;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从 DOCX 样式、编号和保守格式特征中提取标题候选。 */
@Component
public class WordHeadingEvidenceExtractor {
    private static final Pattern HEADING_STYLE = Pattern.compile("(?:heading|标题)\\s*([1-6])", Pattern.CASE_INSENSITIVE);
    private static final int HEURISTIC_TITLE_MAX_LENGTH = 80;

    private final HeadingNumberingParser numberingParser;

    public WordHeadingEvidenceExtractor(HeadingNumberingParser numberingParser) {
        this.numberingParser = numberingParser;
    }

    /** 流式读取 DOCX，调用方负责关闭输入流。 */
    public List<HeadingEvidenceBO> extract(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return List.of();
        }
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<HeadingEvidenceBO> evidences = new ArrayList<>();
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (int index = 0; index < paragraphs.size(); index++) {
                XWPFParagraph paragraph = paragraphs.get(index);
                String text = paragraph.getText() == null ? "" : paragraph.getText().strip();
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                HeadingEvidenceBO evidence = toEvidence(paragraph, text, index + 1);
                if (evidence != null) {
                    evidences.add(evidence);
                }
            }
            return List.copyOf(evidences);
        }
    }

    private HeadingEvidenceBO toEvidence(XWPFParagraph paragraph, String text, int sequence) {
        OptionalInt outlineLevel = resolveOutlineLevel(paragraph);
        if (outlineLevel.isPresent()) {
            return new HeadingEvidenceBO(text, outlineLevel.getAsInt(), sequence,
                    HeadingEvidenceSource.WORD_OUTLINE, 1.0D, null);
        }
        Matcher styleMatcher = HEADING_STYLE.matcher(resolveStyle(paragraph));
        if (styleMatcher.find()) {
            return new HeadingEvidenceBO(text, Integer.parseInt(styleMatcher.group(1)), sequence,
                    HeadingEvidenceSource.WORD_STYLE, 1.0D, null);
        }
        OptionalInt numberingLevel = numberingParser.parseLevel(text);
        if (numberingLevel.isPresent() && paragraph.getNumID() != null) {
            return new HeadingEvidenceBO(text, numberingLevel.getAsInt(), sequence,
                    HeadingEvidenceSource.NUMBERING, 0.95D, null);
        }
        if (numberingLevel.isPresent() && isConservativeBoldTitle(paragraph, text)) {
            return new HeadingEvidenceBO(text, numberingLevel.getAsInt(), sequence,
                    HeadingEvidenceSource.HEURISTIC, 0.80D, null);
        }
        return null;
    }

    /**
     * 读取 DOCX 标准大纲层级。
     *
     * <p>飞书导出的 Word 可能使用无语义名称的数字样式 ID，但仍会保留
     * {@code w:outlineLvl}。该值是从 0 开始的层级，转换后限制为 1 至 6 级。</p>
     *
     * @param paragraph Word 段落
     * @return 可用的大纲标题层级；不存在或越界时返回空
     */
    private OptionalInt resolveOutlineLevel(XWPFParagraph paragraph) {
        if (!paragraph.getCTP().isSetPPr() || !paragraph.getCTP().getPPr().isSetOutlineLvl()) {
            return OptionalInt.empty();
        }
        BigInteger value = paragraph.getCTP().getPPr().getOutlineLvl().getVal();
        if (value == null || value.signum() < 0 || value.compareTo(BigInteger.valueOf(5)) > 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(value.intValue() + 1);
    }

    private String resolveStyle(XWPFParagraph paragraph) {
        return paragraph.getStyle() == null ? "" : paragraph.getStyle().toLowerCase(Locale.ROOT);
    }

    private boolean isConservativeBoldTitle(XWPFParagraph paragraph, String text) {
        List<XWPFRun> runs = paragraph.getRuns();
        return text.length() <= HEURISTIC_TITLE_MAX_LENGTH && !text.matches(".*[。！？；：]$")
                && !runs.isEmpty() && runs.stream().allMatch(XWPFRun::isBold);
    }
}

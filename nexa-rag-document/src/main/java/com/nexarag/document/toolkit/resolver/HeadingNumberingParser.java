package com.nexarag.document.toolkit.resolver;

import org.springframework.stereotype.Component;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 识别常见中文和阿拉伯数字标题编号。 */
@Component
public class HeadingNumberingParser {

    private static final Pattern ARABIC = Pattern.compile("^(\\d{1,3})(?:\\.(\\d{1,3})){0,5}[、.)）\\s]");
    private static final Pattern CHINESE_SECTION = Pattern.compile("^[一二三四五六七八九十百千万]+、");
    private static final Pattern CHINESE_PARENTHESIS = Pattern.compile("^[（(][一二三四五六七八九十百千万]+[)）]");
    private static final Pattern CHAPTER = Pattern.compile("^第[一二三四五六七八九十百千万0-9]+[章节篇]");

    /** 返回编号隐含的标题层级；非标题编号返回空。 */
    public OptionalInt parseLevel(String title) {
        if (title == null) {
            return OptionalInt.empty();
        }
        Matcher matcher = ARABIC.matcher(title.strip());
        if (matcher.find()) {
            String number = matcher.group(0).replaceAll("[^0-9.]", "");
            return OptionalInt.of(Math.min(6, (int) number.chars().filter(character -> character == '.').count() + 1));
        }
        String normalized = title.strip();
        if (CHAPTER.matcher(normalized).find() || CHINESE_SECTION.matcher(normalized).find()) {
            return OptionalInt.of(1);
        }
        if (CHINESE_PARENTHESIS.matcher(normalized).find()) {
            return OptionalInt.of(2);
        }
        return OptionalInt.empty();
    }
}

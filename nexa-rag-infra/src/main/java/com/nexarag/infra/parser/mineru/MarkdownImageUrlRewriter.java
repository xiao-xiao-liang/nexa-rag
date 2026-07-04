package com.nexarag.infra.parser.mineru;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 图片地址重写器，负责把 MinerU 产物中的相对图片地址替换为对象存储地址。
 */
@Component
public class MarkdownImageUrlRewriter {

    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)");

    /**
     * 重写 Markdown 图片地址。
     *
     * @param markdown Markdown 内容
     * @param assetUrls 资源相对路径和访问地址映射
     * @return 重写后的 Markdown 内容
     */
    public String rewrite(String markdown, Map<String, String> assetUrls) {
        if (!StringUtils.hasText(markdown) || assetUrls == null || assetUrls.isEmpty()) {
            return markdown;
        }
        Matcher matcher = IMAGE_PATTERN.matcher(markdown);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String imageUrl = matcher.group(2);
            String replacementUrl = assetUrls.get(imageUrl);
            if (!StringUtils.hasText(replacementUrl)) {
                matcher.appendReplacement(builder, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(builder,
                    Matcher.quoteReplacement("![" + matcher.group(1) + "](" + replacementUrl + ")"));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }
}
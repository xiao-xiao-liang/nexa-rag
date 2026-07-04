package com.nexarag.common.logging;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感日志脱敏工具，用于在写入日志前遮蔽密码、令牌和密钥等敏感字段。
 */
public final class SensitiveLogSanitizer {

    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(apiKey|api-key|api_key|password|passwd|pwd|token|accessToken|access-token|access_token)(\\s*[=:]\\s*)\\S+"),
            Pattern.compile("(?i)\\b(authorization)(\\s*[=:]\\s*)\\S+(?:\\s+\\S+)?")
    );

    private SensitiveLogSanitizer() {
    }

    /**
     * 对日志文本中的常见敏感字段进行脱敏。
     *
     * @param rawText 原始日志文本
     * @return 脱敏后的日志文本
     */
    public static String sanitize(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return rawText;
        }

        String sanitizedText = rawText;
        for (Pattern pattern : SECRET_PATTERNS) {
            // 1. 保留字段名和分隔符，只遮蔽敏感值
            sanitizedText = pattern.matcher(sanitizedText).replaceAll(matchResult ->
                    matchResult.group(1) + matchResult.group(2) + "******");
        }
        return sanitizedText;
    }
}

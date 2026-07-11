package com.nexarag.document.splitter.text;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.error.DocumentErrorCode;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 正则表达式安全校验器，负责限制正则长度并拒绝常见高风险嵌套量词。
 */
@Component
public class RegexSafetyValidator {

    private static final int MAX_REGEX_LENGTH = 256;
    private static final Pattern NESTED_QUANTIFIER_PATTERN =
            Pattern.compile("\\([^)]*[+*][^)]*\\)[+*?]");

    /**
     * 校验并编译自定义正则表达式。
     *
     * @param regex 自定义正则表达式
     * @return 编译后的正则对象
     */
    public Pattern validateAndCompile(String regex) {
        if (regex.length() > MAX_REGEX_LENGTH) {
            throw new ServiceException("正则表达式不能超过256个字符",
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
        if (NESTED_QUANTIFIER_PATTERN.matcher(regex).find()) {
            throw new ServiceException("正则表达式不能包含高风险嵌套量词",
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException exception) {
            throw new ServiceException("正则表达式语法不合法", exception,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }

    /**
     * 校验自定义正则表达式。
     *
     * @param regex 自定义正则表达式
     */
    public void validate(String regex) {
        validateAndCompile(regex);
    }
}

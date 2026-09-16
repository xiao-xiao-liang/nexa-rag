package com.nexarag.infra.observability.langfuse.aop;

import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.nexarag.infra.observability.langfuse.constants.LangfuseExpressionConstant.*;

/**
 * 解析 Langfuse 注解中受限的 SpEL 表达式。
 */
public class LangfuseExpressionResolver {

    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            NEXA_ATTRIBUTE_PREFIX,
            GEN_AI_ATTRIBUTE_PREFIX,
            LANGFUSE_ATTRIBUTE_PREFIX
    );

    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * 解析单个 SpEL 表达式。
     *
     * @param expression 表达式
     * @param method     被拦截方法
     * @param arguments  调用参数
     * @param target     目标对象
     * @return 受支持的值，解析失败或不支持时返回 null
     */
    public Object resolve(String expression, Method method, Object[] arguments, Object target) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        try {
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(target, method, arguments,
                    parameterNameDiscoverer);
            Object value = expressionParser.parseExpression(expression).getValue(context);
            return supportedValue(value) ? value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 解析并校验“键=SpEL”属性集合。
     *
     * @param expressions 注解属性声明
     * @param method      被拦截方法
     * @param arguments   调用参数
     * @param target      目标对象
     * @return 已通过键空间和值类型校验的属性
     */
    public Map<String, Object> resolveAttributes(String[] expressions, Method method, Object[] arguments,
                                                 Object target) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (expressions == null) {
            return Map.of();
        }
        for (String expression : expressions) {
            int separator = expression == null ? -1 : expression.indexOf('=');
            if (separator <= 0 || separator == expression.length() - 1) {
                continue;
            }
            String key = expression.substring(0, separator).trim();
            Object value = resolve(expression.substring(separator + 1).trim(), method, arguments, target);
            if (allowedKey(key) && value != null) {
                attributes.put(key, value);
            }
        }
        return Map.copyOf(attributes);
    }

    /**
     * 解析以 result 为根变量的返回值属性。
     *
     * @param expressions 属性表达式
     * @param result      方法返回值
     * @return 通过键和值校验的属性
     */
    public Map<String, Object> resolveResultAttributes(String[] expressions, Object result) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (expressions == null) {
            return Map.of();
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("result", result);
        for (String expression : expressions) {
            int separator = expression == null ? -1 : expression.indexOf('=');
            if (separator <= 0 || separator == expression.length() - 1) {
                continue;
            }
            String key = expression.substring(0, separator).trim();
            try {
                Object value = expressionParser.parseExpression(expression.substring(separator + 1).trim())
                        .getValue(context);
                if (allowedKey(key) && supportedValue(value)) {
                    attributes.put(key, value);
                }
            } catch (RuntimeException ignored) {
                // 非法结果表达式不影响业务方法。
            }
        }
        return Map.copyOf(attributes);
    }

    private boolean allowedKey(String key) {
        return ALLOWED_PREFIXES.stream().anyMatch(key::startsWith)
                && !key.equals(INPUT_FIELD) && !key.equals(OUTPUT_FIELD) && !key.contains(PROMPT_FIELD);
    }

    private boolean supportedValue(Object value) {
        return value instanceof String || value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double;
    }
}

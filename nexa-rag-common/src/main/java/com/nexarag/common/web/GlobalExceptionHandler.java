package com.nexarag.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.AbstractException;
import com.nexarag.common.exception.ClientException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 全局异常处理器，用于拦截应用异常并返回统一响应结果。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 处理参数校验异常。
     *
     * @param request   HTTP 请求
     * @param exception 参数校验异常
     * @return 统一失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(HttpServletRequest request, MethodArgumentNotValidException exception) {
        String message = Optional.ofNullable(exception.getBindingResult().getFieldError())
                .map(FieldError::getDefaultMessage)
                .orElse(BaseErrorCode.PARAM_ERROR.message());
        log.error("[{}] {} 参数校验失败，message={}", request.getMethod(), getUrl(request), message);
        return Results.failure(BaseErrorCode.PARAM_ERROR.code(), message);
    }

    /**
     * 处理请求参数缺失或类型转换失败异常。
     *
     * @param request   HTTP 请求
     * @param exception 参数绑定异常
     * @return 统一参数错误响应
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public Result<Void> handleRequestParameterException(HttpServletRequest request, Exception exception) {
        log.error("[{}] {} 请求参数绑定失败，message={}", request.getMethod(), getUrl(request), exception.getMessage());
        return Results.failure(BaseErrorCode.PARAM_ERROR.code(), BaseErrorCode.PARAM_ERROR.message());
    }

    /**
     * 处理 JSON 结构、枚举或字段类型无法解析的请求体，避免落入通用 500。
     *
     * @param request HTTP 请求
     * @param exception 请求体解析异常
     * @return 统一参数错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadableRequestBody(HttpServletRequest request, HttpMessageNotReadableException exception) {
        log.warn("[{}] {} 请求体解析失败，message={}", request.getMethod(), getUrl(request), exception.getMessage());
        return Results.failure(BaseErrorCode.PARAM_ERROR.code(), BaseErrorCode.PARAM_ERROR.message());
    }

    /**
     * 处理应用内主动抛出的异常。
     *
     * @param request   HTTP 请求
     * @param response  HTTP 响应
     * @param exception 应用异常
     * @return 统一失败响应
     */
    @ExceptionHandler(AbstractException.class)
    public Result<Void> handleAbstractException(HttpServletRequest request, HttpServletResponse response,
                                                AbstractException exception) {
        if (exception instanceof ClientException clientException && clientException.getHttpStatus() != null) {
            response.setStatus(clientException.getHttpStatus().value());
        }
        if (exception.getCause() != null) {
            log.error("[{}] {} 应用异常，code={}，message={}", request.getMethod(), getUrl(request),
                    exception.getErrorCode(), exception.getErrorMessage(), exception.getCause());
            return Results.failure(exception);
        }
        log.error("[{}] {} 应用异常，code={}，message={}", request.getMethod(), getUrl(request),
                exception.getErrorCode(), exception.getErrorMessage());
        return Results.failure(exception);
    }

    /**
     * 处理媒体协商不可接受异常（如客户端指定仅接收 text/event-stream，但异常需以 JSON 结构输出），直接通过 Response 输出 JSON 避免容器级 500。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param exception 媒体协商异常
     * @throws IOException 响应写入异常
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public void handleHttpMediaTypeNotAcceptableException(HttpServletRequest request, HttpServletResponse response,
                                                           HttpMediaTypeNotAcceptableException exception) throws IOException {
        log.warn("[{}] {} 媒体协商不可接受，直接输出 JSON 错误响应: {}", request.getMethod(), getUrl(request), exception.getMessage());
        if (!response.isCommitted()) {
            int currentStatus = response.getStatus();
            if (currentStatus < HttpServletResponse.SC_BAD_REQUEST) {
                response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
            }
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String json = objectMapper.writeValueAsString(Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "请求指定的媒体类型不受支持"));
            response.getWriter().write(json);
        }
    }

    /**
     * 处理未捕获异常。
     *
     * @param request   HTTP 请求
     * @param throwable 未捕获异常
     * @return 统一失败响应
     */
    @ExceptionHandler(Throwable.class)
    public Result<Void> handleThrowable(HttpServletRequest request, Throwable throwable) {
        log.error("[{}] {} 未处理异常", request.getMethod(), getUrl(request), throwable);
        return Results.failure();
    }

    /**
     * 处理 HTTP 输出异常，避免 SSE 客户端断连后继续写入统一 JSON 响应。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param exception IO 异常
     */
    @ExceptionHandler(IOException.class)
    public void handleIOException(HttpServletRequest request, HttpServletResponse response, IOException exception) {
        if (shouldIgnoreSseClientDisconnect(request, response)) {
            log.info("SSE 客户端连接已断开，停止写入响应，uri={}", getUrl(request));
            return;
        }
        log.error("[{}] {} 响应写入失败", request.getMethod(), getUrl(request), exception);
    }

    /**
     * 判断是否应忽略已提交 SSE 响应的客户端断连异常。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @return 是否应忽略
     */
    boolean shouldIgnoreSseClientDisconnect(HttpServletRequest request, HttpServletResponse response) {
        String contentType = response.getContentType();
        return response.isCommitted()
                && contentType != null
                && contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    /**
     * 获取完整请求地址。
     *
     * @param request HTTP 请求
     * @return 完整请求地址
     */
    private String getUrl(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL() + "?" + queryString;
    }
}

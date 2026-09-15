package com.nexarag.model.client.vllm;

import static com.nexarag.model.constants.VllmApiConstant.CONTENT_SEPARATOR;
import static com.nexarag.model.constants.VllmApiConstant.OPENAI_VERSION_PATH;
import static com.nexarag.model.constants.VllmApiConstant.SHA_256_ALGORITHM;
import static com.nexarag.model.constants.VllmApiConstant.TOKENIZE_PATH;

import com.nexarag.infra.observability.langfuse.config.LangfuseProperties;
import com.nexarag.model.execution.telemetry.RagTokenBreakdown;
import com.nexarag.model.config.ModelProfileProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 调用同一 vLLM 服务 {@code /tokenize} 端点的受控客户端。
 *
 * <p>该客户端不携带模型 API Key，不记录原始文本，并通过进程内缓存和信号量避免统计请求挤占回答请求。</p>
 */
@Component
@Slf4j
public class VllmTokenizerClient {

    private final WebClient webClient;
    private final LangfuseProperties properties;
    private final Semaphore concurrencyLimiter;
    private final Map<String, Integer> tokenCache = new LinkedHashMap<>(16, 0.75F, true);
    private final ExecutorService permitWaitExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Scheduler permitWaitScheduler = Schedulers.fromExecutor(permitWaitExecutor);

    public VllmTokenizerClient(WebClient.Builder webClientBuilder, LangfuseProperties properties) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
        this.concurrencyLimiter = new Semaphore(properties.getTokenizer().getMaxConcurrency());
    }

    /**
     * 统计文本的精确 Token 数。
     *
     * <p>Tokenizer 不可用、超时或返回异常时返回空 Mono，不会中断主聊天流。</p>
     *
     * @param profile 当前模型 Profile
     * @param content 待统计文本
     * @return 精确 Token 数，无法取得时为空
     */
    public Mono<Integer> countTokens(ModelProfileProperties profile, String content) {
        // 1. 跳过关闭的观测、空文本和不完整的 vLLM Profile。
        if (!properties.isEnabled() || !properties.getTokenizer().isEnabled() || !StringUtils.hasText(content)
                || profile == null || !StringUtils.hasText(profile.getBaseUrl())
                || !StringUtils.hasText(profile.getModelName())) {
            return Mono.justOrEmpty(StringUtils.hasText(content) ? null : 0);
        }

        // 2. 先读取仅包含模型端点和文本摘要的本地缓存。
        String cacheKey = cacheKey(profile, content);
        Integer cachedTokens = readCache(cacheKey);
        if (cachedTokens != null) {
            return Mono.just(cachedTokens);
        }

        // 3. 在独立限流队列中调用根路径 /tokenize，失败时降级为空结果。
        return Mono.usingWhen(
                        Mono.fromCallable(() -> {
                                    return concurrencyLimiter.tryAcquire(properties.getTokenizer().getAcquireTimeoutMs(),
                                            TimeUnit.MILLISECONDS);
                                })
                                .subscribeOn(permitWaitScheduler)
                                .filter(Boolean::booleanValue),
                        ignored -> requestTokenCount(profile, content)
                                .timeout(Duration.ofMillis(properties.getTokenizer().getTimeoutMs()))
                                .doOnNext(tokens -> writeCache(cacheKey, tokens)),
                        ignored -> Mono.fromRunnable(concurrencyLimiter::release),
                        (ignored, error) -> Mono.fromRunnable(concurrencyLimiter::release),
                        ignored -> Mono.fromRunnable(concurrencyLimiter::release))
                .onErrorResume(exception -> {
                    log.warn("vLLM Tokenizer 调用失败，modelName={}，异常类型={}", profile.getModelName(),
                            exception.getClass().getSimpleName());
                    return Mono.empty();
                });
    }

    @PreDestroy
    void shutdownPermitWaitExecutor() {
        permitWaitScheduler.dispose();
        permitWaitExecutor.close();
    }

    /**
     * 并行统计最终回答的全部语义分段。
     *
     * @param profile   实际被路由选中的模型 Profile
     * @param breakdown 最终回答的原始语义分段
     * @return 已补齐精确 Token 的新快照，失败分段保留空值
     */
    public Mono<RagTokenBreakdown> countBreakdown(ModelProfileProperties profile, RagTokenBreakdown breakdown) {
        if (breakdown == null) {
            return Mono.empty();
        }
        return Mono.zip(
                        optionalCount(profile, breakdown.systemContent()),
                        optionalCount(profile, breakdown.summaryContent()),
                        optionalCount(profile, historyContent(breakdown.historyMessages())),
                        optionalCount(profile, breakdown.questionContent()),
                        optionalCount(profile, breakdown.retrievalContent()),
                        optionalCount(profile, breakdown.toolContent()))
                .map(tokens -> breakdown.withExactTokenCounts(
                        tokens.getT1().orElse(null), tokens.getT2().orElse(null), tokens.getT3().orElse(null),
                        tokens.getT4().orElse(null), tokens.getT5().orElse(null), tokens.getT6().orElse(null)));
    }

    private Mono<Integer> requestTokenCount(ModelProfileProperties profile, String content) {
        return webClient.post()
                .uri(tokenizerUri(profile.getBaseUrl()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new VllmTokenizerRequestDTO(profile.getModelName(), content))
                .retrieve()
                .bodyToMono(VllmTokenizerResponseDTO.class)
                .map(VllmTokenizerResponseDTO::count)
                .filter(tokens -> tokens != null && tokens >= 0);
    }

    private Mono<Optional<Integer>> optionalCount(ModelProfileProperties profile, String content) {
        return countTokens(profile, content).map(Optional::of).defaultIfEmpty(Optional.empty());
    }

    private String historyContent(java.util.List<com.nexarag.model.gateway.chat.ChatModelMessage> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return "";
        }
        return historyMessages.stream()
                .map(message -> message.content() == null ? "" : message.content())
                .collect(java.util.stream.Collectors.joining(CONTENT_SEPARATOR));
    }

    private URI tokenizerUri(String baseUrl) {
        URI baseUri = URI.create(baseUrl);
        String path = StringUtils.trimTrailingCharacter(baseUri.getPath() == null ? "" : baseUri.getPath(), '/');
        if (path.endsWith(OPENAI_VERSION_PATH)) {
            path = path.substring(0, path.length() - OPENAI_VERSION_PATH.length());
        }
        return URI.create(baseUri.getScheme() + "://" + baseUri.getAuthority() + path + TOKENIZE_PATH);
    }

    private String cacheKey(ModelProfileProperties profile, String content) {
        return profile.getBaseUrl() + CONTENT_SEPARATOR + profile.getModelName() + CONTENT_SEPARATOR + sha256(content);
    }

    private String sha256(String content) {
        try {
            byte[] hash = MessageDigest.getInstance(SHA_256_ALGORITHM).digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private Integer readCache(String key) {
        synchronized (tokenCache) {
            return tokenCache.get(key);
        }
    }

    private void writeCache(String key, Integer tokens) {
        synchronized (tokenCache) {
            tokenCache.put(key, tokens);
            while (tokenCache.size() > properties.getTokenizer().getCacheMaximumSize()) {
                tokenCache.remove(tokenCache.keySet().iterator().next());
            }
        }
    }
}

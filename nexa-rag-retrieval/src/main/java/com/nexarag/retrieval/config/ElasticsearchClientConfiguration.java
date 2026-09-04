package com.nexarag.retrieval.config;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchProperties;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * Elasticsearch 客户端长连接保活、认证凭证与 SSL 兼容配置。
 */
@Configuration
@ConditionalOnClass(RestClientBuilderCustomizer.class)
@ConditionalOnProperty(prefix = "nexa.retrieval.keyword", name = "type", havingValue = "elasticsearch")
public class ElasticsearchClientConfiguration {

    @Bean
    public RestClientBuilderCustomizer elasticsearchRestClientBuilderCustomizer(ElasticsearchProperties elasticsearchProperties) {
        return builder -> {
            builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                    .setConnectTimeout(5000)
                    .setSocketTimeout(30000));

            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                // 1. 设置 IOReactor 连接保活与超时配置
                IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                        .setSoKeepAlive(true)
                        .setSoTimeout(30000)
                        .setConnectTimeout(5000)
                        .build();
                httpClientBuilder.setDefaultIOReactorConfig(ioReactorConfig);

                // 2. 配置主动保活策略：连接最大保持活跃 30 秒，防止复用已被对端断开的死连接
                ConnectionKeepAliveStrategy keepAliveStrategy = (response, context) -> TimeUnit.SECONDS.toMillis(30);
                httpClientBuilder.setKeepAliveStrategy(keepAliveStrategy);

                // 3. 配置自签名 HTTPS / SSL 兼容上下文
                try {
                    SSLContext sslContext = SSLContextBuilder.create()
                            .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                            .build();
                    httpClientBuilder.setSSLContext(sslContext);
                    httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
                    // 降级使用系统默认 SSLContext
                }

                // 4. 配置 Basic Auth 认证凭据，避免因覆盖 setHttpClientConfigCallback 导致 Spring Boot 凭据丢失
                if (StringUtils.hasText(elasticsearchProperties.getUsername())) {
                    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials(AuthScope.ANY,
                            new UsernamePasswordCredentials(elasticsearchProperties.getUsername(), elasticsearchProperties.getPassword()));
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                }

                return httpClientBuilder;
            });
        };
    }
}

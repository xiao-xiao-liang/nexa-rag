package com.nexarag.retrieval.config;

import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchProperties;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ElasticsearchClientConfigurationTest {

    @Test
    void customizerShouldConfigureHttpClientAndRequestConfigCallbacksWithCredentials() {
        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setUsername("elastic");
        properties.setPassword("123456");

        ElasticsearchClientConfiguration configuration = new ElasticsearchClientConfiguration();
        RestClientBuilderCustomizer customizer = configuration.elasticsearchRestClientBuilderCustomizer(properties);
        assertThat(customizer).isNotNull();

        RestClientBuilder builder = mock(RestClientBuilder.class);
        customizer.customize(builder);

        ArgumentCaptor<RestClientBuilder.HttpClientConfigCallback> httpClientCaptor =
                ArgumentCaptor.forClass(RestClientBuilder.HttpClientConfigCallback.class);
        verify(builder).setHttpClientConfigCallback(httpClientCaptor.capture());
        verify(builder).setRequestConfigCallback(any());

        HttpAsyncClientBuilder httpAsyncClientBuilder = mock(HttpAsyncClientBuilder.class);
        HttpAsyncClientBuilder result = httpClientCaptor.getValue().customizeHttpClient(httpAsyncClientBuilder);
        assertThat(result).isSameAs(httpAsyncClientBuilder);
        verify(httpAsyncClientBuilder).setDefaultCredentialsProvider(any());
        verify(httpAsyncClientBuilder).setDefaultIOReactorConfig(any());
        verify(httpAsyncClientBuilder).setKeepAliveStrategy(any());
        verify(httpAsyncClientBuilder).setSSLContext(any());
        verify(httpAsyncClientBuilder).setSSLHostnameVerifier(any());
    }

    @Test
    void customizerShouldNotSetCredentialsWhenUsernameIsBlank() {
        ElasticsearchProperties properties = new ElasticsearchProperties();

        ElasticsearchClientConfiguration configuration = new ElasticsearchClientConfiguration();
        RestClientBuilderCustomizer customizer = configuration.elasticsearchRestClientBuilderCustomizer(properties);

        RestClientBuilder builder = mock(RestClientBuilder.class);
        customizer.customize(builder);

        ArgumentCaptor<RestClientBuilder.HttpClientConfigCallback> httpClientCaptor =
                ArgumentCaptor.forClass(RestClientBuilder.HttpClientConfigCallback.class);
        verify(builder).setHttpClientConfigCallback(httpClientCaptor.capture());

        HttpAsyncClientBuilder httpAsyncClientBuilder = mock(HttpAsyncClientBuilder.class);
        httpClientCaptor.getValue().customizeHttpClient(httpAsyncClientBuilder);
        verify(httpAsyncClientBuilder, org.mockito.Mockito.never()).setDefaultCredentialsProvider(any());
    }
}

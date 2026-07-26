package com.xr.positiveaicode.ai.http;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderFactory;

import java.time.Duration;
import java.util.ServiceLoader;

/**
 * 构建带 DeepSeek thinking.disabled 的 HttpClient（复用 classpath 上的 HttpClientBuilderFactory）。
 */
public final class DeepSeekHttpClientFactory {

    private DeepSeekHttpClientFactory() {
    }

    public static HttpClientBuilder thinkingDisabledBuilder(Duration connectTimeout, Duration readTimeout) {
        HttpClientBuilderFactory factory = ServiceLoader.load(HttpClientBuilderFactory.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到 langchain4j HttpClientBuilderFactory"));

        return new HttpClientBuilder() {
            private Duration connect = connectTimeout != null ? connectTimeout : Duration.ofSeconds(10);
            private Duration read = readTimeout != null ? readTimeout : Duration.ofSeconds(120);

            @Override
            public Duration connectTimeout() {
                return connect;
            }

            @Override
            public HttpClientBuilder connectTimeout(Duration connectTimeout) {
                this.connect = connectTimeout;
                return this;
            }

            @Override
            public Duration readTimeout() {
                return read;
            }

            @Override
            public HttpClientBuilder readTimeout(Duration readTimeout) {
                this.read = readTimeout;
                return this;
            }

            @Override
            public HttpClient build() {
                HttpClientBuilder rawBuilder = factory.create();
                if (connect != null) {
                    rawBuilder.connectTimeout(connect);
                }
                if (read != null) {
                    rawBuilder.readTimeout(read);
                }
                return new ThinkingDisabledHttpClient(rawBuilder.build());
            }
        };
    }
}

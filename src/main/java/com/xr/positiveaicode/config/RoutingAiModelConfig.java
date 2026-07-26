package com.xr.positiveaicode.config;

import com.xr.positiveaicode.ai.http.DeepSeekHttpClientFactory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.routing-chat-model")
@Data
public class RoutingAiModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    @Bean
    @Scope("prototype")
    public ChatModel routingChatModelPrototype() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .maxTokens(maxTokens)
                .temperature(temperature != null ? temperature : 0.0)
                .timeout(Duration.ofSeconds(30))
                .httpClientBuilder(DeepSeekHttpClientFactory.thinkingDisabledBuilder(
                        Duration.ofSeconds(10), Duration.ofSeconds(30)))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}

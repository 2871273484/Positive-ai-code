package com.xr.positiveaicode.config;

import com.xr.positiveaicode.ai.http.DeepSeekHttpClientFactory;
import com.xr.positiveaicode.ai.monitor.AiModelMonitorListener;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.reasoning-streaming-chat-model")
@Data
public class ReasoningStreamingChatModelConfig {

    @Resource
    private AiModelMonitorListener aiModelMonitorListener;

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    @Bean
    @Scope("prototype")
    public StreamingChatModel reasoningStreamingChatModelPrototype() {
        // 同样关闭 thinking，避免 Vue 工程生成长时间无输出
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .connectTimeout(Duration.ofSeconds(10))
                .timeout(Duration.ofSeconds(180))
                .httpClientBuilder(DeepSeekHttpClientFactory.thinkingDisabledBuilder(
                        Duration.ofSeconds(10), Duration.ofSeconds(180)))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .listeners(List.of(aiModelMonitorListener))
                .build();
    }
}

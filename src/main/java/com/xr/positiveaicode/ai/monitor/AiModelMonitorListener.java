package com.xr.positiveaicode.ai.monitor;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
@Slf4j
public class AiModelMonitorListener implements ChatModelListener {

    // 用于存储请求开始时间的键
    private static final String REQUEST_START_TIME_KEY = "request_start_time";
    // 用于监控上下文传递（因为请求和响应事件的触发不是同一个线程）
    private static final String MONITOR_CONTEXT_KEY = "monitor_context";
    
    @Resource
    private AiModelMetricsCollector aiModelMetricsCollector;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestContext.attributes().put(REQUEST_START_TIME_KEY, Instant.now());
        MonitorContext context = MonitorContextHolder.getContext();
        if (context == null) {
            log.warn("监控上下文为空，跳过 onRequest 指标记录");
            return;
        }
        requestContext.attributes().put(MONITOR_CONTEXT_KEY, context);
        String userId = context.getUserId();
        String appId = context.getAppId();
        String modelName = requestContext.chatRequest().modelName();
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "started");
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Map<Object, Object> attributes = responseContext.attributes();
        MonitorContext context = (MonitorContext) attributes.get(MONITOR_CONTEXT_KEY);
        if (context == null) {
            log.warn("监控上下文为空，跳过 onResponse 指标记录");
            return;
        }
        String userId = context.getUserId();
        String appId = context.getAppId();
        String modelName = responseContext.chatResponse().modelName();
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "success");
        recordResponseTime(attributes, userId, appId, modelName);
        recordTokenUsage(responseContext, userId, appId, modelName);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Map<Object, Object> attributes = errorContext.attributes();
        MonitorContext context = (MonitorContext) attributes.get(MONITOR_CONTEXT_KEY);
        if (context == null) {
            context = MonitorContextHolder.getContext();
        }
        if (context == null) {
            log.warn("监控上下文为空，跳过 onError 指标记录: {}", errorContext.error().getMessage());
            return;
        }
        String userId = context.getUserId();
        String appId = context.getAppId();
        String modelName = errorContext.chatRequest().modelName();
        String errorMessage = errorContext.error().getMessage();
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "error");
        aiModelMetricsCollector.recordError(userId, appId, modelName, errorMessage);
        recordResponseTime(attributes, userId, appId, modelName);
    }


    /**
     * 记录响应时间
     */
    private void recordResponseTime(Map<Object, Object> attributes, String userId, String appId, String modelName) {
        Instant startTime = (Instant) attributes.get(REQUEST_START_TIME_KEY);
        Duration responseTime = Duration.between(startTime, Instant.now());
        aiModelMetricsCollector.recordResponseTime(userId, appId, modelName, responseTime);
    }

    /**
     * 记录Token使用情况
     */
    private void recordTokenUsage(ChatModelResponseContext responseContext, String userId, String appId, String modelName) {
        TokenUsage tokenUsage = responseContext.chatResponse().metadata().tokenUsage();
        if (tokenUsage != null) {
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "input", tokenUsage.inputTokenCount());
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "output", tokenUsage.outputTokenCount());
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "total", tokenUsage.totalTokenCount());
        }
    }
}

package com.xr.positiveaicode.ai.http;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * DeepSeek V4 默认开启 thinking，会先烧大量 reasoning 再吐内容。
 * 在请求体注入 thinking.disabled，显著加快路由与流式出码。
 */
@Slf4j
@RequiredArgsConstructor
public class ThinkingDisabledHttpClient implements HttpClient {

    private final HttpClient delegate;

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        return delegate.execute(withThinkingDisabled(request));
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        delegate.execute(withThinkingDisabled(request), parser, listener);
    }

    private static HttpRequest withThinkingDisabled(HttpRequest request) {
        String body = request.body();
        if (StrUtil.isBlank(body) || !body.contains("\"model\"")) {
            return request;
        }
        try {
            JSONObject json = JSONUtil.parseObj(body);
            Object thinking = json.get("thinking");
            if (thinking instanceof JSONObject thinkingObj
                    && "disabled".equals(thinkingObj.getStr("type"))) {
                return request;
            }
            json.set("thinking", JSONUtil.createObj().set("type", "disabled"));
            // 非法/多余的 reasoning_effort 一并去掉，避免 400
            json.remove("reasoning_effort");
            String newBody = json.toString();
            if (log.isDebugEnabled()) {
                log.debug("DeepSeek 请求已注入 thinking.disabled, model={}", json.getStr("model"));
            }
            return HttpRequest.builder()
                    .method(request.method())
                    .url(request.url())
                    .headers(request.headers())
                    .body(newBody)
                    .build();
        } catch (Exception e) {
            log.warn("注入 thinking.disabled 失败，使用原请求: {}", e.getMessage());
            return request;
        }
    }
}

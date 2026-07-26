package com.xr.positiveaicode.core.handler;

import com.xr.positiveaicode.langgraph4j.CodeGenWorkflow;
import com.xr.positiveaicode.model.entity.User;
import com.xr.positiveaicode.model.enums.ChatHistoryMessageTypeEnum;
import com.xr.positiveaicode.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器
 * 处理 HTML 和 MULTI_FILE 类型的流式响应
 */
@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 处理传统流（HTML, MULTI_FILE）
     * 直接收集完整的文本响应
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        StringBuilder aiResponseBuilder = new StringBuilder();
        return originFlux
                .map(chunk -> {
                    // 进度消息透传给前端，但不写入对话历史
                    if (chunk != null && chunk.startsWith(CodeGenWorkflow.PROGRESS_PREFIX)) {
                        return chunk;
                    }
                    aiResponseBuilder.append(chunk);
                    return chunk;
                })
                .doOnComplete(() -> {
                    String aiResponse = aiResponseBuilder.toString();
                    if (!aiResponse.isBlank()) {
                        chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    }
                })
                .doOnError(error -> {
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }
}

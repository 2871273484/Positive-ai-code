package com.xr.positiveaicode.ai;

import com.xr.positiveaicode.ai.guardrail.PromptSafetyInputGuardrail;
import com.xr.positiveaicode.ai.tools.*;
import com.xr.positiveaicode.exception.BusinessException;
import com.xr.positiveaicode.exception.ErrorCode;
import com.xr.positiveaicode.model.enums.CodeGenTypeEnum;
import com.xr.positiveaicode.service.ChatHistoryService;
import com.xr.positiveaicode.utils.SpringContextUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;

    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L);
    }

    /**
     * 根据 appId 获取服务（兼容历史逻辑）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据 appId 和代码生成类型获取服务。
     * 流式 ChatModel 非并发安全：每次新建（含新的 prototype 模型），避免上次未结束的 SSE 卡住后续生成。
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        return createAiCodeGeneratorService(appId, codeGenType);
    }

    /**
     * 创建新的 AI 服务实例
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        // 根据 appId 构建独立的对话记忆
        // HTML/多文件上下文不宜过长，否则 DeepSeek 流式极易中途停顿
        int maxMessages = codeGenType == CodeGenTypeEnum.VUE_PROJECT ? 40 : 8;
        int loadLimit = codeGenType == CodeGenTypeEnum.VUE_PROJECT ? 20 : 6;
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(maxMessages)
                .build();
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, loadLimit);
        // 根据代码生成类型选择不同的模型配置
        return switch (codeGenType) {
            case VUE_PROJECT -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)
                        .streamingChatModel(reasoningStreamingChatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools(toolManager.getAllTools())
                        .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                                toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()
                        ))
                        .maxSequentialToolsInvocations(30)  // 最多连续调用 20 次工具
                        .inputGuardrails(new PromptSafetyInputGuardrail())// 添加输入护轨
//                        .outputGuardrails(new RetryOutputGuardrail())// 添加输出护轨
                        .build();
            }
            case HTML, MULTI_FILE -> {
                // 每次新取 prototype StreamingChatModel，避免连接串线
                StreamingChatModel openAiStreamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)
                        .streamingChatModel(openAiStreamingChatModel)
                        .chatMemory(chatMemory)
                        .inputGuardrails(new PromptSafetyInputGuardrail())
                        .build();
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }

}

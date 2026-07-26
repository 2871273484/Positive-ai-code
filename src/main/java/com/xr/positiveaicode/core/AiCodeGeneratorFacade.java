package com.xr.positiveaicode.core;

import cn.hutool.json.JSONUtil;
import com.xr.positiveaicode.ai.AiCodeGeneratorService;
import com.xr.positiveaicode.ai.AiCodeGeneratorServiceFactory;
import com.xr.positiveaicode.ai.model.HtmlCodeResult;
import com.xr.positiveaicode.ai.model.MultiFileCodeResult;
import com.xr.positiveaicode.ai.model.message.AiResponseMessage;
import com.xr.positiveaicode.ai.model.message.ToolExecutedMessage;
import com.xr.positiveaicode.ai.model.message.ToolRequestMessage;
import com.xr.positiveaicode.constant.AppConstant;
import com.xr.positiveaicode.core.builder.VueProjectBuilder;
import com.xr.positiveaicode.core.parser.CodeParserExecutor;
import com.xr.positiveaicode.core.saver.CodeFileSaverExecutor;
import com.xr.positiveaicode.event.AppCodeGeneratedEvent;
import com.xr.positiveaicode.exception.BusinessException;
import com.xr.positiveaicode.exception.ErrorCode;
import com.xr.positiveaicode.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    @Lazy
    private AiCodeGeneratorService aiCodeGeneratorService;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    private Flux<String> generateAndSaveMultiFileCodeStream(String userMessage,Long appId) {
        Flux<String> stringFlux = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
        return processCodeStream(stringFlux, CodeGenTypeEnum.MULTI_FILE,appId);
    }

    private Flux<String> generateAndSaveHtmlCodeStream(String userMessage) {
        Flux<String> stringFlux = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
        StringBuilder codeBuilder = new StringBuilder();
        return stringFlux.doOnNext(chunk -> codeBuilder.append(chunk)).doOnComplete(() -> {
            try { // 保存文件
                String completeHtmlCode = codeBuilder.toString();
                // 解析HTML代码
                HtmlCodeResult htmlCodeResult = CodeParser.parseHtmlCode(completeHtmlCode);
                // 保存文件
                File saveDir = CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
                log.info("保存成功，保存的目录为：{}", saveDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存文件失败", e.getMessage());
            }

        });
    }

    /**
     * 生成 HTML 模式的代码并保存
     *
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
    private File generateAndSaveHtmlCode(String userMessage) {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
        return CodeFileSaver.saveHtmlCodeResult(result);
    }

    /**
     * 生成多文件模式的代码并保存
     *
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
    private File generateAndSaveMultiFileCode(String userMessage) {
        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
        return CodeFileSaver.saveMultiFileCodeResult(result);
    }

    /**
     * 通用流式代码处理方法。
     * 对「长时间无新 token」做空闲超时，避免前端一直转圈；超时仍尽量保存已生成内容。
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        AtomicBoolean saved = new AtomicBoolean(false);
        Runnable saveOnce = () -> {
            if (!saved.compareAndSet(false, true)) {
                return;
            }
            String completeCode = codeBuilder.toString();
            if (completeCode.isBlank()) {
                return;
            }
            try {
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，路径为：{}", savedDir.getAbsolutePath());
                eventPublisher.publishEvent(new AppCodeGeneratedEvent(this, appId));
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        };
        // HTML/多文件：20s 无新片段则结束（模型常停在末尾不关流）；Vue 仍用 35s
        Duration idleTimeout = (codeGenType == CodeGenTypeEnum.HTML || codeGenType == CodeGenTypeEnum.MULTI_FILE)
                ? Duration.ofSeconds(20)
                : Duration.ofSeconds(35);
        return codeStream
                .doOnNext(codeBuilder::append)
                .timeout(idleTimeout)
                .onErrorResume(error -> {
                    Throwable root = error;
                    while (root.getCause() != null && root != root.getCause()) {
                        root = root.getCause();
                    }
                    boolean idleTimeoutHit = error instanceof TimeoutException
                            || root instanceof TimeoutException
                            || (error.getMessage() != null && error.getMessage().contains("Did not observe any item"));
                    if (!idleTimeoutHit) {
                        return Flux.error(error);
                    }
                    log.warn("AI 流式空闲超时，结束并保存已生成内容, appId={}, chars={}", appId, codeBuilder.length());
                    saveOnce.run();
                    return Flux.just("\n\n> ⚠️ 模型长时间无响应，已停止。已保存已生成部分，请重试或继续说明要改哪里。\n");
                })
                .doOnComplete(saveOnce)
                .doOnCancel(saveOnce);
    }
    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum,Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 获取对应的服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML,appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum,Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 获取对应的服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML,appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE,appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream,Long appId) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        // 执行 Vue 项目构建（同步执行，确保预览时项目已就绪）
                        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
                        vueProjectBuilder.buildProject(projectPath);
                        eventPublisher.publishEvent(new AppCodeGeneratedEvent(this, appId));
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }


}

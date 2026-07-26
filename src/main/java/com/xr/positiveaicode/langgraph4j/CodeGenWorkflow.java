package com.xr.positiveaicode.langgraph4j;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.xr.positiveaicode.ai.monitor.MonitorContext;
import com.xr.positiveaicode.ai.monitor.MonitorContextHolder;
import com.xr.positiveaicode.core.AiCodeGeneratorFacade;
import com.xr.positiveaicode.exception.BusinessException;
import com.xr.positiveaicode.exception.ErrorCode;
import com.xr.positiveaicode.langgraph4j.node.*;
import com.xr.positiveaicode.langgraph4j.state.WorkflowContext;
import com.xr.positiveaicode.model.enums.CodeGenTypeEnum;
import com.xr.positiveaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Slf4j
@Component
public class CodeGenWorkflow {

    /**
     * 创建完整的工作流
     */
    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            return new MessagesStateGraph<String>()
                    .addNode("image_collector", ImageCollectorNode.create())
                    .addNode("prompt_enhancer", PromptEnhancerNode.create())
                    .addNode("router", RouterNode.create())
                    .addNode("code_generator", CodeGeneratorNode.create())
                    .addNode("project_builder", ProjectBuilderNode.create())

                    .addEdge(START, "image_collector")
                    .addEdge("image_collector", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "router")
                    .addEdge("router", "code_generator")
                    .addConditionalEdges("code_generator",
                            edge_async(this::routeBuildOrSkip),
                            Map.of(
                                    "build", "project_builder",
                                    "skip_build", END
                            ))
                    .addEdge("project_builder", END)
                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "工作流创建失败");
        }
    }

    /**
     * 准备阶段工作流：图片收集 → 提示词增强 → 路由（不含代码生成，避免 blockLast 卡死流式输出）
     */
    public CompiledGraph<MessagesState<String>> createPreparationWorkflow() {
        try {
            return new MessagesStateGraph<String>()
                    .addNode("image_collector", ImageCollectorNode.create())
                    .addNode("prompt_enhancer", PromptEnhancerNode.create())
                    .addNode("router", RouterNode.create())
                    .addEdge(START, "image_collector")
                    .addEdge("image_collector", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "router")
                    .addEdge("router", END)
                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "准备工作流创建失败");
        }
    }

    /**
     * 进度消息前缀（前端识别用，不写入最终代码历史）
     */
    public static final String PROGRESS_PREFIX = "[[PROGRESS]]";

    /**
     * 生产路径：先推送进度，再跑准备节点，最后透传 Facade 代码流。
     * 一开始就有 SSE 输出，避免前端长时间无响应。
     */
    public Flux<String> executeForApp(Long appId, String originalPrompt, CodeGenTypeEnum generationType) {
        MonitorContext monitorContext = MonitorContextHolder.getContext();
        Mono<WorkflowContext> preparation = Mono.fromCallable(() -> {
                    if (monitorContext != null) {
                        MonitorContextHolder.setContext(monitorContext);
                    }
                    return runPreparation(appId, originalPrompt, generationType);
                })
                .subscribeOn(Schedulers.boundedElastic())
                // 准备阶段整体封顶，外网超时也不拖住生成
                .timeout(java.time.Duration.ofSeconds(18))
                .onErrorResume(error -> {
                    log.warn("准备工作流超时/失败，跳过素材直接生成, appId={}: {}", appId, error.getMessage());
                    return Mono.just(WorkflowContext.builder()
                            .appId(appId)
                            .originalPrompt(originalPrompt)
                            .enhancedPrompt(originalPrompt + "\n\n## 生成约束\n- 不要在页面中展示系统架构图、流程图、站点结构图。\n")
                            .generationType(generationType)
                            .currentStep("图片收集跳过")
                            .build());
                })
                .cache();

        // 准备阶段心跳，让用户知道仍在处理
        Flux<String> preparationHeartbeat = Flux.interval(java.time.Duration.ofSeconds(5))
                .map(i -> PROGRESS_PREFIX + "仍在收集素材与准备提示词，已等待 " + ((i + 1) * 5) + " 秒…")
                .takeUntilOther(preparation);

        Flux<String> codePhase = preparation.flatMapMany(prepared -> {
            String prompt = StrUtil.blankToDefault(prepared.getEnhancedPrompt(), originalPrompt);
            CodeGenTypeEnum type = prepared.getGenerationType() != null
                    ? prepared.getGenerationType()
                    : generationType;
            log.info("准备工作流完成，开始流式代码生成, appId={}, type={}, promptLength={}",
                    appId, type, prompt.length());
            AiCodeGeneratorFacade facade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            Flux<String> codeFlux = facade.generateAndSaveCodeStream(prompt, type, appId)
                    .publish()
                    .refCount(1);
            // 出码间隙心跳（不写入代码正文，前端靠 PROGRESS 前缀识别）
            Flux<String> codeHeartbeat = Flux.interval(java.time.Duration.ofSeconds(8))
                    .map(i -> PROGRESS_PREFIX + "模型仍在生成，请稍候（已等待 "
                            + ((i + 1) * 8) + " 秒）…")
                    .takeUntilOther(codeFlux.ignoreElements());
            return Flux.concat(
                    Flux.just(PROGRESS_PREFIX + "正在连接 AI 模型生成代码…"),
                    Flux.merge(codeFlux, codeHeartbeat)
            );
        });

        return Flux.concat(
                        Flux.just(PROGRESS_PREFIX + "开始准备：收集图片素材（网络慢时会自动跳过）…"),
                        Flux.merge(preparationHeartbeat, preparation.thenMany(Flux.<String>empty())),
                        Flux.just(PROGRESS_PREFIX + "素材准备完成，开始生成网站代码…"),
                        codePhase
                )
                .doOnError(e -> log.error("应用工作流执行失败, appId={}: {}", appId, e.getMessage(), e))
                .doFinally(signal -> MonitorContextHolder.clearContext());
    }

    /**
     * 执行准备阶段并返回最终上下文
     */
    private WorkflowContext runPreparation(Long appId, String originalPrompt, CodeGenTypeEnum generationType) {
        CompiledGraph<MessagesState<String>> workflow = createPreparationWorkflow();
        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt(originalPrompt)
                .appId(appId)
                .generationType(generationType)
                .currentStep("初始化")
                .build();
        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("应用准备工作流图:\n{}", graph.content());
        log.info("开始执行准备工作流, appId={}, type={}", appId, generationType);

        WorkflowContext finalContext = initialContext;
        int stepCounter = 1;
        for (NodeOutput<MessagesState<String>> step : workflow.stream(
                Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
            WorkflowContext currentContext = WorkflowContext.getContext(step.state());
            if (currentContext != null) {
                finalContext = currentContext;
                log.info("--- 准备工作流第 {} 步完成: {} ---", stepCounter, currentContext.getCurrentStep());
            }
            stepCounter++;
        }
        return finalContext;
    }

    /**
     * 执行工作流
     */
    public WorkflowContext executeWorkflow(String originalPrompt) {
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();

        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt(originalPrompt)
                .currentStep("初始化")
                .build();

        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("工作流图:\n{}", graph.content());
        log.info("开始执行代码生成工作流");

        WorkflowContext finalContext = null;
        int stepCounter = 1;

        for (NodeOutput<MessagesState<String>> step : workflow.stream(
                Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
            log.info("--- 第 {} 步完成 ---", stepCounter);
            WorkflowContext currentContext = WorkflowContext.getContext(step.state());
            if (currentContext != null) {
                finalContext = currentContext;
                log.info("当前步骤上下文: {}", currentContext);
            }
            stepCounter++;
        }
        log.info("代码生成工作流执行完成！");
        return finalContext;
    }

    /**
     * 路由函数决定代码生成后是否需要项目构建
     */
    private String routeBuildOrSkip(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        CodeGenTypeEnum generationType = context.getGenerationType();
        if (generationType == CodeGenTypeEnum.HTML || generationType == CodeGenTypeEnum.MULTI_FILE) {
            return "skip_build";
        }
        return "build";
    }

    /**
     * 执行工作流（Flux 流式输出版本）
     */
    public Flux<String> executeWorkflowWithFlux(String originalPrompt) {
        return Flux.create(sink -> {
            Thread.startVirtualThread(() -> {
                try {
                    CompiledGraph<MessagesState<String>> workflow = createWorkflow();
                    WorkflowContext initialContext = WorkflowContext.builder()
                            .originalPrompt(originalPrompt)
                            .currentStep("初始化")
                            .build();
                    sink.next(formatSseEvent("workflow_start", Map.of(
                            "message", "开始执行代码生成工作流",
                            "originalPrompt", originalPrompt
                    )));
                    GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
                    log.info("工作流图:\n{}", graph.content());

                    int stepCounter = 1;
                    for (NodeOutput<MessagesState<String>> step : workflow.stream(
                            Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
                        log.info("--- 第 {} 步完成 ---", stepCounter);
                        WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                        if (currentContext != null) {
                            sink.next(formatSseEvent("step_completed", Map.of(
                                    "stepNumber", stepCounter,
                                    "currentStep", currentContext.getCurrentStep()
                            )));
                            log.info("当前步骤上下文: {}", currentContext);
                        }
                        stepCounter++;
                    }
                    sink.next(formatSseEvent("workflow_completed", Map.of(
                            "message", "代码生成工作流执行完成！"
                    )));
                    log.info("代码生成工作流执行完成！");
                    sink.complete();
                } catch (Exception e) {
                    log.error("工作流执行失败: {}", e.getMessage(), e);
                    sink.next(formatSseEvent("workflow_error", Map.of(
                            "error", e.getMessage(),
                            "message", "工作流执行失败"
                    )));
                    sink.error(e);
                }
            });
        });
    }

    private String formatSseEvent(String eventType, Object data) {
        try {
            String jsonData = JSONUtil.toJsonStr(data);
            return "event: " + eventType + "\ndata: " + jsonData + "\n\n";
        } catch (Exception e) {
            log.error("格式化 SSE 事件失败: {}", e.getMessage(), e);
            return "event: error\ndata: {\"error\":\"格式化失败\"}\n\n";
        }
    }

    /**
     * 执行工作流（SSE 流式输出版本）
     */
    public SseEmitter executeWorkflowWithSse(String originalPrompt) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        Thread.startVirtualThread(() -> {
            try {
                CompiledGraph<MessagesState<String>> workflow = createWorkflow();
                WorkflowContext initialContext = WorkflowContext.builder()
                        .originalPrompt(originalPrompt)
                        .currentStep("初始化")
                        .build();
                sendSseEvent(emitter, "workflow_start", Map.of(
                        "message", "开始执行代码生成工作流",
                        "originalPrompt", originalPrompt
                ));
                GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
                log.info("工作流图:\n{}", graph.content());

                int stepCounter = 1;
                for (NodeOutput<MessagesState<String>> step : workflow.stream(
                        Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
                    log.info("--- 第 {} 步完成 ---", stepCounter);
                    WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                    if (currentContext != null) {
                        sendSseEvent(emitter, "step_completed", Map.of(
                                "stepNumber", stepCounter,
                                "currentStep", currentContext.getCurrentStep()
                        ));
                        log.info("当前步骤上下文: {}", currentContext);
                    }
                    stepCounter++;
                }
                sendSseEvent(emitter, "workflow_completed", Map.of(
                        "message", "代码生成工作流执行完成！"
                ));
                log.info("代码生成工作流执行完成！");
                emitter.complete();
            } catch (Exception e) {
                log.error("工作流执行失败: {}", e.getMessage(), e);
                sendSseEvent(emitter, "workflow_error", Map.of(
                        "error", e.getMessage(),
                        "message", "工作流执行失败"
                ));
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void sendSseEvent(SseEmitter emitter, String eventType, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(data));
        } catch (IOException e) {
            log.error("发送 SSE 事件失败: {}", e.getMessage(), e);
        }
    }
}

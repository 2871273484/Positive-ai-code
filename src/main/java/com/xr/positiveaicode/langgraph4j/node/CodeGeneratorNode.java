package com.xr.positiveaicode.langgraph4j.node;

import com.xr.positiveaicode.constant.AppConstant;
import com.xr.positiveaicode.core.AiCodeGeneratorFacade;
import com.xr.positiveaicode.langgraph4j.state.WorkflowContext;
import com.xr.positiveaicode.model.enums.CodeGenTypeEnum;
import com.xr.positiveaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class CodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码生成");

            // 使用增强提示词作为发给 AI 的用户消息
            String userMessage = context.getEnhancedPrompt();
            CodeGenTypeEnum generationType = context.getGenerationType();
            AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            log.info("开始生成代码，类型: {} ({})", generationType.getValue(), generationType.getText());
            Long appId = context.getAppId() != null ? context.getAppId() : 1L;
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(userMessage, generationType, appId);
            // 演示路径同步等待完成；生产路径已在 CodeGenWorkflow.executeForApp 中直接透传 Facade 流
            codeStream.blockLast(Duration.ofMinutes(10));
            String generatedCodeDir = String.format("%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId);
            log.info("AI 代码生成完成，生成目录: {}", generatedCodeDir);

            // 更新状态
            context.setCurrentStep("代码生成");
            context.setGeneratedCodeDir(generatedCodeDir);
            return WorkflowContext.saveContext(context);
        });
    }
//    /**
//     * 构造用户消息，如果存在质检失败结果则添加错误修复信息
//     */
//    private static String buildUserMessage(WorkflowContext context) {
//        String userMessage = context.getEnhancedPrompt();
//        // 检查是否存在质检失败结果
//        QualityResult qualityResult = context.getQualityResult();
//        if (isQualityCheckFailed(qualityResult)) {
//            // 直接将错误修复信息作为新的提示词（起到了修改的作用）
//            userMessage = buildErrorFixPrompt(qualityResult);
//        }
//        return userMessage;
//    }
//
//    /**
//     * 判断质检是否失败
//     */
//    private static boolean isQualityCheckFailed(QualityResult qualityResult) {
//        return qualityResult != null &&
//                !qualityResult.getIsValid() &&
//                qualityResult.getErrors() != null &&
//                !qualityResult.getErrors().isEmpty();
//    }

//    /**
//     * 构造错误修复提示词
//     */
//    private static String buildErrorFixPrompt(QualityResult qualityResult) {
//        StringBuilder errorInfo = new StringBuilder();
//        errorInfo.append("\n\n## 上次生成的代码存在以下问题，请修复：\n");
//        // 添加错误列表
//        qualityResult.getErrors().forEach(error ->
//                errorInfo.append("- ").append(error).append("\n"));
//        // 添加修复建议（如果有）
//        if (qualityResult.getSuggestions() != null && !qualityResult.getSuggestions().isEmpty()) {
//            errorInfo.append("\n## 修复建议：\n");
//            qualityResult.getSuggestions().forEach(suggestion ->
//                    errorInfo.append("- ").append(suggestion).append("\n"));
//        }
//        errorInfo.append("\n请根据上述问题和建议重新生成代码，确保修复所有提到的问题。");
//        return errorInfo.toString();
//    }

}

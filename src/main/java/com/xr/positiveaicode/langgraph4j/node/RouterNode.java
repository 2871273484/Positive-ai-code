package com.xr.positiveaicode.langgraph4j.node;

import com.xr.positiveaicode.ai.AiCodeGenTypeRoutingService;
import com.xr.positiveaicode.ai.CodeGenTypeRoutingHelper;
import com.xr.positiveaicode.langgraph4j.state.WorkflowContext;
import com.xr.positiveaicode.model.enums.CodeGenTypeEnum;
import com.xr.positiveaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class RouterNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 智能路由");

            CodeGenTypeEnum generationType = context.getGenerationType();
            if (generationType != null) {
                // 生产路径已按应用固定类型，跳过重新路由
                log.info("使用预设代码生成类型: {} ({})", generationType.getValue(), generationType.getText());
            } else {
                AiCodeGenTypeRoutingService routingService = SpringContextUtil.getBean(AiCodeGenTypeRoutingService.class);
                generationType = CodeGenTypeRoutingHelper.routeSafely(routingService, context.getOriginalPrompt());
                log.info("智能路由完成，选择类型: {} ({})", generationType.getValue(), generationType.getText());
            }

            context.setCurrentStep("智能路由");
            context.setGenerationType(generationType);
            return WorkflowContext.saveContext(context);
        });
    }
}

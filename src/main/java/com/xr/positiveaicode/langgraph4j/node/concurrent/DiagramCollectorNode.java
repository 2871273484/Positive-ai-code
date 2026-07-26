package com.xr.positiveaicode.langgraph4j.node.concurrent;

import com.xr.positiveaicode.langgraph4j.model.ImageResource;
import com.xr.positiveaicode.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 架构图收集节点（已弃用：不再生成系统架构图）
 */
@Slf4j
public class DiagramCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            List<ImageResource> diagrams = new ArrayList<>();
            log.info("跳过架构图生成（产品不再展示系统架构图）");
            context.setDiagrams(diagrams);
            context.setCurrentStep("架构图生成");
            return WorkflowContext.saveContext(context);
        });
    }
}

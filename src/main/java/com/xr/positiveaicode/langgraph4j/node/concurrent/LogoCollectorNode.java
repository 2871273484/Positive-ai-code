package com.xr.positiveaicode.langgraph4j.node.concurrent;

import com.xr.positiveaicode.langgraph4j.model.ImageResource;
import com.xr.positiveaicode.langgraph4j.state.WorkflowContext;
import com.xr.positiveaicode.langgraph4j.tools.LogoGeneratorTool;
import com.xr.positiveaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class LogoCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            List<ImageResource> logos = new ArrayList<>();
            try {
                LogoGeneratorTool logoTool = SpringContextUtil.getBean(LogoGeneratorTool.class);
                ImageResource defaultLogo = logoTool.getDefaultSiteLogo();
                if (defaultLogo != null) {
                    logos.add(defaultLogo);
                    log.info("使用固定品牌 Logo: {}", defaultLogo.getUrl());
                }
            } catch (Exception e) {
                log.error("Logo 准备失败: {}", e.getMessage(), e);
            }
            context.setLogos(logos);
            context.setCurrentStep("Logo生成");
            return WorkflowContext.saveContext(context);
        });
    }
}

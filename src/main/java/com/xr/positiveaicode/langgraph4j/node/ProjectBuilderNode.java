package com.xr.positiveaicode.langgraph4j.node;

import com.xr.positiveaicode.core.builder.VueProjectBuilder;
import com.xr.positiveaicode.exception.BusinessException;
import com.xr.positiveaicode.exception.ErrorCode;
import com.xr.positiveaicode.langgraph4j.state.WorkflowContext;
import com.xr.positiveaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class ProjectBuilderNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 项目构建");

            String generatedCodeDir = context.getGeneratedCodeDir();
            String buildResultDir;
            File distDir = new File(generatedCodeDir, "dist");
            // 代码生成阶段可能已构建过（如 Facade 内对 Vue 项目的构建），避免重复 npm build
            if (distDir.exists() && distDir.isDirectory()) {
                buildResultDir = distDir.getAbsolutePath();
                log.info("检测到已有 dist 目录，跳过重复构建: {}", buildResultDir);
            } else {
                try {
                    VueProjectBuilder vueBuilder = SpringContextUtil.getBean(VueProjectBuilder.class);
                    boolean buildSuccess = vueBuilder.buildProject(generatedCodeDir);
                    if (buildSuccess) {
                        buildResultDir = generatedCodeDir + File.separator + "dist";
                        log.info("Vue 项目构建成功，dist 目录: {}", buildResultDir);
                    } else {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败");
                    }
                } catch (Exception e) {
                    log.error("Vue 项目构建异常: {}", e.getMessage(), e);
                    buildResultDir = generatedCodeDir;
                }
            }
            // 更新状态
            context.setCurrentStep("项目构建");
            context.setBuildResultDir(buildResultDir);
            log.info("项目构建节点完成，最终目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }
}

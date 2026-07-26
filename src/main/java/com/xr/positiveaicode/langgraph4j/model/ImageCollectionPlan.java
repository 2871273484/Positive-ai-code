package com.xr.positiveaicode.langgraph4j.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ImageCollectionPlan implements Serializable {
    
    /**
     * 内容图片搜索任务列表
     */
    private List<ImageSearchTask> contentImageTasks;
    
    /**
     * 插画图片搜索任务列表
     */
    private List<IllustrationTask> illustrationTasks;
    
    /**
     * 架构图生成任务列表
     */
    private List<DiagramTask> diagramTasks;
    
    /**
     * Logo 任务列表（已弃用，保留字段兼容旧计划；运行时注入固定品牌 Logo）
     */
    private List<LogoTask> logoTasks;
    
    /**
     * 内容图片搜索任务
     * 对应 ImageSearchTool.searchContentImages(String query)
     */
    public record ImageSearchTask(String query) implements Serializable {}
    
    /**
     * 插画图片搜索任务
     * 对应 UndrawIllustrationTool.searchIllustrations(String query)
     */
    public record IllustrationTask(String query) implements Serializable {}
    
    /**
     * 架构图生成任务
     * 对应 MermaidDiagramTool.generateMermaidDiagram(String mermaidCode, String description)
     */
    public record DiagramTask(String mermaidCode, String description) implements Serializable {}
    
    /**
     * Logo 任务（已弃用：系统注入固定品牌 Logo，规划阶段应始终为空）
     */
    public record LogoTask(String description) implements Serializable {}
}

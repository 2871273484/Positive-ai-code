package com.xr.positiveaicode.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.xr.positiveaicode.langgraph4j.enums.ImageCategoryEnum;
import com.xr.positiveaicode.langgraph4j.model.ImageResource;
import com.xr.positiveaicode.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class PromptEnhancerNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 提示词增强");
            String originalPrompt = context.getOriginalPrompt();
            String imageListStr = context.getImageListStr();
            List<ImageResource> imageList = context.getImageList();

            StringBuilder enhancedPromptBuilder = new StringBuilder();
            enhancedPromptBuilder.append(originalPrompt);
            enhancedPromptBuilder.append("\n\n## 生成约束\n");
            enhancedPromptBuilder.append("- 不要在页面中展示系统架构图、流程图、站点结构图、sitemap 示意图等内容。\n");
            enhancedPromptBuilder.append("- 页面应直接呈现业务内容，而不是网站结构说明。\n");
            enhancedPromptBuilder.append("- 图片必须与展示内容语义一致：商品图对应商品名，禁止风景/建筑图配电子产品或服装。\n");
            enhancedPromptBuilder.append("- 严禁使用 picsum.photos 随机图。素材不足时用 https://loremflickr.com/600/600/{english-keywords} ，关键词必须与商品/场景一致。\n");

            // 固定品牌 Logo 单独置顶，避免模型忽略
            String logoUrl = null;
            if (CollUtil.isNotEmpty(imageList)) {
                for (ImageResource image : imageList) {
                    if (image.getCategory() == ImageCategoryEnum.LOGO && StrUtil.isNotBlank(image.getUrl())) {
                        logoUrl = image.getUrl();
                        break;
                    }
                }
            }
            if (StrUtil.isNotBlank(logoUrl)) {
                enhancedPromptBuilder.append("\n## 品牌 Logo（强制）\n");
                enhancedPromptBuilder.append("- 全站导航栏左侧 Logo、favicon 必须使用此 URL，禁止用文字 Logo 或其它图片替代：\n");
                enhancedPromptBuilder.append("- ").append(logoUrl).append("\n");
                enhancedPromptBuilder.append("- `<img src=\"").append(logoUrl)
                        .append("\" alt=\"Logo\" />`，圆形展示（如 border-radius:50%）。\n");
            }

            if (CollUtil.isNotEmpty(imageList) || StrUtil.isNotBlank(imageListStr)) {
                enhancedPromptBuilder.append("\n## 可用素材资源\n");
                enhancedPromptBuilder.append("请优先使用下列图片 URL。商品卡片请按描述语义一一匹配（耳机用耳机图、羽绒服用服装图），不要错配。\n");
                enhancedPromptBuilder.append("标记为 LOGO 的图片必须作为网站 Logo（导航栏左侧等），不要替换。\n");
                if (CollUtil.isNotEmpty(imageList)) {
                    int contentOrIllustration = 0;
                    final int maxExtraImages = 12;
                    for (ImageResource image : imageList) {
                        if (image.getCategory() == ImageCategoryEnum.ARCHITECTURE) {
                            continue;
                        }
                        if (image.getCategory() != ImageCategoryEnum.LOGO) {
                            if (contentOrIllustration >= maxExtraImages) {
                                continue;
                            }
                            contentOrIllustration++;
                        }
                        String desc = StrUtil.maxLength(StrUtil.blankToDefault(image.getDescription(), ""), 80);
                        enhancedPromptBuilder.append("- ")
                                .append(image.getCategory().getText())
                                .append("：")
                                .append(desc)
                                .append("（")
                                .append(image.getUrl())
                                .append("）\n");
                    }
                } else {
                    enhancedPromptBuilder.append(imageListStr);
                }
            }
            String enhancedPrompt = enhancedPromptBuilder.toString();
            context.setCurrentStep("提示词增强");
            context.setEnhancedPrompt(enhancedPrompt);
            log.info("提示词增强完成，增强后长度: {} 字符", enhancedPrompt.length());
            return WorkflowContext.saveContext(context);
        });
    }
}

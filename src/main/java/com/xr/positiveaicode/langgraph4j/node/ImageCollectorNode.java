package com.xr.positiveaicode.langgraph4j.node;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.StrUtil;
import com.xr.positiveaicode.langgraph4j.ai.ImageCollectionPlanService;
import com.xr.positiveaicode.langgraph4j.enums.ImageCategoryEnum;
import com.xr.positiveaicode.langgraph4j.model.ImageCollectionPlan;
import com.xr.positiveaicode.langgraph4j.model.ImageResource;
import com.xr.positiveaicode.langgraph4j.state.WorkflowContext;
import com.xr.positiveaicode.langgraph4j.tools.ImageSearchTool;
import com.xr.positiveaicode.langgraph4j.tools.LogoGeneratorTool;
import com.xr.positiveaicode.langgraph4j.tools.UndrawIllustrationTool;
import com.xr.positiveaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集节点：电商等场景收集强相关商品图；简单工具站仅注入 Logo。
 */
@Slf4j
public class ImageCollectorNode {

    private static final int MAX_CONTENT_TASKS_DEFAULT = 2;
    private static final int MAX_CONTENT_TASKS_COMMERCE = 4;
    /** 插画 Pexels 经常超时，默认不再搜索，避免卡在素材阶段 */
    private static final int MAX_ILLUSTRATION_TASKS = 0;
    private static final int TASK_TIMEOUT_SECONDS = 4;
    private static final int OVERALL_TIMEOUT_SECONDS = 8;
    private static final int PLAN_TIMEOUT_SECONDS = 6;

    /** 电商场景兜底品类查询，保证商品图语义相关 */
    private static final List<String> COMMERCE_FALLBACK_QUERIES = List.of(
            "wireless headphones product",
            "down jacket clothing",
            "gaming mouse",
            "nordic table lamp",
            "sneakers shoes",
            "smart watch product"
    );

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            String originalPrompt = context.getOriginalPrompt();
            List<ImageResource> collectedImages = new ArrayList<>();
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            try {
                LogoGeneratorTool logoTool = SpringContextUtil.getBean(LogoGeneratorTool.class);
                ImageResource defaultLogo = logoTool.getDefaultSiteLogo();
                if (defaultLogo != null) {
                    collectedImages.add(defaultLogo);
                }

                if (isSimplePrompt(originalPrompt)) {
                    log.info("简单工具站需求，跳过外网配图，仅注入 Logo");
                } else {
                    boolean commerce = isCommercePrompt(originalPrompt);
                    collectRemoteImages(originalPrompt, collectedImages, commerce);
                    // 电商若 AI 规划不足，用固定品类查询补齐
                    if (commerce && countNonLogo(collectedImages) < 4) {
                        log.info("电商配图不足，使用品类兜底查询补齐");
                        collectByQueries(COMMERCE_FALLBACK_QUERIES, collectedImages, 6);
                    }
                }

                log.info("图片收集完成，共 {} 张", collectedImages.size());
            } catch (Exception e) {
                log.error("图片收集失败（将无配图继续生成）: {}", e.getMessage(), e);
            }
            stopWatch.stop();
            log.info("图片收集总耗时: {} ms", stopWatch.getTotalTimeMillis());
            context.setCurrentStep("图片收集");
            context.setImageList(collectedImages);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 仅计算器/小工具等跳过配图；购物/商城等短句也必须拉相关图。
     */
    static boolean isSimplePrompt(String prompt) {
        if (StrUtil.isBlank(prompt)) {
            return true;
        }
        String lower = prompt.trim().toLowerCase();
        // 电商必须配图；其余短需求可只打 Logo，加快出码
        if (isCommercePrompt(lower)) {
            return false;
        }
        if (containsAny(lower, "商城", "购物", "商店", "电商")) {
            return false;
        }
        // 个人主页 / 简单官网：仅 Logo，避免 Pexels 卡住
        if (containsAny(lower, "个人主页", "个人网站", "简历页", "landing")) {
            return true;
        }
        return containsAny(lower,
                "计算器", "calculator", "时钟", "倒计时", "小工具",
                "不超过", "单页工具");
    }

    static boolean isCommercePrompt(String prompt) {
        if (StrUtil.isBlank(prompt)) {
            return false;
        }
        String lower = prompt.toLowerCase();
        return containsAny(lower,
                "购物", "商城", "电商", "商店", "店铺", "商品",
                "shop", "store", "mall", "ecommerce", "e-commerce", "购物车");
    }

    private static int countNonLogo(List<ImageResource> images) {
        int n = 0;
        for (ImageResource image : images) {
            if (image.getCategory() != ImageCategoryEnum.LOGO) {
                n++;
            }
        }
        return n;
    }

    private static void collectRemoteImages(String originalPrompt, List<ImageResource> collectedImages, boolean commerce) {
        ImageCollectionPlan plan = planWithTimeout(originalPrompt);
        int maxContent = commerce ? MAX_CONTENT_TASKS_COMMERCE : MAX_CONTENT_TASKS_DEFAULT;
        List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
        ImageSearchTool imageSearchTool = SpringContextUtil.getBean(ImageSearchTool.class);

        if (plan != null && plan.getContentImageTasks() != null && !plan.getContentImageTasks().isEmpty()) {
            log.info("获取到图片收集计划，开始并发执行（带超时）, commerce={}", commerce);
            plan.getContentImageTasks().stream()
                    .limit(maxContent)
                    .forEach(task -> futures.add(supplyWithTimeout(
                            () -> imageSearchTool.searchContentImages(task.query()),
                            "content:" + task.query())));
        } else {
            // 规划超时/失败：用简短关键词兜底，不再空等
            String fallbackQuery = heuristicQuery(originalPrompt);
            log.info("图片规划跳过/失败，使用兜底查询: {}", fallbackQuery);
            futures.add(supplyWithTimeout(
                    () -> imageSearchTool.searchContentImages(fallbackQuery),
                    "content-fallback:" + fallbackQuery));
        }

        if (MAX_ILLUSTRATION_TASKS > 0 && !commerce && plan != null && plan.getIllustrationTasks() != null) {
            UndrawIllustrationTool illustrationTool = SpringContextUtil.getBean(UndrawIllustrationTool.class);
            plan.getIllustrationTasks().stream()
                    .limit(MAX_ILLUSTRATION_TASKS)
                    .forEach(task -> futures.add(supplyWithTimeout(
                            () -> illustrationTool.searchIllustrations(task.query()),
                            "illustration:" + task.query())));
        }
        awaitAndCollect(futures, collectedImages);
    }

    /**
     * AI 规划外网素材：短超时，超时则返回 null 走兜底。
     */
    private static ImageCollectionPlan planWithTimeout(String originalPrompt) {
        try {
            ImageCollectionPlanService planService = SpringContextUtil.getBean(ImageCollectionPlanService.class);
            return CompletableFuture
                    .supplyAsync(() -> planService.planImageCollection(originalPrompt))
                    .orTimeout(PLAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        log.warn("图片收集计划超时/失败，将走兜底查询: {}", ex.getMessage());
                        return null;
                    })
                    .join();
        } catch (Exception e) {
            log.warn("图片收集计划异常: {}", e.getMessage());
            return null;
        }
    }

    private static String heuristicQuery(String prompt) {
        if (StrUtil.isBlank(prompt)) {
            return "modern website workspace";
        }
        String p = prompt.toLowerCase();
        if (containsAny(p, "音乐", "music", "专辑")) {
            return "musician concert stage";
        }
        if (containsAny(p, "博客", "blog")) {
            return "writing desk laptop";
        }
        if (containsAny(p, "个人", "作品集", "portfolio", "主页")) {
            return "creative portfolio desk";
        }
        if (containsAny(p, "企业", "公司", "官网")) {
            return "modern office business";
        }
        return "modern website design";
    }

    private static void collectByQueries(List<String> queries, List<ImageResource> collectedImages, int maxTasks) {
        ImageSearchTool imageSearchTool = SpringContextUtil.getBean(ImageSearchTool.class);
        List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
        queries.stream().limit(maxTasks).forEach(query ->
                futures.add(supplyWithTimeout(
                        () -> imageSearchTool.searchContentImages(query),
                        "fallback:" + query)));
        awaitAndCollect(futures, collectedImages);
    }

    private static void awaitAndCollect(List<CompletableFuture<List<ImageResource>>> futures,
                                        List<ImageResource> collectedImages) {
        if (futures.isEmpty()) {
            return;
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            all.get(OVERALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("图片收集整体超时或失败，使用已完成结果继续: {}", e.getMessage());
        }
        for (CompletableFuture<List<ImageResource>> future : futures) {
            if (!future.isDone() || future.isCompletedExceptionally()) {
                future.cancel(true);
                continue;
            }
            try {
                List<ImageResource> images = future.getNow(List.of());
                if (images != null) {
                    // 每个查询只取前 2 张，避免同类图过多
                    collectedImages.addAll(images.stream().limit(2).toList());
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static CompletableFuture<List<ImageResource>> supplyWithTimeout(
            java.util.function.Supplier<List<ImageResource>> supplier, String taskName) {
        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return supplier.get();
                    } catch (Exception e) {
                        log.warn("图片任务失败 {}: {}", taskName, e.getMessage());
                        return List.<ImageResource>of();
                    }
                })
                .orTimeout(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.warn("图片任务超时/异常 {}: {}", taskName, ex.getMessage());
                    return List.of();
                });
    }
}

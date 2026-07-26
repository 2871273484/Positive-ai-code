package com.xr.positiveaicode.ai;

import cn.hutool.core.util.StrUtil;
import com.xr.positiveaicode.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;

/**
 * 代码生成类型路由：AI 优先，失败时回退启发式，避免创建应用中断。
 */
@Slf4j
public final class CodeGenTypeRoutingHelper {

    private CodeGenTypeRoutingHelper() {
    }

    public static CodeGenTypeEnum routeSafely(AiCodeGenTypeRoutingService routingService, String userPrompt) {
        try {
            CodeGenTypeEnum type = routingService.routeCodeGenType(userPrompt);
            if (type != null) {
                return type;
            }
            log.warn("AI 路由返回空，改用启发式规则");
        } catch (Exception e) {
            log.warn("AI 路由失败，改用启发式规则: {}", e.getMessage());
        }
        return fallbackByPrompt(userPrompt);
    }

    /**
     * 简单规则：有复杂交互关键词用 VUE_PROJECT，多页用 MULTI_FILE，其余 HTML
     */
    static CodeGenTypeEnum fallbackByPrompt(String userPrompt) {
        String prompt = StrUtil.blankToDefault(userPrompt, "").toLowerCase();
        if (containsAny(prompt, "vue", "组件", "后台管理", "管理系统", "复杂交互", "登录注册", "购物车", "数据管理")) {
            return CodeGenTypeEnum.VUE_PROJECT;
        }
        if (containsAny(prompt, "多页面", "多个页面", "多页", "网站地图", "导航栏")) {
            return CodeGenTypeEnum.MULTI_FILE;
        }
        // 行数受限的简单站点默认 HTML
        return CodeGenTypeEnum.HTML;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}

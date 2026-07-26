package com.xr.positiveaicode.utils;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从用户 prompt 提取短应用名，例如「做一个简单的音乐网站」→「音乐网站」
 */
public final class AppNameUtils {

    private static final int MAX_NAME_LENGTH = 16;

    private static final Pattern LEADING_VERB = Pattern.compile(
            "^(帮我|请|麻烦|使用\\S{0,12})?(创建|做|生成|制作|搭建|开发|设计)(一个|个|一)?");

    private static final Pattern LEADING_MODIFIER = Pattern.compile(
            "^(简单的|简单|基础的|基础|小型的|小型|精美的|精美|专业的|专业|现代化的|现代|简洁的|简洁)");

    private static final Pattern SITE_TYPE = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9]{1,12}(?:网站|应用|系统|平台|商城|博客|官网|主页|首页|工具|计算器))");

    private AppNameUtils() {
    }

    public static String deriveAppName(String initPrompt) {
        if (StrUtil.isBlank(initPrompt)) {
            return "未命名应用";
        }
        String text = initPrompt.trim()
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", "");

        // 截断约束说明：「不超过5页」「，需要…」
        text = text.split("[，,。.!！？?；;：:]")[0];
        text = text.replaceAll("不超过.*$", "");
        text = text.replaceAll("不要.*$", "");
        text = text.replaceAll("需要.*$", "");

        text = LEADING_VERB.matcher(text).replaceFirst("");
        text = LEADING_MODIFIER.matcher(text).replaceFirst("");
        text = text.replaceFirst("^的", "");

        Matcher matcher = SITE_TYPE.matcher(text);
        if (matcher.find()) {
            return truncate(matcher.group(1));
        }

        if (StrUtil.isBlank(text)) {
            return truncate(initPrompt.trim());
        }
        return truncate(text);
    }

    /**
     * 旧逻辑用 prompt 前 12 字当名称时，用衍生短名覆盖展示
     */
    public static String resolveDisplayName(String appName, String initPrompt) {
        if (StrUtil.isBlank(initPrompt)) {
            return StrUtil.blankToDefault(appName, "未命名应用");
        }
        String truncated = initPrompt.substring(0, Math.min(initPrompt.length(), 12));
        if (StrUtil.isBlank(appName)
                || StrUtil.equals(appName, truncated)
                || looksLikeRawPrompt(appName)
                || appName.length() > MAX_NAME_LENGTH) {
            return deriveAppName(initPrompt);
        }
        return appName;
    }

    private static boolean looksLikeRawPrompt(String appName) {
        return appName.startsWith("做一个")
                || appName.startsWith("帮我")
                || appName.startsWith("创建")
                || appName.startsWith("生成一个")
                || appName.startsWith("设计一个")
                || appName.startsWith("设计")
                || appName.startsWith("请做")
                || appName.startsWith("使用");
    }

    private static String truncate(String name) {
        String cleaned = StrUtil.trim(name);
        if (cleaned.length() <= MAX_NAME_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_NAME_LENGTH);
    }
}

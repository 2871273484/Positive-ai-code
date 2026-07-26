package com.xr.positiveaicode.langgraph4j.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xr.positiveaicode.langgraph4j.enums.ImageCategoryEnum;
import com.xr.positiveaicode.langgraph4j.model.ImageResource;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 插画搜索：优先走 Pexels（国内可达性更好），短超时失败即跳过。
 */
@Slf4j
@Component
public class UndrawIllustrationTool {

    private static final String PEXELS_API_URL = "https://api.pexels.com/v1/search";
    private static final int MAX_COUNT = 4;
    /** 外网不稳时快速失败，避免拖住整站生成 */
    private static final int TIMEOUT_MS = 3000;

    @Value("${pexels.api-key:}")
    private String pexelsApiKey;

    @Tool("搜索插画图片，用于网站美化和装饰")
    public List<ImageResource> searchIllustrations(@P("搜索关键词") String query) {
        if (StrUtil.isBlank(query)) {
            return List.of();
        }
        return searchIllustrationStyleFromPexels(query);
    }

    private List<ImageResource> searchIllustrationStyleFromPexels(String query) {
        List<ImageResource> imageList = new ArrayList<>();
        if (StrUtil.isBlank(pexelsApiKey)) {
            log.warn("Pexels API Key 未配置，跳过插画搜索");
            return imageList;
        }
        String searchQuery = query + " illustration vector flat";
        try (HttpResponse response = HttpRequest.get(PEXELS_API_URL)
                .header("Authorization", pexelsApiKey)
                .form("query", searchQuery)
                .form("per_page", MAX_COUNT)
                .form("page", 1)
                .timeout(TIMEOUT_MS)
                .execute()) {
            if (!response.isOk()) {
                return imageList;
            }
            JSONObject result = JSONUtil.parseObj(response.body());
            JSONArray photos = result.getJSONArray("photos");
            if (photos == null) {
                return imageList;
            }
            for (int i = 0; i < photos.size(); i++) {
                JSONObject photo = photos.getJSONObject(i);
                JSONObject src = photo.getJSONObject("src");
                if (src == null) {
                    continue;
                }
                imageList.add(ImageResource.builder()
                        .category(ImageCategoryEnum.ILLUSTRATION)
                        .description(photo.getStr("alt", query))
                        .url(src.getStr("medium"))
                        .build());
            }
            log.info("Pexels 插画搜索完成, query={}, count={}", query, imageList.size());
        } catch (Exception e) {
            log.warn("Pexels 插画搜索超时或失败，跳过 query={}: {}", query, e.getMessage());
        }
        return imageList;
    }
}

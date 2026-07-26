package com.xr.positiveaicode.langgraph4j.tools;

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
 * 图片搜索工具（短超时，失败立即返回空列表，不阻塞生成）
 */
@Slf4j
@Component
public class ImageSearchTool {

    private static final String PEXELS_API_URL = "https://api.pexels.com/v1/search";
    /** 外网不稳时快速失败，避免拖住整站生成 */
    private static final int TIMEOUT_MS = 3000;
    private static final int SEARCH_COUNT = 3;

    @Value("${pexels.api-key}")
    private String pexelsApiKey;

    @Tool("搜索内容相关的图片，用于网站内容展示")
    public List<ImageResource> searchContentImages(@P("搜索关键词") String query) {
        List<ImageResource> imageList = new ArrayList<>();
        try (HttpResponse response = HttpRequest.get(PEXELS_API_URL)
                .header("Authorization", pexelsApiKey)
                .form("query", query)
                .form("per_page", SEARCH_COUNT)
                .form("page", 1)
                .timeout(TIMEOUT_MS)
                .execute()) {
            if (response.isOk()) {
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
                            .category(ImageCategoryEnum.CONTENT)
                            .description(photo.getStr("alt", query))
                            .url(src.getStr("medium"))
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Pexels 搜索超时或失败，跳过配图 query={}: {}", query, e.getMessage());
        }
        return imageList;
    }
}

package com.xr.positiveaicode.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.xr.positiveaicode.langgraph4j.enums.ImageCategoryEnum;
import com.xr.positiveaicode.langgraph4j.model.ImageResource;
import com.xr.positiveaicode.manager.CosManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网站 Logo 工具：统一使用项目固定品牌图，不再调用 AI 生图。
 * COS 上传有短超时，失败时回退到本地静态地址，避免卡住生成。
 */
@Slf4j
@Component
public class LogoGeneratorTool {

    private static final String LOGO_CLASSPATH = "static/brand/default-site-logo.png";
    /** COS key 带版本号，更换素材时递增以免 CDN 命中旧图 */
    private static final String LOGO_COS_KEY = "/brand/default-site-logo-mascot-v1.png";
    private static final String LOGO_LOCAL_URL = "http://localhost:8080/api/brand/default-site-logo.png";
    private static final String LOGO_DESCRIPTION =
            "网站唯一品牌 Logo（蓝色圆形吉祥物，必须原样用于导航栏左侧 Logo、favicon，禁止替换为文字或其他图片）";
    private static final int COS_UPLOAD_TIMEOUT_SECONDS = 5;

    @Resource
    private CosManager cosManager;

    private final AtomicReference<String> cachedLogoUrl = new AtomicReference<>();

    @Tool("获取网站品牌 Logo 图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        ImageResource logo = getDefaultSiteLogo();
        if (logo == null) {
            return Collections.emptyList();
        }
        if (StrUtil.isNotBlank(description)) {
            logo.setDescription(LOGO_DESCRIPTION + "；场景：" + description);
        }
        return Collections.singletonList(logo);
    }

    public ImageResource getDefaultSiteLogo() {
        String url = ensureLogoUrl();
        if (StrUtil.isBlank(url)) {
            return null;
        }
        return ImageResource.builder()
                .category(ImageCategoryEnum.LOGO)
                .description(LOGO_DESCRIPTION)
                .url(url)
                .build();
    }

    private String ensureLogoUrl() {
        String cached = cachedLogoUrl.get();
        if (StrUtil.isNotBlank(cached)) {
            return cached;
        }
        synchronized (this) {
            cached = cachedLogoUrl.get();
            if (StrUtil.isNotBlank(cached)) {
                return cached;
            }
            String cosUrl = tryUploadToCos();
            String finalUrl = StrUtil.blankToDefault(cosUrl, LOGO_LOCAL_URL);
            cachedLogoUrl.set(finalUrl);
            log.info("固定品牌 Logo 已就绪: {}", finalUrl);
            return finalUrl;
        }
    }

    private String tryUploadToCos() {
        File tempFile = null;
        try {
            ClassPathResource resource = new ClassPathResource(LOGO_CLASSPATH);
            if (!resource.exists()) {
                log.warn("固定品牌 Logo 资源不存在，使用本地占位地址");
                return null;
            }
            tempFile = File.createTempFile("default-site-logo-", ".png");
            try (InputStream in = resource.getInputStream()) {
                FileUtil.writeFromStream(in, tempFile);
            }
            File uploadFile = tempFile;
            return CompletableFuture.supplyAsync(() -> cosManager.uploadFile(LOGO_COS_KEY, uploadFile))
                    .orTimeout(COS_UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        log.warn("Logo 上传 COS 超时/失败，回退本地地址: {}", ex.getMessage());
                        return null;
                    })
                    .join();
        } catch (Exception e) {
            log.warn("准备固定品牌 Logo 失败，回退本地地址: {}", e.getMessage());
            return null;
        } finally {
            if (tempFile != null) {
                FileUtil.del(tempFile);
            }
        }
    }
}

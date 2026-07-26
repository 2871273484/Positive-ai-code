package com.xr.positiveaicode.service.impl;

import cn.hutool.core.util.StrUtil;
import com.xr.positiveaicode.constant.AppConstant;
import com.xr.positiveaicode.model.entity.App;
import com.xr.positiveaicode.model.enums.CodeGenTypeEnum;
import com.xr.positiveaicode.service.AppCoverService;
import com.xr.positiveaicode.service.AppService;
import com.xr.positiveaicode.service.ScreenshotService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AppCoverServiceImpl implements AppCoverService {

    private final Set<Long> generatingAppIds = ConcurrentHashMap.newKeySet();

    @Resource
    private ScreenshotService screenshotService;

    @Lazy
    @Resource
    private AppService appService;

    @Override
    public String generateCover(Long appId) {
        if (appId == null || appId <= 0) {
            return null;
        }
        if (!generatingAppIds.add(appId)) {
            log.info("应用封面正在生成中，跳过重复请求, appId={}", appId);
            return null;
        }
        try {
            App app = appService.getById(appId);
            if (app == null) {
                log.warn("应用不存在，无法生成封面, appId={}", appId);
                return null;
            }
            CodeGenTypeEnum type = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
            if (type == null) {
                log.warn("未知代码生成类型，无法生成封面, appId={}, type={}", appId, app.getCodeGenType());
                return null;
            }
            File indexFile = resolveIndexFile(appId, type);
            if (indexFile == null || !indexFile.exists()) {
                log.warn("网站主页文件不存在，无法生成封面, appId={}, path={}", appId,
                        indexFile == null ? "null" : indexFile.getAbsolutePath());
                return null;
            }

            // 优先用本地 file://，避免依赖外部部署域名；失败再回退 HTTP 预览地址
            String screenshotUrl = null;
            String fileUrl = indexFile.toURI().toString();
            try {
                log.info("开始截取本地主页作为封面, appId={}, url={}", appId, fileUrl);
                screenshotUrl = screenshotService.generateAndUploadScreenshot(fileUrl);
            } catch (Exception e) {
                log.warn("本地文件截图失败，尝试 HTTP 预览地址, appId={}: {}", appId, e.getMessage());
            }
            if (StrUtil.isBlank(screenshotUrl)) {
                String httpUrl = buildHttpPreviewUrl(appId, type);
                log.info("开始截取 HTTP 预览页作为封面, appId={}, url={}", appId, httpUrl);
                screenshotUrl = screenshotService.generateAndUploadScreenshot(httpUrl);
            }
            if (StrUtil.isBlank(screenshotUrl)) {
                log.error("封面截图失败, appId={}", appId);
                return null;
            }

            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = appService.updateById(updateApp);
            if (!updated) {
                log.error("更新应用封面字段失败, appId={}", appId);
                return null;
            }
            log.info("应用封面生成成功, appId={}, cover={}", appId, screenshotUrl);
            return screenshotUrl;
        } catch (Exception e) {
            log.error("生成应用封面异常, appId={}: {}", appId, e.getMessage(), e);
            return null;
        } finally {
            generatingAppIds.remove(appId);
        }
    }

    private File resolveIndexFile(Long appId, CodeGenTypeEnum type) {
        String dirName = type.getValue() + "_" + appId;
        File root = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, dirName);
        if (type == CodeGenTypeEnum.VUE_PROJECT) {
            return new File(root, "dist" + File.separator + "index.html");
        }
        return new File(root, "index.html");
    }

    private String buildHttpPreviewUrl(Long appId, CodeGenTypeEnum type) {
        String baseUrl = String.format("%s/%s_%s/", AppConstant.CODE_PREVIEW_BASE_URL, type.getValue(), appId);
        if (type == CodeGenTypeEnum.VUE_PROJECT) {
            return baseUrl + "dist/index.html";
        }
        return baseUrl;
    }
}

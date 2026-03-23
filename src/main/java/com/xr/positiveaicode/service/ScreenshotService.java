package com.xr.positiveaicode.service;

public interface ScreenshotService {
    /**
     * 生成并上传网页截图
     *
     * @param webUrl 网页URL
     * @return 截图的URL
     */
    String generateAndUploadScreenshot(String webUrl);
}

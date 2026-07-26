package com.xr.positiveaicode.service;

/**
 * 应用封面生成服务
 */
public interface AppCoverService {

    /**
     * 根据已生成的网站主页截图，更新应用封面
     *
     * @param appId 应用 ID
     * @return 封面 URL，失败返回 null
     */
    String generateCover(Long appId);
}

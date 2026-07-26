package com.xr.positiveaicode.constant;

public interface AppConstant {

    /**
     * 精选应用的优先级
     */
    Integer GOOD_APP_PRIORITY = 99;

    /**
     * 默认应用优先级
     */
    Integer DEFAULT_APP_PRIORITY = 0;
    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署访问前缀（经 Static DeployedResourceController）
     */
    String CODE_DEPLOY_HOST = "http://localhost:8080/api/deployed";

    /**
     * 应用预览基础地址（用于生成后截取封面）
     */
    String CODE_PREVIEW_BASE_URL = "http://localhost:8080/api/static";

}

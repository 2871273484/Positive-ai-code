package com.xr.positiveaicode.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.xr.positiveaicode.model.dto.app.AppAddRequest;
import com.xr.positiveaicode.model.dto.app.AppQueryRequest;
import com.xr.positiveaicode.model.entity.App;
import com.xr.positiveaicode.model.entity.User;
import com.xr.positiveaicode.model.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author Positive
 */
public interface AppService extends IService<App> {

    /**
     * 获取应用视图对象
     *
     * @param app 应用
     * @return 应用视图对象
     */
    AppVO getAppVO(App app);

    /**
     * 获取查询条件
     *
     * @param appQueryRequest 应用查询请求
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    List<AppVO> getAppVOList(List<App> appList);

    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    String deployApp(Long appId, User loginUser);

    void generateAppScreenshotAsync(Long appId, String appUrl);

    /**
     * 同步生成应用封面（网站主页截图）
     *
     * @param appId 应用 ID
     * @return 封面 URL
     */
    String generateAppCover(Long appId);

    Long createApp(AppAddRequest appAddRequest, User loginUser);
}

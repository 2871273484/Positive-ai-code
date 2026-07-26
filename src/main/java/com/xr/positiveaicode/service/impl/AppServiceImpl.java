package com.xr.positiveaicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.xr.positiveaicode.ai.AiCodeGenTypeRoutingService;
import com.xr.positiveaicode.ai.AiCodeGenTypeRoutingServiceFactory;
import com.xr.positiveaicode.ai.CodeGenTypeRoutingHelper;
import com.xr.positiveaicode.ai.monitor.MonitorContext;
import com.xr.positiveaicode.ai.monitor.MonitorContextHolder;
import com.xr.positiveaicode.constant.AppConstant;
import com.xr.positiveaicode.core.builder.VueProjectBuilder;
import com.xr.positiveaicode.core.handler.StreamHandlerExecutor;
import com.xr.positiveaicode.exception.BusinessException;
import com.xr.positiveaicode.exception.ErrorCode;
import com.xr.positiveaicode.exception.ThrowUtils;
import com.xr.positiveaicode.langgraph4j.CodeGenWorkflow;
import com.xr.positiveaicode.mapper.AppMapper;
import com.xr.positiveaicode.service.AppCoverService;
import com.xr.positiveaicode.model.dto.app.AppAddRequest;
import com.xr.positiveaicode.model.dto.app.AppQueryRequest;
import com.xr.positiveaicode.model.entity.App;
import com.xr.positiveaicode.model.entity.User;
import com.xr.positiveaicode.model.enums.ChatHistoryMessageTypeEnum;
import com.xr.positiveaicode.model.enums.CodeGenTypeEnum;
import com.xr.positiveaicode.model.vo.AppVO;
import com.xr.positiveaicode.model.vo.UserVO;
import com.xr.positiveaicode.model.entity.AppCategory;
import com.xr.positiveaicode.service.AppCategoryService;
import com.xr.positiveaicode.service.AppService;
import com.xr.positiveaicode.service.ChatHistoryService;
import com.xr.positiveaicode.service.ScreenshotService;
import com.xr.positiveaicode.service.UserService;
import com.xr.positiveaicode.utils.AppNameUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author Positive
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Value("${code.deploy-host:http://localhost}")
    private String deployHost;
    @Resource
    private UserService userService;

    @Resource
    private CodeGenWorkflow codeGenWorkflow;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private AppCoverService appCoverService;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    @Resource
    private AppCategoryService appCategoryService;

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 旧数据可能用 prompt 前 12 字当名称，展示时归一成短标题
        appVO.setAppName(AppNameUtils.resolveDisplayName(app.getAppName(), app.getInitPrompt()));
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        fillCategoryFields(appVO, appCategoryService.listCategoryIdsByAppId(app.getId()));
        return appVO;
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long categoryId = appQueryRequest.getCategoryId();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
        // 按标签筛选：命中关联表中任意标签
        if (categoryId != null && categoryId > 0) {
            List<Long> appIds = appCategoryService.listAppIdsByCategoryId(categoryId);
            if (CollUtil.isEmpty(appIds)) {
                queryWrapper.eq("id", -1L);
            } else {
                queryWrapper.in("id", appIds);
            }
        }
        return queryWrapper;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        List<Long> appIds = appList.stream().map(App::getId).collect(Collectors.toList());
        Map<Long, List<Long>> appCategoryMap = appCategoryService.listCategoryIdsByAppIds(appIds);
        Set<Long> allCategoryIds = appCategoryMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNameMap = CollUtil.isEmpty(allCategoryIds)
                ? Map.of()
                : appCategoryService.listByIds(allCategoryIds).stream()
                .collect(Collectors.toMap(AppCategory::getId, AppCategory::getName, (a, b) -> a));
        return appList.stream().map(app -> {
            AppVO appVO = new AppVO();
            BeanUtil.copyProperties(app, appVO);
            appVO.setAppName(AppNameUtils.resolveDisplayName(app.getAppName(), app.getInitPrompt()));
            appVO.setUser(userVOMap.get(app.getUserId()));
            List<Long> cids = appCategoryMap.getOrDefault(app.getId(), List.of());
            if (CollUtil.isEmpty(cids) && app.getCategoryId() != null) {
                cids = List.of(app.getCategoryId());
            }
            List<String> names = cids.stream()
                    .map(categoryNameMap::get)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
            appVO.setCategoryIds(cids);
            appVO.setCategoryNames(names);
            if (CollUtil.isNotEmpty(cids)) {
                appVO.setCategoryId(cids.get(0));
                appVO.setCategoryName(names.isEmpty() ? null : names.get(0));
            }
            return appVO;
        }).collect(Collectors.toList());
    }

    private void fillCategoryFields(AppVO appVO, List<Long> categoryIds) {
        List<Long> cids = categoryIds == null ? List.of() : categoryIds;
        if (CollUtil.isEmpty(cids) && appVO.getCategoryId() != null) {
            cids = List.of(appVO.getCategoryId());
        }
        appVO.setCategoryIds(cids);
        if (CollUtil.isEmpty(cids)) {
            appVO.setCategoryNames(List.of());
            return;
        }
        Map<Long, String> nameMap = appCategoryService.listByIds(cids).stream()
                .collect(Collectors.toMap(AppCategory::getId, AppCategory::getName, (a, b) -> a));
        List<String> names = cids.stream()
                .map(nameMap::get)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        appVO.setCategoryNames(names);
        appVO.setCategoryId(cids.get(0));
        appVO.setCategoryName(names.isEmpty() ? null : names.get(0));
    }


    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限访问该应用，仅本人可以生成代码
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        // 5. 通过校验后，添加用户消息到对话历史
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 6. 设置监控上下文
        MonitorContextHolder.setContext(
                MonitorContext.builder()
                        .userId(loginUser.getId().toString())
                        .appId(appId.toString())
                        .build()
        );
        // 7. 走代码生成工作流（图片收集 → 提示词增强 → 路由 → 生成 → 构建）
        Flux<String> codeStream = codeGenWorkflow.executeForApp(appId, message, codeGenTypeEnum);
        // 8. 收集 AI 响应内容并在完成后记录到对话历史（封面由代码落盘事件触发）
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                .doFinally(signalType -> {
                    // 流结束时清理（无论成功/失败/取消）
                    MonitorContextHolder.clearContext();
                });

    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        // 7. Vue 项目特殊处理：执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 将 dist 目录作为部署源
            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
        }
        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 9. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 10. 构建应用访问 URL
        String appDeployUrl = String.format("%s/%s/", deployHost, deployKey);
        // 11. 异步生成截图并更新应用封面（优先截本地主页）
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;

    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL（兼容旧调用，实际优先截本地主页）
     */
    @Override
    public String generateAppCover(Long appId) {
        return appCoverService.generateCover(appId);
    }

    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(1000L);
                String cover = appCoverService.generateCover(appId);
                if (StrUtil.isBlank(cover) && StrUtil.isNotBlank(appUrl)) {
                    // 回退：直接截传入 URL（部署域名场景）
                    String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
                    if (StrUtil.isNotBlank(screenshotUrl)) {
                        App updateApp = new App();
                        updateApp.setId(appId);
                        updateApp.setCover(screenshotUrl);
                        this.updateById(updateApp);
                        log.info("应用封面（部署 URL）更新成功, appId={}, cover={}", appId, screenshotUrl);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("异步生成应用封面失败, appId={}: {}", appId, e.getMessage(), e);
            }
        });
    }


    /**
     * 删除应用时关联删除对话历史
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            log.error("删除应用关联对话历史失败: {}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称：与页面强相关的短标题（如「音乐网站」「计算器网站」）
        app.setAppName(AppNameUtils.deriveAppName(initPrompt));
        // 使用 AI 智能选择代码生成类型（失败时启发式兜底，避免创建中断）
        AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = CodeGenTypeRoutingHelper.routeSafely(routingService, initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

}

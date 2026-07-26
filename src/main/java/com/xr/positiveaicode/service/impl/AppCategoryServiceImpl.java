package com.xr.positiveaicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.xr.positiveaicode.exception.BusinessException;
import com.xr.positiveaicode.exception.ErrorCode;
import com.xr.positiveaicode.constant.AppConstant;
import com.xr.positiveaicode.mapper.AppCategoryMapper;
import com.xr.positiveaicode.mapper.AppCategoryRelMapper;
import com.xr.positiveaicode.mapper.AppMapper;
import com.xr.positiveaicode.model.dto.app.AppCategoryAddRequest;
import com.xr.positiveaicode.model.dto.app.AppCategoryUpdateRequest;
import com.xr.positiveaicode.model.entity.App;
import com.xr.positiveaicode.model.entity.AppCategory;
import com.xr.positiveaicode.model.entity.AppCategoryRel;
import com.xr.positiveaicode.model.vo.AppCategoryVO;
import com.xr.positiveaicode.service.AppCategoryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AppCategoryServiceImpl extends ServiceImpl<AppCategoryMapper, AppCategory>
        implements AppCategoryService {

    private static final int MAX_TAGS_PER_APP = 3;

    @Resource
    private AppMapper appMapper;

    @Resource
    private AppCategoryRelMapper appCategoryRelMapper;

    private static final Map<String, Integer> DEFAULT_CATEGORIES = new LinkedHashMap<>();

    static {
        DEFAULT_CATEGORIES.put("工具", 10);
        DEFAULT_CATEGORIES.put("网站", 20);
        DEFAULT_CATEGORIES.put("数据分析", 30);
        DEFAULT_CATEGORIES.put("活动页面", 40);
        DEFAULT_CATEGORIES.put("管理平台", 50);
        DEFAULT_CATEGORIES.put("用户应用", 60);
        DEFAULT_CATEGORIES.put("个人管理", 70);
        DEFAULT_CATEGORIES.put("游戏", 80);
    }

    private static final Map<String, String[]> KEYWORD_MAP = new LinkedHashMap<>();

    static {
        KEYWORD_MAP.put("游戏", new String[]{"游戏", "闯关", "puzzle", "game"});
        KEYWORD_MAP.put("数据分析", new String[]{"数据", "看板", "分析", "图表", "dashboard", "统计"});
        KEYWORD_MAP.put("管理平台", new String[]{"后台", "管理", "运营", "admin", "中台"});
        KEYWORD_MAP.put("活动页面", new String[]{"活动", "落地页", "促销", "报名", "landing", "营销"});
        KEYWORD_MAP.put("个人管理", new String[]{"待办", "笔记", "日记", "个人管理", "清单", "todo"});
        KEYWORD_MAP.put("用户应用", new String[]{"社区", "论坛", "社交", "聊天", "用户"});
        KEYWORD_MAP.put("工具", new String[]{"工具", "计算器", "转换", "生成器", "工具箱"});
        KEYWORD_MAP.put("网站", new String[]{"网站", "官网", "博客", "商城", "电商", "作品集", "主页"});
    }

    @Override
    public synchronized void ensureDefaultCategories() {
        long count = this.count();
        if (count > 0) {
            return;
        }
        List<AppCategory> list = new ArrayList<>();
        DEFAULT_CATEGORIES.forEach((name, sort) -> list.add(AppCategory.builder()
                .name(name)
                .sortOrder(sort)
                .build()));
        this.saveBatch(list);
    }

    @Override
    public List<AppCategoryVO> listCategoryVO() {
        ensureDefaultCategories();
        backfillFeaturedAppCategories();
        List<AppCategory> categories = this.list(QueryWrapper.create().orderBy("sortOrder", true).orderBy("id", true));
        return categories.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 给已精选但无任何标签的应用自动打 1 个标签
     */
    private void backfillFeaturedAppCategories() {
        List<App> apps = appMapper.selectListByQuery(
                QueryWrapper.create().eq("priority", AppConstant.GOOD_APP_PRIORITY));
        if (CollUtil.isEmpty(apps)) {
            return;
        }
        for (App app : apps) {
            List<Long> existing = listCategoryIdsByAppId(app.getId());
            if (CollUtil.isNotEmpty(existing)) {
                // 同步首标签到旧字段，便于兼容
                syncPrimaryCategoryId(app.getId(), existing.get(0));
                continue;
            }
            Long categoryId = app.getCategoryId();
            if (categoryId == null) {
                categoryId = resolveCategoryId(app.getAppName(), app.getInitPrompt());
            }
            if (categoryId == null) {
                continue;
            }
            replaceAppCategories(app.getId(), List.of(categoryId));
        }
    }

    @Override
    public Long addCategory(AppCategoryAddRequest request) {
        if (request == null || StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分类名称不能为空");
        }
        String name = request.getName().trim();
        if (name.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分类名称不能超过 20 字");
        }
        long exists = this.count(QueryWrapper.create().eq("name", name));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分类已存在");
        }
        AppCategory category = AppCategory.builder()
                .name(name)
                .sortOrder(request.getSortOrder() == null ? 100 : request.getSortOrder())
                .build();
        boolean ok = this.save(category);
        if (!ok) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "新增分类失败");
        }
        return category.getId();
    }

    @Override
    public boolean updateCategory(AppCategoryUpdateRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        AppCategory old = this.getById(request.getId());
        if (old == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "分类不存在");
        }
        AppCategory update = new AppCategory();
        update.setId(request.getId());
        if (StrUtil.isNotBlank(request.getName())) {
            String name = request.getName().trim();
            if (name.length() > 20) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "分类名称不能超过 20 字");
            }
            long exists = this.count(QueryWrapper.create().eq("name", name).ne("id", request.getId()));
            if (exists > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "分类已存在");
            }
            update.setName(name);
        }
        if (request.getSortOrder() != null) {
            update.setSortOrder(request.getSortOrder());
        }
        return this.updateById(update);
    }

    @Override
    public boolean deleteCategory(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        AppCategory old = this.getById(id);
        if (old == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "分类不存在");
        }
        return this.removeById(id);
    }

    @Override
    public Long resolveCategoryId(String appName, String initPrompt) {
        ensureDefaultCategories();
        String text = ((appName == null ? "" : appName) + " " + (initPrompt == null ? "" : initPrompt)).toLowerCase();
        String matchedName = "网站";
        outer:
        for (Map.Entry<String, String[]> entry : KEYWORD_MAP.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword.toLowerCase())) {
                    matchedName = entry.getKey();
                    break outer;
                }
            }
        }
        AppCategory category = this.getOne(QueryWrapper.create().eq("name", matchedName));
        return category == null ? null : category.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceAppCategories(Long appId, List<Long> categoryIds) {
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 无效");
        }
        List<Long> normalized = normalizeCategoryIds(categoryIds);
        appCategoryRelMapper.deleteByQuery(QueryWrapper.create().eq("appId", appId));
        if (CollUtil.isEmpty(normalized)) {
            syncPrimaryCategoryId(appId, null);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<AppCategoryRel> rows = normalized.stream()
                .map(cid -> AppCategoryRel.builder()
                        .appId(appId)
                        .categoryId(cid)
                        .createTime(now)
                        .build())
                .collect(Collectors.toList());
        for (AppCategoryRel row : rows) {
            appCategoryRelMapper.insert(row);
        }
        syncPrimaryCategoryId(appId, normalized.get(0));
    }

    @Override
    public List<Long> listCategoryIdsByAppId(Long appId) {
        if (appId == null) {
            return Collections.emptyList();
        }
        List<AppCategoryRel> rels = appCategoryRelMapper.selectListByQuery(
                QueryWrapper.create().eq("appId", appId).orderBy("id", true));
        if (CollUtil.isEmpty(rels)) {
            return Collections.emptyList();
        }
        return rels.stream().map(AppCategoryRel::getCategoryId).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public Map<Long, List<Long>> listCategoryIdsByAppIds(List<Long> appIds) {
        if (CollUtil.isEmpty(appIds)) {
            return Collections.emptyMap();
        }
        List<AppCategoryRel> rels = appCategoryRelMapper.selectListByQuery(
                QueryWrapper.create().in("appId", appIds).orderBy("id", true));
        if (CollUtil.isEmpty(rels)) {
            return Collections.emptyMap();
        }
        Map<Long, List<Long>> map = new LinkedHashMap<>();
        for (AppCategoryRel rel : rels) {
            map.computeIfAbsent(rel.getAppId(), k -> new ArrayList<>()).add(rel.getCategoryId());
        }
        return map;
    }

    @Override
    public List<Long> listAppIdsByCategoryId(Long categoryId) {
        if (categoryId == null) {
            return Collections.emptyList();
        }
        List<AppCategoryRel> rels = appCategoryRelMapper.selectListByQuery(
                QueryWrapper.create().eq("categoryId", categoryId));
        if (CollUtil.isEmpty(rels)) {
            return Collections.emptyList();
        }
        return rels.stream().map(AppCategoryRel::getAppId).distinct().collect(Collectors.toList());
    }

    private List<Long> normalizeCategoryIds(List<Long> categoryIds) {
        if (CollUtil.isEmpty(categoryIds)) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> unique = categoryIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unique.size() > MAX_TAGS_PER_APP) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "每个案例最多选择 3 个标签");
        }
        if (unique.isEmpty()) {
            return Collections.emptyList();
        }
        long validCount = this.count(QueryWrapper.create().in("id", unique));
        if (validCount != unique.size()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "存在无效的分类标签");
        }
        return new ArrayList<>(unique);
    }

    private void syncPrimaryCategoryId(Long appId, Long categoryId) {
        App update = new App();
        update.setId(appId);
        update.setCategoryId(categoryId);
        appMapper.update(update);
    }

    private AppCategoryVO toVO(AppCategory category) {
        AppCategoryVO vo = new AppCategoryVO();
        BeanUtil.copyProperties(category, vo);
        return vo;
    }
}

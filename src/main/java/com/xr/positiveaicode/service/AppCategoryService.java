package com.xr.positiveaicode.service;

import com.mybatisflex.core.service.IService;
import com.xr.positiveaicode.model.dto.app.AppCategoryAddRequest;
import com.xr.positiveaicode.model.dto.app.AppCategoryUpdateRequest;
import com.xr.positiveaicode.model.entity.AppCategory;
import com.xr.positiveaicode.model.vo.AppCategoryVO;

import java.util.List;
import java.util.Map;

public interface AppCategoryService extends IService<AppCategory> {

    /**
     * 确保默认分类存在
     */
    void ensureDefaultCategories();

    /**
     * 按排序返回全部分类
     */
    List<AppCategoryVO> listCategoryVO();

    Long addCategory(AppCategoryAddRequest request);

    boolean updateCategory(AppCategoryUpdateRequest request);

    boolean deleteCategory(Long id);

    /**
     * 根据应用名称/提示词推断分类 id
     */
    Long resolveCategoryId(String appName, String initPrompt);

    /**
     * 覆盖保存应用标签（最多 3 个）
     */
    void replaceAppCategories(Long appId, List<Long> categoryIds);

    /**
     * 查询应用的分类 id 列表
     */
    List<Long> listCategoryIdsByAppId(Long appId);

    /**
     * 批量查询：appId -> categoryIds
     */
    Map<Long, List<Long>> listCategoryIdsByAppIds(List<Long> appIds);

    /**
     * 拥有某分类标签的应用 id
     */
    List<Long> listAppIdsByCategoryId(Long categoryId);
}


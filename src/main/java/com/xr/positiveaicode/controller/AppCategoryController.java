package com.xr.positiveaicode.controller;

import com.xr.positiveaicode.annotation.AuthCheck;
import com.xr.positiveaicode.common.BaseResponse;
import com.xr.positiveaicode.common.DeleteRequest;
import com.xr.positiveaicode.common.ResultUtils;
import com.xr.positiveaicode.constant.UserConstant;
import com.xr.positiveaicode.exception.ErrorCode;
import com.xr.positiveaicode.exception.ThrowUtils;
import com.xr.positiveaicode.model.dto.app.AppCategoryAddRequest;
import com.xr.positiveaicode.model.dto.app.AppCategoryUpdateRequest;
import com.xr.positiveaicode.model.vo.AppCategoryVO;
import com.xr.positiveaicode.service.AppCategoryService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 案例广场分类标签
 */
@RestController
@RequestMapping("/app/category")
public class AppCategoryController {

    @Resource
    private AppCategoryService appCategoryService;

    /**
     * 公开：案例广场分类列表
     */
    @GetMapping("/list")
    public BaseResponse<List<AppCategoryVO>> listCategories() {
        return ResultUtils.success(appCategoryService.listCategoryVO());
    }

    /**
     * 管理员新增分类
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addCategory(@RequestBody AppCategoryAddRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(appCategoryService.addCategory(request));
    }

    /**
     * 管理员更新分类
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateCategory(@RequestBody AppCategoryUpdateRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(appCategoryService.updateCategory(request));
    }

    /**
     * 管理员删除分类
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteCategory(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(appCategoryService.deleteCategory(deleteRequest.getId()));
    }
}

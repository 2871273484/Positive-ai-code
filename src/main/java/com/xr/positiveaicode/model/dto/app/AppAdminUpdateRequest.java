package com.xr.positiveaicode.model.dto.app;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AppAdminUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用封面
     */
    private String cover;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 案例广场分类 id（兼容旧字段，等同于 categoryIds 第一个）
     */
    private Long categoryId;

    /**
     * 案例广场标签 id 列表（最多 3 个；传空列表表示清空）
     */
    private List<Long> categoryIds;

    private static final long serialVersionUID = 1L;
}

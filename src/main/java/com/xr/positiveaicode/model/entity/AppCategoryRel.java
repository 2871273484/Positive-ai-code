package com.xr.positiveaicode.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用与案例分类关联（一应用最多 3 个标签）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_category_rel")
public class AppCategoryRel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("appId")
    private Long appId;

    @Column("categoryId")
    private Long categoryId;

    @Column("createTime")
    private LocalDateTime createTime;
}

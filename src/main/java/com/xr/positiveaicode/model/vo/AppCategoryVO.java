package com.xr.positiveaicode.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AppCategoryVO implements Serializable {

    private Long id;

    private String name;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}

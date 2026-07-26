package com.xr.positiveaicode.model.dto.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppCategoryUpdateRequest implements Serializable {

    private Long id;

    private String name;

    private Integer sortOrder;

    private static final long serialVersionUID = 1L;
}

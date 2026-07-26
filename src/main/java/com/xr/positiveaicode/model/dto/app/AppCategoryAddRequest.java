package com.xr.positiveaicode.model.dto.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppCategoryAddRequest implements Serializable {

    private String name;

    private Integer sortOrder;

    private static final long serialVersionUID = 1L;
}

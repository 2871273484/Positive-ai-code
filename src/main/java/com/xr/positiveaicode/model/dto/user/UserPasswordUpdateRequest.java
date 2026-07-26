package com.xr.positiveaicode.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户修改密码请求
 */
@Data
public class UserPasswordUpdateRequest implements Serializable {

    /**
     * 原密码
     */
    private String oldPassword;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认新密码
     */
    private String checkPassword;

    private static final long serialVersionUID = 1L;
}

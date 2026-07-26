package com.xr.positiveaicode.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户更新资料请求
 */
@Data
public class UserProfileUpdateRequest implements Serializable {

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像 URL
     */
    private String userAvatar;

    private static final long serialVersionUID = 1L;
}

package com.xr.positiveaicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.xr.positiveaicode.exception.BusinessException;
import com.xr.positiveaicode.exception.ErrorCode;
import com.xr.positiveaicode.model.dto.user.UserQueryRequest;
import com.xr.positiveaicode.model.entity.User;
import com.xr.positiveaicode.mapper.UserMapper;
import com.xr.positiveaicode.model.enums.UserRoleEnum;
import com.xr.positiveaicode.model.vo.LoginUserVO;
import com.xr.positiveaicode.model.vo.UserVO;
import com.xr.positiveaicode.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.xr.positiveaicode.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 * @author Positive
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService{

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 检查是否重复
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 3. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过段");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过段");
        }
        // 2. 密码加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 3. 查询用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null){
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "positive";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询（获取最新）
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .eq("userRole", userRole)
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public LoginUserVO updateMyProfile(String userName, String userAvatar, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (StrUtil.isBlank(userName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "昵称不能为空");
        }
        String trimmedName = userName.trim();
        if (trimmedName.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "昵称不能超过 20 个字");
        }
        User update = new User();
        update.setId(loginUser.getId());
        update.setUserName(trimmedName);
        if (userAvatar != null) {
            String avatar = userAvatar.trim();
            if (StrUtil.isNotBlank(avatar)) {
                if (avatar.length() > 512) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "头像链接过长");
                }
                if (!avatar.startsWith("http://") && !avatar.startsWith("https://")) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "头像须为 http/https 链接");
                }
                update.setUserAvatar(avatar);
            } else {
                // 传空字符串则清空头像
                update.setUserAvatar("");
            }
        }
        boolean result = this.updateById(update);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新资料失败");
        }
        User latest = this.getById(loginUser.getId());
        request.getSession().setAttribute(USER_LOGIN_STATE, latest);
        return getLoginUserVO(latest);
    }

    @Override
    public boolean updateMyPassword(String oldPassword, String newPassword, String checkPassword,
                                    HttpServletRequest request) {
        if (StrUtil.hasBlank(oldPassword, newPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (oldPassword.length() < 8 || newPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于 8 位");
        }
        if (!newPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的新密码不一致");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码不能与原密码相同");
        }

        User loginUser = getLoginUser(request);
        String encryptOld = getEncryptPassword(oldPassword);
        if (!encryptOld.equals(loginUser.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "原密码错误");
        }

        User update = new User();
        update.setId(loginUser.getId());
        update.setUserPassword(getEncryptPassword(newPassword));
        boolean result = this.updateById(update);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "修改密码失败");
        }
        User latest = this.getById(loginUser.getId());
        request.getSession().setAttribute(USER_LOGIN_STATE, latest);
        return true;
    }
}

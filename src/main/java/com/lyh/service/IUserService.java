package com.lyh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lyh.dto.LoginFormDTO;
import com.lyh.dto.Result;
import com.lyh.entity.User;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone);

    Result login(LoginFormDTO loginForm);
}

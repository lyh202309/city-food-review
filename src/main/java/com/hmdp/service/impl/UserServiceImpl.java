package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //向这个手机号发验证码
        //验证码暂存在session里面，其实还涉及到验证码失效问题，这里先不弄？
        //画流程图还真的挺重要的，有时候不知道干啥就是因为没画流程图
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确！");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //存到session里面
        session.setAttribute("code",code);
        session.setAttribute("phone",phone);
        //假装发了
        log.debug("验证码测试：验证码为"+code);
        //返回成功就行了
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = (String) session.getAttribute("phone");
        String code = (String) session.getAttribute("code");
        if(!loginForm.getPhone().equals(phone) || !loginForm.getCode().equals(code)) {
            return Result.fail("手机号不一致或验证码错误");
        }
        User user = query().eq("phone",phone).one();
        if(user == null) {
            user = createWithPhone(phone);
        }
        session.setAttribute("user",user);
        //验证码没用了可以删了
        session.removeAttribute("code");
        return Result.ok();
    }

    private User createWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}

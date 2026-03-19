package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
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
    @Autowired
    StringRedisTemplate stringRedisTemplate;


    @Override
    public Result sendCode(String phone) {
        //向这个手机号发验证码
        //验证码暂存在session里面，其实还涉及到验证码失效问题，这里先不弄？
        //画流程图还真的挺重要的，有时候不知道干啥就是因为没画流程图
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确！");
        }
        //生成验证码，其实这里的魔法值也要改
        String code = RandomUtil.randomNumbers(6);
        //存redis里面了，而且设置有效时间
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY +phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //假装发了
        log.debug("验证码测试：验证码为"+code);
        //返回成功就行了
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        //1.验证刚才的手机和验证码对不对
        String phone = loginForm.getPhone();
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if(code == null || !loginForm.getCode().equals(code)) {
            //2.手机号变了查不到或者验证码不对
            return Result.fail("验证码错误");
        }
        //3.根据手机号查询用户，这里需要保证一个手机一个用户，没有的话就创建一个
        User user = query().eq("phone",phone).one();
        if(user == null) {
            //数据库没有这个电话对应的用户，创建一个用户
            user = createWithPhone(phone);
        }
        //4.为了方便会话内的数据共享，我们把同一个会话的信息存到redis里面
        //4.1同一个会话使用token进行标识,这里其实是有可能key重复的，不过简便起见我们不处理这个问题了
        String token = RandomUtil.randomString(10);
        String tokenKey = LOGIN_USER_KEY + token;
        //4.2把用户对象转变为一个Map<String, String>
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        //这里存的就是Map<String, String>了
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().
                        setIgnoreNullValue(false).
                        setFieldValueEditor((key , value) -> value.toString()));
        //4.3存到redis里面
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User createWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}

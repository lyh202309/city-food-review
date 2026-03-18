package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //拦截器，逻辑为：能够从请求中的session中获得有有效的用户信息（不像前面的用jwt判断什么的）
        HttpSession session = request.getSession();
        UserDTO user = (UserDTO) session.getAttribute("user");
        //只有正确登录，session才有可能有用户信息
        if(user == null) {
            //！！！给点信息
            response.setStatus(401);
            return false;
        }
        //有用户信息那还说啥了，通过吧
        //直接存线程里面，以后同一个会话内所有的请求的服务层以及控制层全都能拿到这个信息
        UserHolder.saveUser(user);
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

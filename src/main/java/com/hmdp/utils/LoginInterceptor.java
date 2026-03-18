package com.hmdp.utils;

import com.hmdp.entity.User;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //拦截器，逻辑为：能够从请求中的session中获得有有效的用户信息（不像前面的用jwt判断什么的）
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");
        //只有正确登录，session才有可能有用户信息
        if(user == null) {
            //！！！给点信息
            response.setStatus(401);
            return false;
        }
        //有用户信息那还说啥了，通过吧
        return true;
    }
}

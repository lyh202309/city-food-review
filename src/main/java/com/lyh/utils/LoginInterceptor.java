package com.lyh.utils;

import com.lyh.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserDTO userDTO = UserHolder.getUser();
        if(userDTO == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}

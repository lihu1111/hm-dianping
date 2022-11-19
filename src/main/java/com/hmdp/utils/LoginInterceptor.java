package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Date 2022/11/18 18:21
 * @Author lihu
 */
public class LoginInterceptor implements HandlerInterceptor {
//    private StringRedisTemplate stringRedisTemplate;
//
//    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

//        // 1. 获取session
//        HttpSession session = request.getSession();
//        // 2. 获取 user 对象
//        Object user = session.getAttribute("user");
//        // 2.1. 如果没有登录， 拦截
//        if(user == null) {
//            response.setStatus(401);
//            return false;
//        }
//        // 2.2 如果存在， 保存到ThreadLocal
//        UserHolder.saveUser((UserDTO) user);

       // 是否放行
        UserDTO user = UserHolder.getUser();
        if(user == null) {
            response.setStatus(401);
            return false;
        }
        // 放行
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}

package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Date 2022/11/18 18:21
 * @Author lihu
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

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
        //  1. 获取token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlankIfStr(token)) {
            // 没有获取到 token， 即没有登陆
            return true;
        }
        // 2. 获取redis用户
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        // 2.0 判断是否为空
        if(map.isEmpty()) {
            return true;
        }
        // 2.1 将hash对象转换成java对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
        // 3. 存储
        UserHolder.saveUser(userDTO);
        // 4. 刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
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

package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    IUserService userService;

    @Override
    public Result follow(Long id, Boolean isFollow) {
        // 1. 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        // 2. 判断是关注对方用户还是取关
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(user.getId());
            follow.setFollowUserId(id);
            boolean isSuccess = save(follow);
            // 2.1 如果关注则加入redis
            if (isFollow) {
                String key = "follows:" + user.getId();
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }

        } else {
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("follow_user_id", id)
                    .eq("user_id", user.getId()));
            if (isSuccess) {
                String key = "follows:" + user.getId();
                stringRedisTemplate.opsForSet().remove(key);
            }
        }
        return Result.ok();
    }

    @Override
    public Result follow(Long id) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("follow_user_id", id)
                .eq("user_id", userId)
                .count();
        return Result.ok(count > 0);
    }

    @Override
    public Result commonConcern(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + id;
        String key2 = "follows:" + userId;
        Set<String> set =
                stringRedisTemplate.opsForSet().intersect(key1, key2);
        List<Long> collect = set
                .stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        List<UserDTO> users = userService
                .listByIds(collect)
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}

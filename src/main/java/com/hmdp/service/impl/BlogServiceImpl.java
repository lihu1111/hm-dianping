package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    UserServiceImpl userService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        // 1. 根据 id 查询
        Blog blog = getById(id);
        if (blog == null) return Result.fail("笔记不存在");
        // 查询发布该 blog 的用户
        queryBlogUser(blog);
        isBlogLiked(getById(id));
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {

        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录
            return;
        }
        String userId = user.getId().toString();
        String key = "blog:liked:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId);
        blog.setIsLike(score != null);

    }

    @Override
    public Result likeBlog(Long id) {
        // 1. 判断当前用户是否已经点赞
        String userId = UserHolder.getUser().getId().toString();
        String key = "blog:liked:" + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId);
        if (score == null) {
            // 3. 如果不是则 +1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId, System.currentTimeMillis());
            }
            // TODO 将 blogId +1 存入消息队列， 运用消息队列通知其他线程进行数据库操作
        } else {
            // 2. 如果是 则 -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId);
            }
            // TODO 将 blogId -1 存入消息队列，运用消息队列通知其他线程进行数据库操作
        }
        return Result.ok();
    }

    @Override
    public Result blogLikes(Long id) {
        String key = "blog:liked:" + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.EMPTY_LIST);
        }
        List<Long> userIds = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOList = userService
                .listByIds(userIds).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
//        Page<User> page = new Page<>();
//        List<User> userList = new ArrayList<>();
//        range.forEach(userIds -> {
//            User user = userService.getById(userIds);
//            userList.add(user);
//        });
//        page.setRecords(userList);
        return Result.ok(userDTOList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (isSuccess) {
            // 找到博主所有粉丝
            List<Follow> followUsers =
                    followService
                            .query()
                            .eq("follow_user_id", user.getId()).list();
            // 将笔记推送给所有粉丝
            for (Follow follow : followUsers) {
                // 获取粉丝 id
                Long userId = follow.getUserId();
                // 创建收件箱
                String key = "feed:" + userId;
                stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
            }
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}

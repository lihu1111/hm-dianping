package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopById(Long id) {
//        // 1. 查询缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
//        // 1.1 不为空则返回
//        if (StrUtil.isNotBlank(shopJson)) {
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return Result.ok(shop);
//        }
//        // 如果是空值""， 则说明是防止数据库穿透的，数据库里没有这个信息，不用查数据库了
//        if (shopJson != null) {
//            return Result.fail("店铺信息不存在！");
//        }
//        // 2. 没有找到 查询数据库
//        Shop shop = getById(id);
//        // 3. 数据库如果没有， 返回错误
//        if (shop == null) {
//            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return Result.fail("店铺不存在！");
//        }
//        // 4. 数据库找到， 写入缓存
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop)
//                , RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        // 5. 返回数据
//        return Result.ok(shop);
//        Shop shop = queryWithPassThrough(id);
        // 缓存穿透， 解决方案1：互斥锁，热点数据失效，防止多个线程像数据库发送请求写缓存
        Shop shop = queryWithMutex(id);
//        Shop shop = queryWithLogicExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 缓存穿透
     *
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        // 1. 查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        // 1.1 不为空则返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 如果是空值""， 则说明是防止数据库穿透的，数据库里没有这个信息，不用查数据库了
        if (shopJson != null) {
            return null;
        }
        // 2. 没有找到 查询数据库
        Shop shop = getById(id);
        // 3. 数据库如果没有， 返回错误
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 4. 数据库找到， 写入缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop)
                , RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 5. 返回数据
        return shop;
    }

    /**
     * 缓存击穿
     * 1. 互斥锁解决方案
     *
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        // 1. 查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        // 1.1 不为空则返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 如果是空值""， 则说明是防止数据库穿透的，数据库里没有这个信息，不用查数据库了
        if (shopJson != null) {
            return null;
        }
        // 2 实现缓存重建
        // 2.1 尝试获取锁
        Shop shop = null;
        try {
            boolean lock = tryLock(RedisConstants.LOCK_SHOP_KEY + id);

            if (!lock) {
                // 2.3 如果失败 则休眠，等待，重复查询
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 2.2 如果成功 则查询数据库写入缓存
            shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
            if (StrUtil.isNotBlank(shopJson)) {
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            // 如果是空值""， 则说明是防止数据库穿透的，数据库里没有这个信息，不用查数据库了
            if (shopJson != null) {
                return null;
            }

            // 2. 没有找到 查询数据库
            shop = getById(id);
            // 模拟重建延迟
            Thread.sleep(200);
            // 3. 数据库如果没有， 返回错误
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 4. 数据库找到， 写入缓存
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop)
                    , RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unlock(RedisConstants.LOCK_SHOP_KEY + id);
        }
        // 5. 返回数据
        return shop;
    }

    /**
     * 获取锁
     *
     * @param key 锁
     * @return 锁是否获取成功
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 将热点数据提前缓存进redis
     *
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        // 逻辑过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    /**
     * 缓存击穿
     * 2. 逻辑过期
     *
     * @param id
     * @return
     */

    public Shop queryWithLogicExpire(Long id) {
        // 1. 查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        // 1.1 不为空则返回
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // 逻辑过期
        // .1 命中 把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        // .2 判断时间是否过期
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = BeanUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())) {
            // .3 未过期 直接返回店铺信息
            return shop;
        }
        // .4 过期 缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        // .5 获取互斥锁
        boolean lock = tryLock(lockKey);
        // .6 判断取锁是否成功
        if(lock) {
            // .7 成功 开启线程更新缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }

            });
        }
        // .8 失败 返回店铺信息
        return shop;
    }

    @Override
    @Transactional // 事务
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            Result.fail("店铺id不能为空");
        }
        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}

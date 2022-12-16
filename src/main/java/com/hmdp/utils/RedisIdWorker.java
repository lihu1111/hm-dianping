package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Date 2022/11/21 9:23
 * @Author lihu
 */
@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    // 序列号位数
    private static final int COUNT_BITS = 32;


    StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 1. 生成时间戳
        LocalDateTime dateTime = LocalDateTime.now();
        long epochSecond = dateTime.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = epochSecond - BEGIN_TIMESTAMP;
        // 2. 生成序列号
        // 2.1 获取当前日期
        String date = dateTime.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2 自增 2 的 64 次方
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);// 如果 key 相同， 则会导致超过 increment以key为底，key不变则一直递增
        // 3. 拼接返回
        return timeStamp << COUNT_BITS | count;
    }
}

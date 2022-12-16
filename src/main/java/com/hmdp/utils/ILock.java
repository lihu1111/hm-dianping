package com.hmdp.utils;

/**
 * @Date 2022/12/6 18:34
 * @Author lihu
 */
public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSec 锁的持有时间，到期自动过期
     * @return true表示获取成功， false表示获取失败
     */
     boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}

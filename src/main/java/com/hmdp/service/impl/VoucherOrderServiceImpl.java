package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.support.ExecutorServiceAdapter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    ISeckillVoucherService seckillVoucherService;

    /**
     * 全局唯一 id 生成器
     */
    @Autowired
    RedisIdWorker redisIdWorker;

    @Autowired
    StringRedisTemplate stringRedisTemplate;
    /**
     * 阻塞队列
     */
    private BlockingQueue<VoucherOrder> blockingQueue = new ArrayBlockingQueue<>(1024 * 1024);

//    ExecutorService executorService =


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 1. 执行脚本，判断是否拥有下单资格
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.EMPTY_LIST,
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        // 2. 判断结果是否为0
        if(r != 0) {
            // 2.1 不为0 则没有秒杀资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        } else {
            // 2.2 为0 将秒杀券id 用户id 订单id 写入阻塞队列
            long orderId = redisIdWorker.nextId("order");
            // TODO  保存阻塞队列
            return Result.ok();
        }
    }









//    public Result seckillVoucher(Long voucherId) {
//        // 1. 是否存在
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        // 2. 是否开始
//        LocalDateTime beginTime = seckillVoucher.getBeginTime();
//        LocalDateTime now = LocalDateTime.now();
//        if (beginTime.isAfter(now)) {
//            // 没开始
//            return Result.fail("秒杀活动还没开始");
//        }
//        // 3. 是否结束
//        LocalDateTime endTime = seckillVoucher.getEndTime();
//        if (endTime.isBefore(now)) {
//            return Result.fail("秒杀活动已经结束");
//        }
//        // 4. 库存是否充足
//        Integer stock = seckillVoucher.getStock();
//        if (stock < 0) {
//            return Result.fail("库存不足");
//        }
//        UserDTO user = UserHolder.getUser();
//        Long userId = user.getId();
////        synchronized (userId.toString().intern()) {
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId, stock);
////        }
//        /**
//         * 一个用户一个种类的优惠券
//         */
//        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId + ":" + voucherId);
//        boolean lock = simpleRedisLock.tryLock(10L);
//        if (!lock) {
//            return Result.fail("你已经购买过了");
//        }
//
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId, stock);
//        } finally {
//            simpleRedisLock.unlock();
//        }
//    }

    @Transactional
    public Result createVoucherOrder(Long voucherId, Integer stock) {
        //5. 一人一单
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        // 5.1 查询 id
        Integer count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        // 5.2 如果》 0
        if (count > 0) {
            return Result.fail("你已经买过这个券了");
        }
        // 6. 扣减库存，创建订单
        boolean update = seckillVoucherService
                .update()
                .set("stock", stock - 1)
                .eq("voucher_id", voucherId)
                //                .eq("stock", seckillVoucher.getStock())
                .gt("stock", 0)
                .update();
        if (!update) {
            return Result.fail("库存不足");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1 订单 id
        long id = redisIdWorker.nextId("order");
        voucherOrder.setId(id);
        // 6.2 优惠券 id
        voucherOrder.setVoucherId(voucherId);
        // 6.3 用户 id

        voucherOrder.setUserId(userId);
        save(voucherOrder);
        // 6. 返回id
        return Result.ok(id);
    }

}

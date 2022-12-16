package com.hmdp;

import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    IVoucherService voucherService;
    @Autowired
    ShopServiceImpl shopService;
    @Autowired
    RedisIdWorker redisIdWorker;
    private ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Test
    public void test1() throws InterruptedException {
        shopService.saveShop2Redis(1L, 2L);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        // 每一个线程都要生成 500 个 id 打印出来
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id=" + id);
            }
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(task);

        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println(end - start);
    }
    @Test
    void test3() {
        List<Voucher> shop_id = voucherService.query().eq("shop_id", 1).list();
        for (Voucher voucher : shop_id) {
            System.out.println(voucher);
        }
    }
}

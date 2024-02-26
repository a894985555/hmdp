package com.hmdp;

import com.hmdp.entity.RedisIdWorker;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherService;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class test {

    @Autowired
    IShopService shopService;
    @Autowired
    IVoucherService voucherService;

    @Autowired
    RedisIdWorker redisIdWorker;

    @Autowired
    RedissonClient redissonClient;

    private static final ExecutorService es = Executors.newFixedThreadPool(10);

    Object mutex = new Object();

    //编写一个main方法
    public static void main(String[] args) {
        long suffix = (1L<<32) - 1;
        long prefix = (1L<<63) - 1;
        prefix = prefix - suffix;
        System.out.println(Long.toBinaryString(suffix));
        System.out.println(Long.toBinaryString(prefix));

        Long a = 1L;
        System.out.println(a.doubleValue());
    }

    public static Integer get(int n) {
//        System.out.println();
        HashMap<Object, Object> hashMap = new HashMap<>();
        hashMap.put("a","2023-06-15 10:09:08");
        System.out.println(hashMap.get("a"));
        try {

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.out.println("over"+ n);
        }

        return n;
    }

    @Test
    void testSaveShop() {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void testIdWorker() throws InterruptedException {

        // 一开始latch为300
        CountDownLatch latch = new CountDownLatch(10000);

        // 保证线程安全的arraylist
        Vector<Long> list = new Vector<>();

        // 子线程任务
        Runnable task = () -> {
            for (int i = 0; i < 200; i++) {
                long id = redisIdWorker.nextId("order");
                list.add(Long.valueOf(id));
            }

            // 执行完latch减一
            latch.countDown();
        };
        long begin = System.currentTimeMillis();

        // 主线程开启1000个子线程
        for (int i = 0; i < 10000; i++) {
            es.submit(task);
        }

        // 只有latch=0，主线程取消阻塞
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));

        // 62-32位，共31位
        long suffix = (1L<<32) - 1;

        // 31-0位，共32位
        long prefix = (1L<<63) - 1;
        prefix = prefix - suffix;
        System.out.println(Long.toBinaryString(suffix));
        System.out.println(Long.toBinaryString(prefix));
        HashMap<Long, Long> hashMap = new HashMap<>();
        System.out.println(list.size());
        for (Long item : list) {
            Long key = item & prefix;
            if (hashMap.get(key) != null) {
                Long value = hashMap.get(key);
                hashMap.put(item & prefix, value+1);
            } else {
                hashMap.put(item & prefix, 1L);
            }
        }
        // 计算某个时间戳下的并发量，计算出来redis每秒最大的并发处理量在10w左右
        for (Map.Entry<Long, Long> longLongEntry : hashMap.entrySet()) {
            System.out.println(longLongEntry.getKey()+":"+longLongEntry.getValue());
        }
    }

    @Test
    void insertSeckill() {
        Voucher voucher = new Voucher();
        voucher.setShopId(1L);
        voucher.setTitle("150元代金券");
        voucher.setSubTitle("周一至周日均可使用");
        voucher.setPayValue(7500L);
        voucher.setActualValue(15000L);
        voucher.setType(1);
        voucher.setStatus(1);
        voucher.setStock(100);
        voucher.setBeginTime(LocalDateTime.now());
        voucher.setEndTime(LocalDateTime.now().plusHours(8));
        voucherService.addSeckillVoucher(voucher);
    }

    @Test
    void testRedisson() throws Exception{
        //获取锁(可重⼊)，指定锁的名称
        RLock lock = redissonClient.getLock("anyLock");
        //尝试获取锁，参数分别是：获取锁的最⼤等待时间(期间会重试)，锁⾃动释放时间，时间单位
        boolean isLock = lock.tryLock(1,10, TimeUnit.SECONDS);
        //判断获取锁成功
        if(isLock) {
            try {
                System.out.println("执⾏业务");
            } finally {
                //释放锁
                lock.unlock();
            }
        }
    }
}



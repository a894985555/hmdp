package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.PageHelper;
import com.hmdp.entity.RedisIdWorker;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisConstants;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
public class test {

    @Autowired
    IUserService userService;
    @Autowired
    IShopService shopService;
    @Autowired
    IVoucherService voucherService;

    @Autowired
    RedisIdWorker redisIdWorker;

    @Autowired
    RedissonClient redissonClient;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    UserMapper userMapper;

    private static final ExecutorService es = Executors.newFixedThreadPool(10);

    Object mutex = new Object();

    @Test
    public void test() {
        User A = new User();
        A.setNickName("zc");
        A.setPhone("123");

        User B = new User();
        B.setNickName("zccc");
        B.setPhone(null);

        BeanUtils.copyProperties(B,A);
        System.out.println(A);
//        System.out.println(users);
    }

    //编写一个main方法
    public static void main(String[] args) {
        long suffix = (1L<<32) - 1;
        long prefix = (1L<<63) - 1;
        prefix = prefix - suffix;
        System.out.println(Long.toBinaryString(suffix));
        System.out.println(Long.toBinaryString(prefix));
        Long i = Long.valueOf("1073742320");
        LocalDateTime now = LocalDateTime.now();
        System.out.println(Long.toBinaryString(i));
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

    @Test
    void loadShopData() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId⼀致的放到⼀个集合
        Map<Long, List<Shop>> map =
                list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写⼊Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            // 3.3.写⼊redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(),
                shop.getY()), shop.getId().toString());
            }
        }
    }

    @Test
    void testHyperLogLog() {
        String[] users = new String[1000];
        int index = 0;
        for (int i=1;i<=100000000;i++) {
            users[index++] = "user_" + i;
            if (i%1000==0) {
                index = 0;
                stringRedisTemplate.opsForHyperLogLog().add("hll",users);
            }
        }
        Long size = stringRedisTemplate.opsForHyperLogLog().size("hll");
        System.out.println(size);
    }

}



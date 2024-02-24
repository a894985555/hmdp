package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtils {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;


    public boolean tryLock(String key) {
        Boolean ifAbsent = redisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        System.out.println("上锁");
        return ifAbsent;
    }

    public void unlock(String key) {
        redisTemplate.delete(key);
        System.out.println("解锁");
    }
}

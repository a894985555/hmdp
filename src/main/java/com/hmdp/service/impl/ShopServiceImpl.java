package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.ssh.JschUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Transactional
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisUtils redisUtils;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Shop getShopByIdUseCache(Long id) {
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopLockKey = RedisConstants.LOCK_SHOP_KEY + id;
        String shopJson = redisTemplate.opsForValue().get(shopKey);
        Shop shop = null;

        // 1.先查缓存，有直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 2.没有缓存，查数据库,需要获取互斥锁
        if (!redisUtils.tryLock(shopLockKey)) {
            return null;
        }

        // 3.查询数据库
        shop = getById(id);
        String shopValue = "";
        if (shop != null) {
            shopValue = JSONUtil.toJsonStr(shop);
        }

        // 4.把数据库的数据写入缓存，并设置超时时间30分钟, 如果是空的也需要缓存，防止缓存击穿
        redisTemplate.opsForValue().set(shopKey, shopValue, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 5.释放锁
        redisUtils.unlock(shopLockKey);

        // 6.返回数据给客户端
        return shop;
    }

    @Override
    public Shop queryWithLogicalExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String json = redisTemplate.opsForValue().get(key);

        // 缓存预热
        if (StrUtil.isBlank(json)) {
            return saveShop2Redis(id, 10L);
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 未过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }

        // 过期，开启新线程来重建缓存
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        if (redisUtils.tryLock(lockKey)) {


            // 匿名函数写法
//            CACHE_REBUILD_EXECUTOR.submit(new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        saveShop2Redis(id, 20L);
//                    } catch (Exception e) {
//                        throw new RuntimeException(e);
//                    } finally {
//                        redisUtils.unlock(lockKey);
//                    }
//                }
//            });


            // lambda表达式写法
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    redisUtils.unlock(lockKey);
                }
            });
        }
        return shop;
    }

    @Override
    public Shop saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
        return shop;
    }

    @Override
    public Shop saveShopUseCache(Shop shop) {
        save(shop);
        return shop;
    }

    @Override
    public Shop updateShopUseCache(Shop shop) {
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        String shopKey = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        redisTemplate.delete(shopKey);

        return shop;
    }


}

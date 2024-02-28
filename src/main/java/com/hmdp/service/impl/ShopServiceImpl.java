package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.ssh.JschUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisUtils;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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

    @Autowired
    StringRedisTemplate stringRedisTemplate;

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
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 2.计算分⻚参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 3.查询redis、按照距离排序、分⻚。结果：shopId、distance
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                stringRedisTemplate.opsForGeo()
                        .search(
                                key,
                                GeoReference.fromCoordinate(x, y),
                                new Distance(5000),
                                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                        );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下⼀⻚了，结束
            return Result.ok(Collections.emptyList());
        }

        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());

        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr +
                ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        // 6.返回
        return Result.ok(shops);
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

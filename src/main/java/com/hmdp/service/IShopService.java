package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.Shop;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Shop getShopByIdUseCache(Long id);

    Shop queryWithLogicalExpire(Long id);

    Shop saveShopUseCache(Shop shop);

    Shop updateShopUseCache(Shop shop);

    Shop saveShop2Redis(Long id, Long expireSeconds);


}

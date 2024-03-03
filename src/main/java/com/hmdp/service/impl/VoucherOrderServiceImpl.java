package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.func.Func1;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.dto.UserOrderDTO;
import com.hmdp.entity.*;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Transactional
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    ISeckillVoucherService seckillVoucherService;
    @Autowired
    RedisIdWorker redisIdWorker;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    RedissonClient redissonClient;
    @Autowired
    RabbitTemplate rabbitTemplate;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        Resource resource = new ClassPathResource("lua/order.lua");
        SECKILL_SCRIPT.setLocation(resource);
        SECKILL_SCRIPT.setResultType(Long.class);
    }

//    private static final DefaultRedisScript<Long> CANCEL_ORDER_SCRIPT;
//
//    static {
//        CANCEL_ORDER_SCRIPT = new DefaultRedisScript<>();
//        Resource resource = new ClassPathResource("lua/cancelOrder.lua");
//        CANCEL_ORDER_SCRIPT.setLocation(resource);
//        CANCEL_ORDER_SCRIPT.setResultType(Long.class);
//    }

    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {

        // 获取⽤户
        Long userId = UserHolder.getUser().getId();

        // 获取订单id
        long orderId = redisIdWorker.nextId("order");

        // 1.执⾏lua脚本, 具备原子性，串行化执行，校验用户是否一人一单
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();

        // 2.判断结果是否为0
        if (r == 1) {
            return Result.fail("库存不足");
        }
        if (r == 2) {
            return Result.fail("不允许重复下单");
        }

        // 消息队列异步处理下单
        UserOrderDTO userDTO = new UserOrderDTO();
        userDTO.setOrderId(orderId);
        userDTO.setId(userId);
        userDTO.setVoucherId(voucherId);
        rabbitTemplate.convertAndSend("hmall.direct","voucher_order", userDTO);


        // 3.返回订单id
        return Result.ok(orderId);
    }


}



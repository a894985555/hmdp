package com.hmdp.listener;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.dto.UserOrderDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Component
@Transactional
public class VoucherOrderListener {

    @Autowired
    IVoucherOrderService voucherOrderService;

    @Autowired
    ISeckillVoucherService seckillVoucherService;
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "hmall.queue"),
            exchange = @Exchange(name = "hmall.direct"),
            key={"voucher_order"}
    ))
    public void listenObjectQueue(UserOrderDTO userDTO) {

        // User(id=null, phone=123456, password=null, nickName=zc, ggg=null, deleted=false, info=null, status=null, icon=, createTime=null, updateTime=null)
        System.out.println(userDTO);

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(userDTO.getVoucherId());
        voucherOrder.setUserId(userDTO.getId());
        voucherOrder.setId(userDTO.getOrderId());

        // 3.创建订单
        seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .update();

        voucherOrderService.save(voucherOrder);


        System.out.println("创建订单成功");

    }
}

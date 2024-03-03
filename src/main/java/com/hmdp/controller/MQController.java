package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;


/**
 * 可以看出消息按奇偶分流给两个消费者
 * 主线程很快就返回结果了，而消息队列的消费者是异步执行慢慢处理相关的消息
 */

/**
 * 由于消费者1的处理速度快，因此给他分配更多的消息
 * 而消费者2的处理速度慢，因此可以少给他分配消息
 * 最终效率大大提升，能者多劳，合理安排分配策略，避免消息积压
 */
@RestController
@RequestMapping("/mq")
public class MQController {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @GetMapping("/fanout")
    public Result fanout() throws InterruptedException {

        String exchangeName = "hmall.fanout";// 消息
        String message = "hello, everyone_!";

        for (int i = 0; i < 50; i++) {

            // 发送消息，每20毫秒发送⼀次，相当于每秒发送50条消息
            rabbitTemplate.convertAndSend(exchangeName,"", message + i);
            Thread.sleep(20);
        }
        return Result.ok();
    }


    @GetMapping("/direct")
    public Result direct() throws InterruptedException {

        String exchangeName = "hmall.direct";// 消息
        String message = "direct_!";

        for (int i = 0; i < 5; i++) {

            rabbitTemplate.convertAndSend(exchangeName,"red", message + i + "red");
            rabbitTemplate.convertAndSend(exchangeName,"blue", message + i + "blue");
            rabbitTemplate.convertAndSend(exchangeName,"yellow", message + i + "yellow");
            Thread.sleep(20);
        }
        return Result.ok();
    }

    @GetMapping("topic")
    public Result topic() throws InterruptedException {
        String exchangeName = "hmall.topic";// 消息
        String message = "topic_";

        for (int i = 0; i < 5; i++) {

            rabbitTemplate.convertAndSend(exchangeName,"china.news", message + i + "_china.news");
            rabbitTemplate.convertAndSend(exchangeName,"china.news.123", message + i + "_china.news.123");
            rabbitTemplate.convertAndSend(exchangeName,"japan.news", message + i + "_japan.news");
            rabbitTemplate.convertAndSend(exchangeName,"japan.news.123", message + i + "_japan.news.123");
            Thread.sleep(20);
        }
        return Result.ok();
    }

    @GetMapping("test")
    public Result test() throws InterruptedException {
        String exchangeName = "hmall.test";// 消息
        String message = "topic_";


        for (int i = 0; i < 50; i++) {

            // 发送消息，每20毫秒发送⼀次，相当于每秒发送50条消息
            rabbitTemplate.convertAndSend(exchangeName, "", message + i);
            Thread.sleep(20);
        }
        return Result.ok();
    }

    @GetMapping("object")
    public Result testSendMap() throws InterruptedException {
        // 准备消息
        User user = new User();
        user.setPhone("123456");
        user.setNickName("zc");

        // 发送消息
        rabbitTemplate.convertAndSend("object.fanout","", user);
        return Result.ok();
    }
}

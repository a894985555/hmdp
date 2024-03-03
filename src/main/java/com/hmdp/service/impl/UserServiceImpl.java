package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.HexValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号:利用util下RegexUtils进行正则验证
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确!");
        }
        //2.生成验证码:导入hutool依赖，内有RandomUtil
        String code = RandomUtil.randomNumbers(6);
        //3.保存验证码到Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);//有效期2mins
        session.setAttribute("code", code);
        //4.发送验证码
        log.info("验证码为: " + code);
        log.debug("发送短信验证码成功!");

        return Result.ok();

    }

    @Override
    public Result loginBySession(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验⼿机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("⼿机号格式错误！");
        }
        // 3.校验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            //3.不⼀致，报错
            return Result.fail("验证码错误");
        }
        //⼀致，根据⼿机号查询⽤户
        User user = query().eq("phone", phone).one();
        //5.判断⽤户是否存在
        if (user == null) {
            //不存在，则创建
            user = createUserWithPhone(phone);
        }
        //7.保存⽤户信息到session中
        session.setAttribute("user", user);
        return Result.ok();

    }

    @Override
    public Result loginByRedis(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验⼿机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("⼿机号格式错误！");
        }
        // 3.从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 不⼀致，报错
            return Result.fail("验证码错误");
        }
        // 4.⼀致，根据⼿机号查询⽤户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
        // 5.判断⽤户是否存在
        if (user == null) {
            // 6.不存在，创建新⽤户并保存
            user = createUserWithPhone(phone);
        }
        // 7.保存⽤户信息到 redis中
        // 7.1.随机⽣成token，作为登录令牌
        String token = UUID.randomUUID().toString();
        // 7.2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        System.out.println(userDTO);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> {
                                    if (fieldValue != null) {
                                        return fieldValue.toString();
                                    }
                                return "";
                        }));

        // 7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 8.返回token
        return Result.ok(token);
    }

    /**
     * bitMap存储签到
     * @return
     */
    @Override
    public Result sign() {
        // 1.获取当前登录⽤户
        Long userId = UserHolder.getUser().getId();
        // 2.获取⽇期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本⽉的第⼏天
        int dayOfMonth = now.getDayOfMonth();
        System.out.println(dayOfMonth);
        // 5.写⼊Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();

    }

    /**
     * 获取最近连续签到数
     * @return
     */
    @Override
    public Result signCount() {
        // 1.获取当前登录⽤户
        Long userId = UserHolder.getUser().getId();
        // 2.获取⽇期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本⽉的第⼏天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本⽉截⽌今天为⽌的所有的签到记录，返回的是⼀个⼗进制的数字 bitfield sign:1034:202402 get u28 0
        // 因此会消去开头多余的0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        result = result;
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        System.out.println(num);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        while (true) {
            // 6.1.让这个数字与1做与运算，得到数字的最后⼀个bit位 // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移⼀位，抛弃最后⼀个bit位，继续下⼀个bit位
            num >>>= 1;
        }
        System.out.println(count);
        return Result.ok(count);
    }

    public User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        save(user);
        return user;
    }
}


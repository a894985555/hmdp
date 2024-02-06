package com.hmdp.aspect;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Aspect
public class CommonAspect {


    @Pointcut(value = "execution(* com.hmdp.controller.*.*(..))")
    public void controllerPointCut() {}

    @Pointcut(value = "execution(* com.hmdp.service.*.*(..))")
    public void servicePointCut() {}

    @Pointcut(value = "execution(* com.hmdp.mapper.*.*(..))")
    public void mapperPointCut() {}


//    @Around("controllerPointCut()")
    public Object controllerAspect(ProceedingJoinPoint joinPoint) {
        Object res = new Object();
        try {
            System.out.print("controller : " + joinPoint.getSignature().getName() + "(");
            ObjectMapper objectMapper = new ObjectMapper();
//            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            // 如果json中有新增的字段并且是实体类类中不存在的，不报错
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            // 设置序列化格式
            javaTimeModule.addSerializer(LocalDateTime.class,
                    new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            objectMapper.registerModule(javaTimeModule);
            System.out.println(joinPoint.getArgs());
//            String jsonStr = objectMapper.writeValueAsString(joinPoint.getArgs());
            String jsonStr = "123";
            System.out.println(jsonStr + ")");
            res = joinPoint.proceed();
        } catch (Throwable throwable) {
            System.out.println(joinPoint.getSignature().getName()+throwable + "异常");
        } finally {
//            System.out.println("controller end: " + joinPoint.getSignature().getName());
        }
        return res;
    }

//    @Around("servicePointCut()")
    public Object serviceAspect(ProceedingJoinPoint joinPoint) {
        Object res = new Object();
        try {
            System.out.print("\tservice : " + joinPoint.getSignature().getName() + "(");
            ObjectMapper objectMapper = new ObjectMapper();
//            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            // 如果json中有新增的字段并且是实体类类中不存在的，不报错
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            // 设置序列化格式
            javaTimeModule.addSerializer(LocalDateTime.class,
                    new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            objectMapper.registerModule(javaTimeModule);
            String jsonStr = objectMapper.writeValueAsString(joinPoint.getArgs());
            System.out.println(jsonStr + ")");
            res = joinPoint.proceed();
        } catch (Throwable throwable) {
            System.out.println(joinPoint.getSignature().getName()+throwable + "异常");
        } finally {
//            System.out.println("\tservice end: " + joinPoint.getSignature().getName());
        }
        return res;
    }

//    @Around("mapperPointCut()")
    public Object mapperAspect(ProceedingJoinPoint joinPoint) {
        Object res = new Object();
        try {
            System.out.print("\t\tmapper : " + joinPoint.getSignature().getName() + "(");
            ObjectMapper objectMapper = new ObjectMapper();
//            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            // 如果json中有新增的字段并且是实体类类中不存在的，不报错
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            // 设置序列化格式
            javaTimeModule.addSerializer(LocalDateTime.class,
                    new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            objectMapper.registerModule(javaTimeModule);
            String jsonStr = objectMapper.writeValueAsString(joinPoint.getArgs());
            System.out.println(jsonStr + ")");

            res = joinPoint.proceed();

        } catch (Throwable throwable) {
            System.out.println(joinPoint.getSignature().getName()+throwable + "异常");
        } finally {
//            System.out.println("mapper end: " + joinPoint.getSignature().getName());
        }
        return res;
    }
}

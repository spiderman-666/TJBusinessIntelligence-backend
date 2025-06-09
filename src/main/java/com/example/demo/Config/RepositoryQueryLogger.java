package com.example.demo.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
public class RepositoryQueryLogger {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Around("execution(* com.example.demo.Repository.*.*(..))")
    public Object logQueryExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        try {
            return joinPoint.proceed();
        } finally {
            long time = System.currentTimeMillis() - start;
            String method = joinPoint.getSignature().toShortString();

            System.out.println("[SQL 查询日志] 方法: " + method + ", 耗时: " + time + " ms");

            // 写入 Redis 日志队列
            Map<String, Object> queryLog = new HashMap<>();
            queryLog.put("query", method);
            queryLog.put("time", time);
            queryLog.put("timestamp", System.currentTimeMillis());
            stringRedisTemplate.opsForList().leftPush("sql:query:log", objectMapper.writeValueAsString(queryLog));
        }
    }
}


package com.yws.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class RedisLockUtil {

    //分布式锁
    public static boolean getCacheLock(StringRedisTemplate stringRedisTemplate, String lockKey, String clientId, int timeout, TimeUnit timeUnit){
        return stringRedisTemplate.opsForValue().setIfAbsent(lockKey, clientId, timeout, timeUnit);
    }

    public static void releaseCacheLock(StringRedisTemplate stringRedisTemplate, String lockKey, String clientId){
        if(clientId.equals(stringRedisTemplate.opsForValue().get(lockKey))){
            stringRedisTemplate.delete(lockKey);
        }
    }


}

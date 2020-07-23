package com.yws.annotation;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class CacheAspect {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate<String, Object> objectRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Pointcut("@annotation(Cache)")
    public void cachePointCut(){};


    // 3. 环绕通知
    @Around(value = "cachePointCut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String targetClass = joinPoint.getTarget().getClass().getName();
        Signature signature = joinPoint.getSignature();
        String methodName = joinPoint.getSignature().getName();
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < joinPoint.getArgs().length; i++) {
            args.append(i + joinPoint.getArgs()[i].toString() + ";");
        }
        String key = targetClass + ":" + methodName + ":" + args;

        RLock lock = redissonClient.getLock("RedissonLock");

        Method method = ((MethodSignature) signature).getMethod();
        Method methodWithAnnotations = joinPoint.getTarget().getClass().getDeclaredMethod(method.getName(), method.getParameterTypes());
        Cache cache = methodWithAnnotations.getDeclaredAnnotation(Cache.class);

//        switch (cache.cacheValid()) {
//            case VALID:
//                if (stringRedisTemplate.hasKey(key)) {
//                    //读缓存
//                    return readCache(cache, key);
//                }
//                synchronized (stringRedisTemplate) {
//                    if (stringRedisTemplate.hasKey(key)) {
//                        //读缓存
//                        return readCache(cache, key);
//                    }
//                    Object proceed = joinPoint.proceed();
//                    //写缓存
//                    writeCache(cache, key, proceed);
//                    return proceed;
//                }
//            case INVALID:
//                Object proceed1 = joinPoint.proceed();
//                //缓存失效
//                invalidCache(cache, targetClass);
//                return proceed1;
//        }
//        return null;
//    }
        switch (cache.cacheValid()){
            case VALID:
                if(stringRedisTemplate.hasKey(key)){
                    //读缓存
                    return readCache(cache, key);
                }
                try{
                    boolean res = lock.tryLock(1,1,TimeUnit.SECONDS);
                    if(res){
                        Object proceed = joinPoint.proceed();
                        //写缓存
                        writeCache(cache, key, proceed);
                        return proceed;
                    }
                }catch (Exception e){
                    throw e;
                }finally {
                    lock.unlock();
                }
            case INVALID:
                Object proceed1 = joinPoint.proceed();
                //缓存失效
                invalidCache(cache, targetClass);
                return proceed1;
        }
        return null;
    }

    //读缓存
    private Object readCache(Cache cache, String key){
        System.out.println("读缓存...");
        Object o = objectRedisTemplate.opsForHash().get(key, 0+"");
        stringRedisTemplate.expire(key, getExpireTime(cache), cache.expireUnit());
        return o;
    }

    //写缓存
    private void writeCache(Cache cache, String key, Object proceed){
        System.out.println("写缓存...");
        objectRedisTemplate.opsForHash().put(key, 0+"", proceed);
        stringRedisTemplate.expire(key, getExpireTime(cache), cache.expireUnit());
    }

    //缓存失效
    private void invalidCache(Cache cache, String targetClass){
        System.out.println("缓存失效...");
        Set<String> keys = null;
        if (cache.thisExpireMethods().length == 1 && cache.thisExpireMethods()[0] == "*") {
            keys = stringRedisTemplate.keys(targetClass+"*");
        }else {
            keys = new HashSet<>();
            for (String method : cache.thisExpireMethods()) {
                keys.addAll(stringRedisTemplate.keys(targetClass+":"+method+"*"));
            }
        }
        if(cache.otherExpireMethods().length != 0){
            for (InValidMethod inValidMethod : cache.otherExpireMethods()) {
                for (String method : inValidMethod.methods()) {
                    keys.addAll(stringRedisTemplate.keys(inValidMethod.inValidClass().getName()+":"+method+"*"));
                }
            }
        }
        for (String key : keys) {
            stringRedisTemplate.expire(key, -1, TimeUnit.MINUTES);
        }
    }

    //读取过期时间
    private int getExpireTime(Cache cache){
        int expireTime;
        if(cache.expireMinTime() != -1 && cache.expireMaxTime() != -1){
            expireTime = cache.expireMinTime() + new Random().nextInt(cache.expireMaxTime()-cache.expireMinTime());
        } else{
            expireTime = cache.expireTime();
        }
        return expireTime;
    }
}

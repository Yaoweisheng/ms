package com.yws.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cache {
    enum CacheValid{ VALID, INVALID};
    CacheValid cacheValid() default CacheValid.VALID;//设置缓存有效、无效
    int expireTime() default 60;//过期时间
    int expireMinTime() default -1;//最小过期时间
    int expireMaxTime() default -1;//最大过期时间
    TimeUnit expireUnit() default TimeUnit.MINUTES;//过期时间单位
    String[] thisExpireMethods() default {"*"};//当前类的失效数据方法
    InValidMethod[] otherExpireMethods() default {};//其他类的失效数据方法
}
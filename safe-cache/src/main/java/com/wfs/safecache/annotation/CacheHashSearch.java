package com.wfs.safecache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis Hash类型，不可设置存活时间
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheHashSearch {
    /**
     * 业务名称
     */
    String businessName() default "";

    /**
     * 缓存查询的key，仅支持spel语句
     */
    String cacheKey();

    /**
     * Map的key，仅支持spel语句（检查spel语句：idea，光标移动到该字段参数上使用alt+enter，选择 Language injections --> Spring EL）
     */
    String hashKey();

    /**
     * 是否使用配置文件中的配置（设置为true，则优先使用全局配置；全局配置中为null的使用注解配置）
     */
    boolean globalConfig() default true;

    /**
     * 是否使用分布式锁
     */
    boolean enableDistributedLock() default false;

    /**
     * 是否存储null
     */
    boolean saveNull() default true;
}

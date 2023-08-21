package com.wfs.safecache.annotation;

import java.lang.annotation.*;

/**
 * 向Redisson布隆过滤器中添加数据
 */
@Repeatable(AddDataToBloomFilters.class)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AddDataToBloomFilter {

    /**
     * 业务名称
     */
    String businessName() default "";

    /**
     * 布隆过滤器名称
     */
    String bloomFilterName();

    /**
     * 插入进布隆过滤器中数据，仅支持spel语句（检查spel语句：idea，光标移动到该字段参数上使用alt+enter，选择 Language injections --> Spring EL）
     */
    String dataOfBloomFilter();
}

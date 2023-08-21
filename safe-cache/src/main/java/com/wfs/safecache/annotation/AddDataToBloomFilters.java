package com.wfs.safecache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AddDataToBloomFilter的重复注解，向Redisson布隆过滤器中添加数据
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AddDataToBloomFilters {
    AddDataToBloomFilter[] value();
}

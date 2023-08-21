package com.wfs.safecache.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

@ConfigurationProperties(prefix = "wfs-safe-cache")
@Data
public class SafeCacheProperties {
    /**
     * 缓存存活时间（不配置默认使用注解字段默认值，default=-1）
     */
    private Long timeout;

    /**
     * 缓存存活时间单位（不配置默认使用注解字段默认值，default=TimeUnit.SECONDS）
     */
    private TimeUnit unit;

    /**
     * 是否使用分布式锁（不配置默认使用注解字段默认值，default=true）
     */
    private Boolean enableDistributedLock;

    /**
     * 是否缓存null（不配置默认使用注解字段默认值，default=false）
     */
    private Boolean saveNull;

    /**
     * null缓存存活时间（不配置默认使用注解字段默认值，default=30）
     */
    private Long timeoutOfNull;

    /**
     * null缓存存活时间单位（不配置默认使用注解字段默认值，default=TimeUnit.SECONDS）
     */
    private TimeUnit unitOfNull;
}
package com.wfs.safecache.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.wfs.safecache.aop.BloomFilterAop;
import com.wfs.safecache.aop.CacheSearchAop;
import com.wfs.safecache.controller.BloomFilterController;
import com.wfs.safecache.properties.SafeCacheBloomFilterProperties;
import com.wfs.safecache.properties.SafeCacheProperties;
import com.wfs.safecache.scanner.BloomFilterScanner;
import com.wfs.safecache.service.BloomFilterScheduleService;
import com.wfs.safecache.service.BloomFilterService;
import com.wfs.safecache.service.MasterScheduleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling//开启定时任务，写在别的地方扫不到
@EnableConfigurationProperties(value = {SafeCacheProperties.class, SafeCacheBloomFilterProperties.class})
public class SafeCacheAutoConfiguration {
//涉及版权，不予展示
}

package com.wfs.safecache.controller;

import com.alibaba.fastjson.JSON;
import com.wfs.safecache.config.MyRedisAutoConfiguration;
import com.wfs.safecache.entity.MasterServer;
import com.wfs.safecache.entity.Result;
import com.wfs.safecache.myEnum.RedisKeysEnum;
import com.wfs.safecache.properties.MyRedisProperties;
import com.wfs.safecache.properties.SafeCacheBloomFilterProperties;
import com.wfs.safecache.scanner.BloomFilterScanner;
import com.wfs.safecache.service.BloomFilterScheduleService;
import com.wfs.safecache.service.BloomFilterService;
import com.wfs.safecache.service.MasterScheduleService;
import com.wfs.safecache.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

@RestController
@RequestMapping("/bloomFilter")
@Slf4j
public class BloomFilterController {
//涉及版权，不予展示
}
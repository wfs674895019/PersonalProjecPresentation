package com.wfs.safecache.aop;

import com.alibaba.fastjson.JSON;
import com.wfs.safecache.annotation.CacheHashSearch;
import com.wfs.safecache.annotation.CacheStringSearch;
import com.wfs.safecache.config.MyRedisAutoConfiguration;
import com.wfs.safecache.properties.MyRedisProperties;
import com.wfs.safecache.properties.SafeCacheProperties;
import com.wfs.safecache.util.SpelUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Aspect
@Slf4j
@Order(2)//多个注解时, 先执行BloomFilterAop, 再执行CacheSearchAop
public class CacheSearchAop {
    @Autowired
    private SafeCacheProperties annotationProperties;
    @Autowired
    private MyRedisProperties myRedisProperties;
    @Autowired
    private MyRedisAutoConfiguration myRedisAutoConfiguration;

    //使用自己的StringRedisTemplate 和 RedissonClient
    private StringRedisTemplate redisTemplate;
    private RedissonClient redissonClient;

    @Autowired//setter注入
    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        if (myRedisProperties.isUnifiedConfig()) {//是否使用统一的redis配置，统一则用spring.redis的redis配置
            this.redisTemplate = redisTemplate;
        } else {
            this.redisTemplate = myRedisAutoConfiguration.getMyRedisTemplate();
        }
    }

    @Autowired//setter注入
    public void setRedissonClient(RedissonClient redissonClient) {
        if (myRedisProperties.isUnifiedConfig()) {//是否使用统一的redis配置，统一则用spring.redis的redis配置
            this.redissonClient = redissonClient;
        } else {
            this.redissonClient = myRedisAutoConfiguration.getMyRedissonClient();
        }
    }

    private final Map<String, ReentrantReadWriteLock> localLockMap = new ConcurrentHashMap<>();//存储本地锁

    private final Map<String, AtomicInteger> lockedThreadSetMap = new ConcurrentHashMap<>();//存储本地阻塞线程

    @Pointcut("@annotation(com.wfs.safecache.annotation.CacheStringSearch)")
    private void cacheStringSearchPointcut() {
    }

    @Pointcut("@annotation(com.wfs.safecache.annotation.CacheHashSearch)")
    private void cacheHashSearchPointcut() {
    }

    @Around("cacheStringSearchPointcut()")
    public Object cacheStringSearchPointcutMethod(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {//ProceedingJoinPoint继承了JoinPoint, 有proceed()方法可以执行原方法
        //获取原方法
        Method method = ((MethodSignature) proceedingJoinPoint.getSignature()).getMethod();
        //获取返回值类型
        Class<?> returnType = method.getReturnType();
        //获取注解
        CacheStringSearch annotation = method.getAnnotation(CacheStringSearch.class);

        String cacheKey = SpelUtil.generateKeyBySpEL(annotation.cacheKey(), proceedingJoinPoint);//spel取缓存的key
        long timeout;//存活时间
        TimeUnit unit;//时间单位
        boolean enableDistributedLock;//是否使用分布式锁
        String businessName = annotation.businessName();//业务名称
        boolean saveNull;//是否存null
        long timeoutOfNull;//null缓存存活时间
        TimeUnit unitOfNull;//null缓存存活时间单位

        log.debug("业务名称'{}', cacheKey='{}': 被@CacheStringSearch拦截: ", businessName, cacheKey);

        //开启使用全局配置, 全局配置里有的用全局配置, 没有的用注解的值
        if (annotation.globalConfig()) {
            timeout = annotationProperties.getTimeout() != null ? annotationProperties.getTimeout() : annotation.timeout();
            unit = annotationProperties.getUnit() != null ? annotationProperties.getUnit() : annotation.unit();
            enableDistributedLock = annotationProperties.getEnableDistributedLock() != null ? annotationProperties.getEnableDistributedLock() : annotation.enableDistributedLock();
            saveNull = annotationProperties.getSaveNull() != null ? annotationProperties.getSaveNull() : annotation.saveNull();
            timeoutOfNull = annotationProperties.getTimeoutOfNull() != null ? annotationProperties.getTimeoutOfNull() : annotation.timeoutOfNull();
            unitOfNull = annotationProperties.getUnitOfNull() != null ? annotationProperties.getUnitOfNull() : annotation.unitOfNull();
        } else {
            timeout = annotation.timeout();
            unit = annotation.unit();
            enableDistributedLock = annotation.enableDistributedLock();
            saveNull = annotation.saveNull();
            timeoutOfNull = annotation.timeoutOfNull();
            unitOfNull = annotation.unitOfNull();
        }

        String cache = redisTemplate.opsForValue().get(cacheKey);//查缓存

        if (cache != null) {//缓存中有, 直接返回
            return JSON.parseObject(cache, returnType);
        } else {//缓存中没有, 执行原代码, 并把结果放缓存中
            ReadWriteLock readWriteLock;

            if (enableDistributedLock) {//开启集群
                //分布式锁
                readWriteLock = redissonClient.getReadWriteLock(cacheKey + "-lock");
                log.debug("业务名称'{}', cacheKey='{}': Redis中不存在, 开启集群模式, 准备抢锁执行原方法", businessName, cacheKey);
            } else {
                //本地锁
                localLockMap.putIfAbsent(cacheKey, new ReentrantReadWriteLock(true));
                readWriteLock = localLockMap.get(cacheKey);
                log.debug("业务名称'{}', cacheKey='{}': Redis中不存在, 开启本地模式, 准备抢锁执行原方法", businessName, cacheKey);
            }

            //尝试获取写锁；获取失败则获取读锁, 然后阻塞, 等待写锁放行
            if (readWriteLock.writeLock().tryLock()) {
                log.info("业务名称'{}', cacheKey='{}': 加锁, 准备执行原方法", businessName, cacheKey);
                try {
                    return stringProceed(proceedingJoinPoint, returnType, cacheKey, timeout, unit, saveNull, timeoutOfNull, unitOfNull);//执行原方法
                } finally {
                    readWriteLock.writeLock().unlock();
                    log.info("业务名称'{}', cacheKey='{}': 解锁, 释放阻塞线程", businessName, cacheKey);
                }
            } else {
                //记录阻塞线程数量
                lockedThreadSetMap.putIfAbsent(cacheKey, new AtomicInteger(0));
                AtomicInteger atomicBlockedThreadNumber = lockedThreadSetMap.get(cacheKey);
                int incrementAndGet = atomicBlockedThreadNumber.incrementAndGet();

                log.info("业务名称'{}', cacheKey='{}': 当前阻塞线程'{}'个", businessName, cacheKey, incrementAndGet);

                readWriteLock.readLock().lock();
                try {
                    return stringProceed(proceedingJoinPoint, returnType, cacheKey, timeout, unit, saveNull, timeoutOfNull, unitOfNull);
                } finally {
                    readWriteLock.readLock().unlock();
                    int decrementAndGet = atomicBlockedThreadNumber.decrementAndGet();
                    log.info("业务名称'{}', cacheKey='{}': 被放行, 当前阻塞线程'{}'个", businessName, cacheKey, decrementAndGet);
                }
            }
        }
    }

    private Object stringProceed(ProceedingJoinPoint proceedingJoinPoint, Class<?> returnType, String cacheKey, Long timeout, TimeUnit unit, boolean saveNull, long timeoutOfNull, TimeUnit unitOfNull) throws Throwable {
        String secondQueryCache = redisTemplate.opsForValue().get(cacheKey);//双重检查, 查缓存
        if (secondQueryCache != null) {
            return JSON.parseObject(secondQueryCache, returnType);
        } else {
            Object DBObject = proceedingJoinPoint.proceed();//执行原方法, 查数据库

            if (DBObject == null && saveNull) {//如果数据库查询为null且saveNull为true, 存空字符串进redis
                if (timeoutOfNull <= 0) {
                    redisTemplate.opsForValue().set(cacheKey, "");//存空进缓存里
                } else {
                    redisTemplate.opsForValue().set(cacheKey, "", timeoutOfNull, unitOfNull);//存空进缓存里
                }
            } else if (DBObject != null) {
                String jsonString = JSON.toJSONString(DBObject);
                if (timeout <= 0) {
                    redisTemplate.opsForValue().set(cacheKey, jsonString);//存进缓存里
                } else {
                    redisTemplate.opsForValue().set(cacheKey, jsonString, timeout, unit);//存进缓存里
                }
            }
            return DBObject;
        }
    }

    @Around("cacheHashSearchPointcut()")
    public Object cacheHashSearchPointcutMethod(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {//ProceedingJoinPoint继承了JoinPoint, 有proceed()方法可以执行原方法
        //获取原方法
        Method method = ((MethodSignature) proceedingJoinPoint.getSignature()).getMethod();
        //获取返回值类型
        Class<?> returnType = method.getReturnType();
        //获取注解
        CacheHashSearch annotation = method.getAnnotation(CacheHashSearch.class);

        String cacheKey = SpelUtil.generateKeyBySpEL(annotation.cacheKey(), proceedingJoinPoint);//spel取缓存的key
        String hashKey = SpelUtil.generateKeyBySpEL(annotation.hashKey(), proceedingJoinPoint);//spel取缓存的hashKey
        boolean enableDistributedLock;//是否使用分布式锁
        String businessName = annotation.businessName();//业务名称
        boolean saveNull;//是否存null

        log.debug("业务名称'{}', cacheKey='{}:{}': 被@CacheHashSearch拦截", businessName, cacheKey, hashKey);

        //开启使用全局配置, 全局配置里有的用全局配置, 没有的用注解的值
        if (annotation.globalConfig()) {
            enableDistributedLock = annotationProperties.getEnableDistributedLock() != null ? annotationProperties.getEnableDistributedLock() : annotation.enableDistributedLock();
            saveNull = annotationProperties.getSaveNull() != null ? annotationProperties.getSaveNull() : annotation.saveNull();
        } else {
            enableDistributedLock = annotation.enableDistributedLock();
            saveNull = annotation.saveNull();
        }

        String cache = (String) redisTemplate.opsForHash().get(cacheKey, hashKey);//查缓存

        if (cache != null) {//缓存中有, 直接返回
            return JSON.parseObject(cache, returnType);
        } else {//缓存中没有, 执行原代码, 并把结果放缓存中
            ReadWriteLock readWriteLock;

            if (enableDistributedLock) {//开启集群
                //分布式锁
                readWriteLock = redissonClient.getReadWriteLock(cacheKey + "-" + hashKey + "-lock");
                log.debug("业务名称'{}', cacheKey='{}:{}': Redis中不存在, 开启集群模式, 准备抢锁执行原方法", businessName, cacheKey, hashKey);
            } else {
                //本地锁
                localLockMap.putIfAbsent(cacheKey + "-" + hashKey, new ReentrantReadWriteLock(true));
                readWriteLock = localLockMap.get(cacheKey + "-" + hashKey);
                log.debug("业务名称'{}', cacheKey='{}:{}': Redis中不存在, 开启集群模式, 准备抢锁执行原方法", businessName, cacheKey, hashKey);
            }

            if (readWriteLock.writeLock().tryLock()) {
                log.info("业务名称'{}', cacheKey='{}:{}': 加锁, 准备执行原方法", businessName, cacheKey, hashKey);
                try {
                    return hashProceed(proceedingJoinPoint, returnType, cacheKey, hashKey, saveNull);
                } finally {
                    readWriteLock.writeLock().unlock();
                    log.info("业务名称'{}', cacheKey='{}:{}': 解锁, 释放阻塞线程", businessName, cacheKey, hashKey);
                }
            } else {
                //记录阻塞线程数量
                lockedThreadSetMap.putIfAbsent(cacheKey + ":" + hashKey, new AtomicInteger(0));
                AtomicInteger atomicBlockedThreadNumber = lockedThreadSetMap.get(cacheKey + ":" + hashKey);
                int incrementAndGet = atomicBlockedThreadNumber.incrementAndGet();

                log.info("业务名称'{}', cacheKey='{}:{}': 被阻塞, 当前阻塞线程'{}'个", businessName, cacheKey, hashKey, incrementAndGet);
                readWriteLock.readLock().lock();

                try {
                    return hashProceed(proceedingJoinPoint, returnType, cacheKey, hashKey, saveNull);
                } finally {
                    readWriteLock.readLock().unlock();
                    int decrementAndGet = atomicBlockedThreadNumber.decrementAndGet();
                    log.info("业务名称'{}', cacheKey='{}:{}': 被放行, 当前阻塞线程: {}个", businessName, cacheKey, hashKey, decrementAndGet);
                }
            }
        }
    }

    private Object hashProceed(ProceedingJoinPoint proceedingJoinPoint, Class<?> returnType, String cacheKey, String hashKey, boolean saveNull) throws Throwable {
        String secondQueryCache = (String) redisTemplate.opsForHash().get(cacheKey, hashKey);//双重检查, 查缓存
        if (secondQueryCache != null) {
            return JSON.parseObject(secondQueryCache, returnType);
        } else {
            Object DBObject = proceedingJoinPoint.proceed();//执行原方法, 查数据库
            if (DBObject == null && saveNull) {//如果数据库查询为null且saveNull为true, 存空字符串进redis
                redisTemplate.opsForHash().put(cacheKey, hashKey, "");//存进缓存里
            } else if (DBObject != null) {
                String jsonString = JSON.toJSONString(DBObject);
                redisTemplate.opsForHash().put(cacheKey, hashKey, jsonString);//存进缓存里
            }
            return DBObject;
        }
    }
}

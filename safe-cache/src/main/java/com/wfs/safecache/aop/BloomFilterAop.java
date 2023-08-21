package com.wfs.safecache.aop;

import com.wfs.safecache.annotation.AddDataToBloomFilter;
import com.wfs.safecache.annotation.AddDataToBloomFilters;
import com.wfs.safecache.annotation.BloomFilter;
import com.wfs.safecache.config.MyRedisAutoConfiguration;
import com.wfs.safecache.entity.BloomFilterContainsEntity;
import com.wfs.safecache.myEnum.BloomFilterEnum;
import com.wfs.safecache.myEnum.RedisKeysEnum;
import com.wfs.safecache.properties.MyRedisProperties;
import com.wfs.safecache.util.SpelUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Aspect
@Slf4j
@Order(1)//多个注解时, 先执行BloomFilterAop, 再执行CacheSearchAop
public class BloomFilterAop {
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

    @Pointcut("@annotation(com.wfs.safecache.annotation.BloomFilter)")
    private void bloomFilterPointcut() {
    }

    @Pointcut("@annotation(com.wfs.safecache.annotation.AddDataToBloomFilter)")
    private void AddDataToBloomFilterPointcut() {
    }

    @Pointcut("@annotation(com.wfs.safecache.annotation.AddDataToBloomFilters)")
    private void AddDataToBloomFiltersPointcut() {
    }

    /**
     * 拦截查询请求，查布隆过滤器是否有数据
     */
    @Around("bloomFilterPointcut()")
    public Object bloomFilterPointcutMethod(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {//ProceedingJoinPoint继承了JoinPoint, 有proceed()方法可以执行原方法
        //获取原方法
        Method method = ((MethodSignature) proceedingJoinPoint.getSignature()).getMethod();
        //获取注解
        BloomFilter annotation = method.getAnnotation(BloomFilter.class);
        //获取原方法返回类型
        Class<?> returnType = method.getReturnType();

        String[] bloomFilterNames = annotation.bloomFilterName();
        List<String> dataOfBloomFilterList = Arrays.stream(annotation.dataOfBloomFilter()).map(data -> SpelUtil.generateKeyBySpEL(data, proceedingJoinPoint)).collect(Collectors.toList());
        String businessName = annotation.businessName();
        BloomFilterEnum pattern = annotation.pattern();

        log.info("业务名称'{}': @BloomFilter被拦截, 布隆过滤器'{}', dataOfBloomFilter'{}'",
                businessName, Arrays.toString(bloomFilterNames), dataOfBloomFilterList);

        List<BloomFilterContainsEntity> result = new ArrayList<>();//记录布隆过滤器查询结果
        for (int i = 0; i < bloomFilterNames.length; i++) {
            RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(RedisKeysEnum.bloomFilterLockPrefix + bloomFilterNames[i]);

            //尝试获取读锁, 如果获取到则bloomFilter没有处于更新状态, 可以使用bloomFilter；如果没有获取到读锁, 则bloomFilter正在更新, 不使用bloomFilter
            if (readWriteLock.readLock().tryLock()) {
                try {
                    log.info("业务名称'{}, dataOfBloomFilter='{}': 准备查询布隆过滤器'{}'", businessName, dataOfBloomFilterList.get(i), bloomFilterNames[i]);
                    RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(RedisKeysEnum.bloomFilterPrefix + bloomFilterNames[i]);

                    //判断布隆过滤器是否存在（布隆过滤器可能被人为删除）
                    if (!bloomFilter.isExists()) {
                        log.error("业务名称'{}': 布隆过滤器'{}'不存在! 方法继续执行", businessName, bloomFilterNames[i]);
                        return proceedingJoinPoint.proceed();
                    }

                    //如果布隆过滤器本体被人搞破坏从Redis中给删了
                    if (Boolean.FALSE.equals(redisTemplate.hasKey(RedisKeysEnum.bloomFilterPrefix + bloomFilterNames[i]))) {
                        log.error("业务名称'{}': 布隆过滤器'{}'本体丢失! 方法继续执行", businessName, bloomFilterNames[i]);
                        return proceedingJoinPoint.proceed();
                    }

                    //查询布隆过滤器；如果有人直接从Redis中删了布隆过滤器的配置信息（size和hashIterations），会在这报错
                    boolean contains;
                    try {
                        contains = bloomFilter.contains(dataOfBloomFilterList.get(i));
                    } catch (Exception e) {
                        log.error("业务名称'{}': 布隆过滤器'{}'出现异常! 方法继续执行", businessName, bloomFilterNames[i], e);
                        return proceedingJoinPoint.proceed();
                    }

                    //记录布隆过滤器判断结果
                    BloomFilterContainsEntity containsEntity = new BloomFilterContainsEntity();
                    containsEntity.setBloomFilterName(bloomFilterNames[i]);
                    containsEntity.setDataOfBloomFilter(dataOfBloomFilterList.get(i));
                    containsEntity.setContains(contains);

                    result.add(containsEntity);
                } finally {
                    readWriteLock.readLock().unlock();
                }
            } else {
                log.info("业务名称'{}': 布隆过滤器'{}'正在被占用, 方法继续执行", businessName, bloomFilterNames[i]);
                return proceedingJoinPoint.proceed();
            }
        }

        //如果只有一个布隆过滤器
        if (result.size() == 1) {
            BloomFilterContainsEntity containsEntity = result.get(0);

            // 查询次数 +1
            redisTemplate.opsForValue().increment(RedisKeysEnum.bloomFilterPrefix + containsEntity.getBloomFilterName() + RedisKeysEnum.numberOfQueriesSuffix);

            //如果布隆过滤器判断数据存在，执行原方法
            if (containsEntity.isContains()) {
                log.info("业务名称'{}': 布隆过滤器'{}'判断dataOfBloomFilter='{}'存在, 原方法放行", businessName, containsEntity.getBloomFilterName(), containsEntity.getDataOfBloomFilter());
                Object proceed = proceedingJoinPoint.proceed();

                //如果是原方法返回值是list类型且list是空，或者原方法返回值是null，说明误判了
                if ((returnType == List.class && ((List<?>) proceed).isEmpty()) || proceed == null) {

                    //误判次数 +1
                    redisTemplate.opsForValue().increment(RedisKeysEnum.bloomFilterPrefix + containsEntity.getBloomFilterName() + RedisKeysEnum.numberOfFalseSuffix);
                    log.info("业务名称'{}': 布隆过滤器'{}'判断dataOfBloomFilter='{}'存在, 出现误判!", businessName, containsEntity.getBloomFilterName(), containsEntity.getDataOfBloomFilter());
                }

                return proceed;
            } else {
                log.info("业务名称'{}': 布隆过滤器'{}'判断dataOfBloomFilter='{}'不存在, 返回null", businessName, containsEntity.getBloomFilterName(), containsEntity.getDataOfBloomFilter());
                return null;
            }
        }

        //如果有多个布隆过滤器，单匹配模式
        if (pattern.equals(BloomFilterEnum.SINGLE_MATCH)) {
            for (BloomFilterContainsEntity containsEntity : result) {

                //查询次数 +1
                redisTemplate.opsForValue().increment(RedisKeysEnum.bloomFilterPrefix + containsEntity.getBloomFilterName() + RedisKeysEnum.numberOfQueriesSuffix);

                if (containsEntity.isContains()) {
                    log.info("业务名称'{}': 布隆过滤器'{}'判断dataOfBloomFilter='{}'存在, 执行原方法", businessName, containsEntity.getBloomFilterName(), containsEntity.getDataOfBloomFilter());
                    Object proceed = proceedingJoinPoint.proceed();

                    //如果是原方法返回值是list类型且list是空，或者原方法返回值是null，说明误判了
                    if ((returnType == List.class && ((List<?>) proceed).isEmpty()) || proceed == null) {
                        redisTemplate.opsForValue().increment(RedisKeysEnum.bloomFilterPrefix + containsEntity.getBloomFilterName() + RedisKeysEnum.numberOfFalseSuffix);// 误判次数 +1
                        log.info("业务名称'{}': 布隆过滤器'{}'判断dataOfBloomFilter='{}'存在, 出现误判!", businessName, containsEntity.getBloomFilterName(), containsEntity.getDataOfBloomFilter());
                    }

                    return proceed;
                }
            }

            //如果多个布隆过滤器都判断不存在，返回null
            log.info("业务名称'{}': 布隆过滤器'{}'判断dataOfBloomFilter='{}'不存在, 返回null", businessName, Arrays.toString(bloomFilterNames), dataOfBloomFilterList);
            return null;
        }

        //如果有多个布隆过滤器，全匹配模式
        else {
            for (BloomFilterContainsEntity containsEntity : result) {

                //查询次数 +1
                redisTemplate.opsForValue().increment(RedisKeysEnum.bloomFilterPrefix + containsEntity.getBloomFilterName() + RedisKeysEnum.numberOfQueriesSuffix);

                if (!containsEntity.isContains()) {
                    log.info("业务名称'{}': 布隆过滤器'{}'判断dataOfBloomFilter='{}'不存在, 返回null", businessName, containsEntity.getBloomFilterName(), containsEntity.getDataOfBloomFilter());
                    return null;
                }
            }

            //如果多个布隆过滤器都判断存在，执行原方法
            log.info("业务名称'{}': 布隆过滤器'{}'判断dataOfBloomFilter='{}'存在, 执行原方法", businessName, Arrays.toString(bloomFilterNames), dataOfBloomFilterList);
            Object proceed = proceedingJoinPoint.proceed();

            //如果是原方法返回值是list类型且list是空，或者原方法返回值是null，说明误判了
            if ((returnType == List.class && ((List<?>) proceed).isEmpty()) || proceed == null) {
                for (String bloomFilterName : bloomFilterNames) {
                    redisTemplate.opsForValue().increment(RedisKeysEnum.bloomFilterPrefix + bloomFilterName + RedisKeysEnum.numberOfFalseSuffix);// 误判次数 +1
                }
                log.info("业务名称'{}': 布隆过滤器'{}'判断dataOfBloomFilter='{}'存在, 出现误判!", businessName, Arrays.toString(bloomFilterNames), dataOfBloomFilterList);
            }

            return proceed;
        }
    }

    /**
     * 拦截新增数据的请求，向布隆过滤器插数据（单个注解）
     */
    @Around("AddDataToBloomFilterPointcut()")
    public Object AddDataToBloomFilterPointcutMethod(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {

        //获取原方法
        Method method = ((MethodSignature) proceedingJoinPoint.getSignature()).getMethod();

        //获取注解
        AddDataToBloomFilter annotation = method.getAnnotation(AddDataToBloomFilter.class);

        //向布隆过滤器add数据
        addDataToBloomFilter(proceedingJoinPoint, annotation);

        //执行原方法
        return proceedingJoinPoint.proceed();
    }

    /**
     * 拦截新增数据的请求，向布隆过滤器插数据（重复注解）
     */
    @Around("AddDataToBloomFiltersPointcut()")
    public Object AddDataToBloomFiltersPointcutMethod(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {

        //获取原方法
        Method method = ((MethodSignature) proceedingJoinPoint.getSignature()).getMethod();

        //获取注解
        AddDataToBloomFilters addDataToBloomFilters = method.getAnnotation(AddDataToBloomFilters.class);
        AddDataToBloomFilter[] annotations = addDataToBloomFilters.value();

        //先执行原方法（如果先向布隆过滤器add数据，那么如果数据库还没add数据时，客户端来查数据，布隆过滤器放行，但数据库没数据返回null，如果再把null存redis，那这个数据在null过期前就相当于没add）
        Object proceed = proceedingJoinPoint.proceed();

        //向布隆过滤器add数据
        for (AddDataToBloomFilter annotation : annotations) {
            addDataToBloomFilter(proceedingJoinPoint, annotation);
        }

        return proceed;
    }

    /**
     * 向布隆过滤器add数据
     */
    private void addDataToBloomFilter(ProceedingJoinPoint proceedingJoinPoint, AddDataToBloomFilter annotation) {
        String businessName = annotation.businessName();
        String bloomFilterName = annotation.bloomFilterName();
        String dataOfBloomFilter = SpelUtil.generateKeyBySpEL(annotation.dataOfBloomFilter(), proceedingJoinPoint);

        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(RedisKeysEnum.bloomFilterPrefix + bloomFilterName);
        if (bloomFilter.isExists()) {

            //如果布隆过滤器本体被人搞破坏从Redis中给删了，配置信息还在：此时不能向布隆过滤器里插入数据，否则会自动新创建一个无数据的布隆过滤器
            if (Boolean.FALSE.equals(redisTemplate.hasKey(RedisKeysEnum.bloomFilterPrefix + annotation.bloomFilterName()))) {
                log.error("业务名称'{}': 布隆过滤器'{}'本体丢失, 新增数据: {} 失败", businessName, annotation.bloomFilterName(), dataOfBloomFilter);
                return;
            }

            bloomFilter.add(dataOfBloomFilter);//如果因为配置信息（size和hashIterations）被删除，那么这里会add失败，同时查询时也会失败，所以不会存在数据库有数据却被布隆过滤器拦截的情况
            log.info("业务名称'{}': 布隆过滤器'{}'新增数据: {}", businessName, bloomFilterName, dataOfBloomFilter);
        } else {
            log.error("业务名称'{}': 布隆过滤器'{}'不存在, 新增数据: {} 失败", businessName, bloomFilterName, dataOfBloomFilter);
        }
    }
}
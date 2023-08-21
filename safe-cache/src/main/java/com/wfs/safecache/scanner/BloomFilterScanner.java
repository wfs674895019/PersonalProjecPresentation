package com.wfs.safecache.scanner;

import com.alibaba.fastjson.JSON;
import com.wfs.safecache.annotation.BloomFilter;
import com.wfs.safecache.config.MyRedisAutoConfiguration;
import com.wfs.safecache.entity.MasterServer;
import com.wfs.safecache.myEnum.RedisKeysEnum;
import com.wfs.safecache.properties.MyRedisProperties;
import com.wfs.safecache.properties.SafeCacheBloomFilterProperties;
import com.wfs.safecache.service.BloomFilterScheduleService;
import com.wfs.safecache.service.BloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 在项目启动之后执行扫描所有@BloomFilter
 */
@Component
@Slf4j
@Order(value = 1)
public class BloomFilterScanner implements ApplicationRunner, DisposableBean {

    @Autowired
    private BloomFilterService bloomFilterService;
    @Autowired
    private BloomFilterScheduleService bloomFilterScheduleService;
    @Autowired
    private SafeCacheBloomFilterProperties bloomFilterProperties;
    @Autowired
    private Environment environment;
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

    /**
     * 在项目启动之后执行扫描所有@BloomFilter
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        runBloomFilterApplication();
    }

    /**
     * 项目关闭前，删除Redis中注册的Master服务器信息，手动关闭RedissonClient（不手动关闭，springboot会被阻塞不退出）；redis连接不需要手动关闭，不知道为啥
     */
//    @PreDestroy 用这个注解也能实现项目关闭前执行代码
    @Override
    public void destroy() {
        redisTemplate.delete(RedisKeysEnum.masterServer);
        myRedisAutoConfiguration.getMyRedissonClient().shutdown();//因为MyRedissonClient没有被springboot管理，所以需要手动shutdown
        log.info("服务关闭, 已删除Redis中注册的Master服务器信息");
    }

    /**
     * 布隆过滤器项目初始化
     */
    public void runBloomFilterApplication() throws Exception {

        //抢到锁的是master服务器
        InetAddress localHost = InetAddress.getLocalHost();
        String port = environment.getProperty("local.server.port");
        MasterServer masterServer = new MasterServer(localHost.getHostAddress(), port);

        //setIfAbsent当做锁，存活时间 9 分钟，开启定时任务，每 5 分钟续租
        Boolean setIfAbsent = redisTemplate.opsForValue().setIfAbsent(
                RedisKeysEnum.masterServer, JSON.toJSONString(masterServer), bloomFilterProperties.getLeaseTerm(), bloomFilterProperties.getLeaseTermTimeUnit());
        if (Boolean.TRUE.equals(setIfAbsent)) {
            log.info("布隆过滤器Master服务器为: {}", masterServer);

//                添加ShutdownHook，服务关闭时，删除redis中注册的Master服务器信息（主线程抛异常后挂了，开启这个钩子线程，但Redis连接已经关了，所以会卡在redisTemplate.delete这里，所以不要用注册关闭钩子这种方式了）
//                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//                    redisTemplate.delete(RedisKeysEnum.masterServer);
//                    log.info("服务关闭, 已删除Redis中注册的Master服务器信息");
//                }));
        } else {
            return;
        }

            /*
              不能在这添加检查Master服务器租期定时任务
              masterScheduleService.addCheckLeaseSchedule(masterServer);
              会循环依赖
             */

        //校验falseRateMonitorCron
        String falseRateMonitorCron = bloomFilterProperties.getFalseRateMonitorCron();
        if (!falseRateMonitorCron.equalsIgnoreCase("stop") && !CronExpression.isValidExpression(falseRateMonitorCron)) {
            throw new IllegalArgumentException("falseRateMonitorCron= " + falseRateMonitorCron + " 不是合法的cron表达式!");
        }

        log.info("================开始搜索 @BloomFilter================");

        //获得所有的BloomFilter注解
        List<BloomFilter> bloomFilterList = bloomFilterService.getAllBloomFilterAnnotation();

        log.info("项目中共有@BloomFilter {} 个", bloomFilterList.size());

        Set<String> bloomFilterNameSet = new HashSet<>();//set判断名字唯一

        int n = 1;
        for (BloomFilter annotation : bloomFilterList) {
            log.info("@BloomFilter初始化: {}/{}", n++, bloomFilterList.size());

            String businessName = annotation.businessName();
            String[] bloomFilterNames = annotation.bloomFilterName();
            long[] expectedInsertions = annotation.InitOfExpectedInsertions();
            double[] falseProbability = annotation.InitOfFalseProbability();
            Class<?>[] entity = annotation.InitOfEntity();
            String[] fieldNames = annotation.InitOfFieldName();

            //获取updateCron
            String updateCron;
            if (annotation.globalConfigCron()) {
                updateCron = bloomFilterProperties.getUpdateCron() != null ? bloomFilterProperties.getUpdateCron() : annotation.updateCron();
            } else {
                updateCron = annotation.updateCron();
            }

            //判断注解参数中数组的数量是否不一致，不一致抛异常
            if (!bloomFilterService.verify(annotation)) {
                throw new IllegalArgumentException("业务名称: " + businessName + ", @BloomFilter中数组数量不一致!");
            }

            //依次初始化布隆过滤器
            for (int i = 0; i < bloomFilterNames.length; i++) {

                //判断bloomFilterName是否唯一
                if (!bloomFilterNameSet.add(bloomFilterNames[i])) {
                    throw new IllegalArgumentException("bloomFilterName = " + bloomFilterNames[i] + " 不唯一!");
                }

                //如果不重置 InitOfExpectedInsertions 和 InitOfFalseProbability
                if (!bloomFilterProperties.isResetBloomFilterConfig()) {
                    RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(RedisKeysEnum.bloomFilterPrefix + bloomFilterNames[i]);
                    if (bloomFilter.isExists()) {//如果布隆过滤器存在，获取Redis中的最新值
                        try {
                            expectedInsertions[i] = bloomFilter.getExpectedInsertions();
                            falseProbability[i] = bloomFilter.getFalseProbability();
                        }
                        //如果出现参数转换失败（例如有人在Redis中改了数字变成字母，或者直接删除了字段会报java.lang.IllegalStateException: Bloom filter is not initialized!），这时用注解的值
                        catch (Exception e) {
                            expectedInsertions[i] = annotation.InitOfExpectedInsertions()[i];
                            falseProbability[i] = annotation.InitOfFalseProbability()[i];
                        }
                    }
                }

                //初始化布隆过滤器
                bloomFilterService.initBloomFilter(businessName, bloomFilterNames[i], expectedInsertions[i], falseProbability[i], entity[i], fieldNames[i]);

                //如果重置BloomFilter:UpdateCron，需要插入定时任务 updateCron 进 Redis
                if (bloomFilterProperties.isResetUpdateCron()) {
                    redisTemplate.opsForHash().put(RedisKeysEnum.updateCron, bloomFilterNames[i], updateCron);
                }

                //添加定时任务（需要先初始化布隆过滤器和插入定时任务 updateCron 进 Redis，再添加定时任务，否则定时任务需要的 Redis 数据还未创建）
                bloomFilterScheduleService.addUpdateScheduleFromRedis(bloomFilterNames[i]);
            }
        }

        //添加误判率的监控器（需要先初始化布隆过滤器，再添加监控器，否则定时任务需要的 Redis 数据还未创建）
        redisTemplate.opsForValue().set(RedisKeysEnum.falseRateMonitorCron, falseRateMonitorCron);
        bloomFilterScheduleService.addFalseRateMonitorScheduleFromRedis();

        log.info("================@BloomFilter 全部初始化完成================");
    }
}

//    @PreDestroy
//    public void destroy() {
//        redisTemplate.delete("BloomFilterMasterServer");
//        log.info("服务正在关闭, 已删除redis中注册的Master服务器信息");
//    }


//
//    //after bean是com.wfs.demo.service.PersonService$$EnhancerBySpringCGLIB$$d0718584，是代理类；
//    // 不知道为啥，但就是不能直接method.getAnnotation拿注解，这样拿不到，只能AnnotationUtils.findAnnotation(跟getAnnotation有啥区别还没看)
//    @Override
//    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
//        Class<?> beanClass = bean.getClass();
//        Method[] methods = beanClass.getDeclaredMethods();
////        for (Method method : methods) {
////            // 处理带有自定义注解的方法
////            Annotation annotation = AnnotationUtils.findAnnotation(method, BloomFilter.class);
////            if (annotation != null) {
////                System.out.println("找到了,class:" + beanClass.getName() + " method:" + method + "anno: " + annotation);
////            }
////        }
//        System.out.println(beanName + "初始化完成");
//        return bean;
//    }
//
////    @Autowired
////    RedissonClient redissonClient;
//
//    //    @Autowired
////    StringRedisTemplate redisTemplate;
//
//    //before获取到的bean是com.wfs.demo.service.PersonService，可以直接method.getAnnotation拿注解
//    @Override
//    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
//        Class<?> beanClass = bean.getClass();
//        Method[] methods = beanClass.getMethods();
////        for (Method method : methods) {
////            if (method.isAnnotationPresent(BloomFilter.class)) {
////                // 处理带有自定义注解的方法
////                BloomFilter annotation = method.getAnnotation(BloomFilter.class);
////
////                System.out.println("Found method with CustomAnnotation: " + beanClass.getName() + method.getName());
////                RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(RedisKeysEnum.bloomFilterPrefix + annotation.bloomFilterName()[0]);
////                bloomFilter.tryInit(10, 0.1);
////            }
////        }
////        System.out.println(redisTemplate.opsForValue().get("a"));
//        System.out.println(beanName + "初始化之前");
//        return bean;
//    }
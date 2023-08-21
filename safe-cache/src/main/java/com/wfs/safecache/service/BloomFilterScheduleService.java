package com.wfs.safecache.service;

import com.wfs.safecache.annotation.BloomFilter;
import com.wfs.safecache.config.MyRedisAutoConfiguration;
import com.wfs.safecache.entity.BloomFilterEntity;
import com.wfs.safecache.myEnum.RedisKeysEnum;
import com.wfs.safecache.properties.MyRedisProperties;
import com.wfs.safecache.properties.SafeCacheBloomFilterProperties;
import com.wfs.safecache.vo.BFScheduleUpdateVo;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

@Service
@Slf4j
public class BloomFilterScheduleService {

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;
    @Autowired
    private BloomFilterService bloomFilterService;
    @Autowired
    private SafeCacheBloomFilterProperties bloomFilterProperties;
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

    private final Map<String, ScheduledFuture<?>> updateScheduledFutureMap = new HashMap<>();

    private ScheduledFuture<?> falseRateMonitorScheduledFuture;

    /**
     * 添加定时更新任务，从redis中取cron
     */
    public void addUpdateScheduleFromRedis(String bloomFilterName) {
        ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(
                //1.添加任务内容(Runnable)
                () -> updateTask(bloomFilterName),//这个方法不支持向外抛除了RuntimeException的异常，必须自己把异常给处理掉

                //2.设置执行周期(Trigger)，如果是null则不创建定时任务
                triggerContext -> {
                    String updateCron = (String) redisTemplate.opsForHash().get(RedisKeysEnum.updateCron, bloomFilterName);
                    if (updateCron == null) {//有人直接从redis把数据给删了，会导致null（定时任务的线程在这里挂掉，跟主线程没关系）
                        log.error("Redis的BloomFilter:UpdateCron中没找到: {}, 定时任务无法创建!", bloomFilterName);
                        throw new RuntimeException("Redis的BloomFilter:UpdateCron中没找到: " + bloomFilterName + ", 定时任务无法创建!");
                    } else if (updateCron.equalsIgnoreCase("stop")) {
                        log.info("布隆过滤器'{}', updateCron={}, 定时任务已暂停", bloomFilterName, updateCron);
                        return null;
                    } else if (!CronExpression.isValidExpression(updateCron)) {
                        log.error("布隆过滤器'{}', updateCron={} 不是合法的cron表达式, 定时任务创建失败!", bloomFilterName, updateCron);
                        throw new IllegalArgumentException("布隆过滤器: " + bloomFilterName + ", updateCron=" + updateCron + ", 不是合法的cron表达式!");
                    } else {
                        log.info("布隆过滤器'{}'添加定时更新任务, updateCron={}", bloomFilterName, updateCron);
                        return new CronTrigger(updateCron).nextExecutionTime(triggerContext);
                    }
                }
        );

        //把要执行的定时任务记录进map，用于在更新删除时，取消这个已注册进线程池的定时任务
        if (scheduledFuture != null) {
            updateScheduledFutureMap.put(bloomFilterName, scheduledFuture);
        }
    }

    /**
     * 更新布隆过滤器
     */
    private void updateTask(String bloomFilterName) {

        BloomFilter annotation = bloomFilterService.getBloomFilterAnnotationByBloomFilterName(bloomFilterName);
        if (annotation != null) {
            RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(RedisKeysEnum.bloomFilterPrefix + bloomFilterName);

            if (bloomFilter.isExists()) {
                log.info("业务名称'{}', 布隆过滤器'{}', 开始执行自动更新", annotation.businessName(), bloomFilterName);

                try {
                    bloomFilterService.update(bloomFilterName, null, null);
                } catch (Exception e) {
                    log.error("业务名称'{}', 布隆过滤器'{}', 更新失败!", annotation.businessName(), bloomFilterName, e);
                }
            } else {
                log.info("业务名称'{}', 布隆过滤器'{}', 已删除, 如需更新请手动操作", annotation.businessName(), bloomFilterName);
                cancelUpdateScheduledFuture(bloomFilterName);//取消掉加在定时任务线程池的任务
            }
        } else {
            log.error("布隆过滤器'{}'不存在!", bloomFilterName);
            cancelUpdateScheduledFuture(bloomFilterName);//取消掉加在定时任务线程池的任务（如果有人通过update(BFScheduleUpdateVo updateVo)加了一个不存在的bloomFilterName的定时任务，那就需要在这里把定时任务取消掉，否则这个任务还会被再次加进线程池里）
        }
    }

    /**
     * 更新定时任务，为controller提供方法实现
     */
    public void updateUpdateTask(BFScheduleUpdateVo updateVo) {
        redisTemplate.opsForHash().put(RedisKeysEnum.updateCron, updateVo.getBloomFilterName(), updateVo.getCron());

        //取消现在已经注册进线程池的定时任务
        cancelUpdateScheduledFuture(updateVo.getBloomFilterName());

        //添加新的定时任务
        addUpdateScheduleFromRedis(updateVo.getBloomFilterName());
    }

    /**
     * 删除定时任务，为controller提供方法实现
     */
    public void deleteUpdateTask(String bloomFilterName) {

        redisTemplate.opsForHash().put(RedisKeysEnum.updateCron, bloomFilterName, "stop");//逻辑删除

        //取消现在已经注册进线程池的定时任务
        cancelUpdateScheduledFuture(bloomFilterName);
    }

    /**
     * 取消现在已经注册进线程池的定时任务
     */
    private void cancelUpdateScheduledFuture(String bloomFilterName) {
        ScheduledFuture<?> scheduledFuture = updateScheduledFutureMap.get(bloomFilterName);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            log.info("布隆过滤器'{}'定时更新任务已取消", bloomFilterName);
        }
    }

    /**
     * 取消所有现在已经注册进线程池的定时任务，为controller提供方法实现
     */
    public void cancelAllUpdateScheduledFuture() {
        Collection<ScheduledFuture<?>> values = updateScheduledFutureMap.values();
        for (ScheduledFuture<?> scheduledFuture : values) {
            scheduledFuture.cancel(true);
        }
        log.info("定时更新任务已全部取消");
    }

    /**
     * 获取存储定时任务的map，为controller提供方法实现
     */
    public Map<String, ScheduledFuture<?>> getUpdateScheduledFutureMap() {
        return updateScheduledFutureMap;
    }


    /********************************************************************/

    /**
     * 添加误判率监视器的定时任务
     */
    public void addFalseRateMonitorScheduleFromRedis() {
        falseRateMonitorScheduledFuture = taskScheduler.schedule(
                //1.添加任务内容(Runnable)
                this::falseRateMonitorTask,

                //2.设置执行周期(Trigger)，如果是null则不创建定时任务
                triggerContext -> {
                    String cron = redisTemplate.opsForValue().get(RedisKeysEnum.falseRateMonitorCron);
                    if (cron == null) {//有人直接从redis把数据给删了，会导致null
                        log.error("Redis中未找到{}, 误判率检测器定时任务无法创建!", RedisKeysEnum.falseRateMonitorCron);
                        throw new RuntimeException("Redis中未找到" + RedisKeysEnum.falseRateMonitorCron + ", 误判率检测器定时任务无法创建!");
                    } else if (cron.equalsIgnoreCase("stop")) {
                        log.info("falseRateMonitorCron={}, 误判率监视器定时任务已暂停", cron);
                        return null;
                    } else if (!CronExpression.isValidExpression(cron)) {
                        log.error("falseRateMonitorCron={} 不是合法的cron表达式, 定时任务创建失败!", cron);
                        throw new IllegalArgumentException("falseRateMonitorCron=" + cron + ", 不是合法的cron表达式!");
                    } else {
                        log.info("添加误判率监视器定时任务, falseRateMonitorCron={}", cron);
                        return new CronTrigger(cron).nextExecutionTime(triggerContext);
                    }
                }
        );
    }

    /**
     * 检查所有布隆过滤器的误判率，并对超过阈值的布隆过滤器进行更新
     */
    private void falseRateMonitorTask() {

        List<BloomFilterEntity> bloomFilterEntityList = bloomFilterService.getAllBloomFilterEntity();

        for (BloomFilterEntity bloomFilterEntity : bloomFilterEntityList) {

            //判断是否存在
            if (bloomFilterEntity.isExists()) {
                if (bloomFilterEntity.getErrorMessage() != null) {
                    try {
                        log.info("业务名称'{}', 布隆过滤器'{}', Redis中发现数据异常: '{}', 触发更新",
                                bloomFilterEntity.getBusinessName(), bloomFilterEntity.getBloomFilterName(), bloomFilterEntity.getErrorMessage());
                        bloomFilterService.update(bloomFilterEntity.getBloomFilterName(), null, null);
                        continue;
                    } catch (Exception e) {
                        log.error("业务名称'{}', 布隆过滤器'{}', Redis中发现数据异常触发更新, 更新失败!",
                                bloomFilterEntity.getBusinessName(), bloomFilterEntity.getBloomFilterName(), e);
                        continue;
                    }
                }

                //判断是否超过阈值
                if (bloomFilterEntity.getNumberOfQueries() > bloomFilterProperties.getQueriesThreshold() &&
                        bloomFilterEntity.getFalseRate() > bloomFilterProperties.getFalseRateThreshold()) {

                    //更新
                    log.info("业务名称'{}', 布隆过滤器'{}', NumberOfQueries={}, FalseRate={}, 超过阈值, 开始更新",
                            bloomFilterEntity.getBusinessName(), bloomFilterEntity.getBloomFilterName(), bloomFilterEntity.getNumberOfQueries(), bloomFilterEntity.getFalseRate());

                    try {
                        bloomFilterService.update(bloomFilterEntity.getBloomFilterName(), bloomFilterEntity.getExpectedInsertions(), bloomFilterEntity.getFalseProbability());
                    } catch (Exception e) {
                        log.error("业务名称'{}', 布隆过滤器'{}', NumberOfQueries={}, FalseRate={}, 超过阈值, 更新失败!",
                                bloomFilterEntity.getBusinessName(), bloomFilterEntity.getBloomFilterName(), bloomFilterEntity.getNumberOfQueries(), bloomFilterEntity.getFalseRate(), e);
                    }
                } else {
                    log.info("业务名称'{}', 布隆过滤器'{}', NumberOfQueries={}, FalseRate={}, 未超过阈值, 无需更新",
                            bloomFilterEntity.getBusinessName(), bloomFilterEntity.getBloomFilterName(), bloomFilterEntity.getNumberOfQueries(), bloomFilterEntity.getFalseRate());
                }
            } else {
                log.info("业务名称'{}', 布隆过滤器'{}', 已删除, 如需更新请手动操作", bloomFilterEntity.getBusinessName(), bloomFilterEntity.getBloomFilterName());
            }
        }
    }

    /**
     * 误判率监视器更新定时任务
     */
    public void updateFalseRateMonitorTask(String cron) {

        redisTemplate.opsForValue().set(RedisKeysEnum.falseRateMonitorCron, cron);

        //取消现在已经注册进线程池的定时任务
        if (falseRateMonitorScheduledFuture != null) {
            falseRateMonitorScheduledFuture.cancel(true);
        }

        //添加新的定时任务
        addFalseRateMonitorScheduleFromRedis();
    }

    /**
     * 删除误判率监视器定时任务
     */
    public void deleteFalseRateMonitorTask() {

        redisTemplate.opsForValue().set(RedisKeysEnum.falseRateMonitorCron, "stop");//逻辑删除

        //取消现在已经注册进线程池的定时任务
        cancelFalseRateMonitorScheduledFuture();
    }

    /**
     * 取消现在已经注册进线程池的定时任务
     */
    public void cancelFalseRateMonitorScheduledFuture() {

        if (falseRateMonitorScheduledFuture != null) {
            falseRateMonitorScheduledFuture.cancel(true);
            log.info("误判率监视器定时任务已取消");
        }
    }
}


//    @Override
//    public void configureTasks(ScheduledTaskRegistrar scheduledTaskRegistrar) {
//        List<String> bloomFilterNameList = bloomFilterService.getAllBloomFilterName();
//        for (String bloomFilterName : bloomFilterNameList) {
//            scheduledTaskRegistrar.addTriggerTask(
//                    //1.添加任务内容(Runnable)
//                    () -> task(bloomFilterName),
//
//                    //2.设置执行周期(Trigger)
//                    triggerContext -> {
//                        QueryWrapper<BloomFilterScheduleEntity> queryWrapper = new QueryWrapper<>();
//                        queryWrapper.eq("name", bloomFilterName);
//                        BloomFilterScheduleEntity bloomFilterSchedule = scheduleMapper.selectOne(queryWrapper);
//                        if (bloomFilterSchedule == null) {
//                            log.error("bloom_filter_schedule表中没找到: {}, 定时任务无法创建", bloomFilterName);
//                            return null;
//                        }
//                        return new CronTrigger(bloomFilterSchedule.getCron()).nextExecutionTime(triggerContext);
//                    });
//        }
//    }
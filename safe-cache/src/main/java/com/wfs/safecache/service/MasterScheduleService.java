package com.wfs.safecache.service;

import com.alibaba.fastjson.JSON;
import com.wfs.safecache.config.MyRedisAutoConfiguration;
import com.wfs.safecache.entity.MasterServer;
import com.wfs.safecache.myEnum.RedisKeysEnum;
import com.wfs.safecache.properties.MyRedisProperties;
import com.wfs.safecache.properties.SafeCacheBloomFilterProperties;
import com.wfs.safecache.scanner.BloomFilterScanner;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@Order(2)//让BloomFilterScanner先执行，再执行这个
public class MasterScheduleService implements ApplicationRunner {
    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;
    @Autowired
    private BloomFilterScanner bloomFilterScanner;
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

    @Autowired//setter注入
    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        if (myRedisProperties.isUnifiedConfig()) {//是否使用统一的redis配置，统一则用spring.redis的redis配置
            this.redisTemplate = redisTemplate;
        } else {
            this.redisTemplate = myRedisAutoConfiguration.getMyRedisTemplate();
        }
    }

    /**
     * 项目启动时添加检查Master服务器租期定时任务
     */
    @Override
    public void run(ApplicationArguments args) throws UnknownHostException {

        //添加检查Master服务器租期定时任务
        InetAddress localHost = InetAddress.getLocalHost();
        String port = environment.getProperty("local.server.port");
        MasterServer masterServer = new MasterServer(localHost.getHostAddress(), port);
        addCheckLeaseSchedule(masterServer);
    }

    /**
     * 添加检查Master服务器租期定时任务
     */
    private void addCheckLeaseSchedule(MasterServer masterServer) {
        taskScheduler.schedule(
                //1.添加任务内容(Runnable)
                () -> checkLeaseTask(masterServer),

                //2.设置执行周期(Trigger)，如果是null则不创建定时任务
                triggerContext -> {
                    String cron = bloomFilterProperties.getCheckLeaseCron();
                    if (cron.equalsIgnoreCase("stop")) {
                        log.info("checkLeaseCron={}, 当前服务器不添加租期检查定时任务", cron);
                        return null;
                    } else if (!CronExpression.isValidExpression(cron)) {
                        log.error("checkLeaseCron={} 不是合法的cron表达式, 定时任务创建失败!", cron);
                        throw new IllegalArgumentException("checkLeaseCron=" + cron + ", 不是合法的cron表达式!");
                    } else {
                        log.info("添加检查租期任务, cron={}", cron);
                        return new CronTrigger(cron).nextExecutionTime(triggerContext);
                    }
                }
        );
    }

    /**
     * 检查Master服务器租期任务
     */
    private void checkLeaseTask(MasterServer masterServer) {
        MasterServer redisMasterServer = JSON.parseObject(redisTemplate.opsForValue().get(RedisKeysEnum.masterServer), MasterServer.class);
        if (redisMasterServer == null) {
            //Master服务器挂了，没人续租导致redisMasterServer为null，执行换服务器代码
            log.info("开始更换Master服务器");
            try {
                bloomFilterScanner.runBloomFilterApplication();
            } catch (Exception e) {
                log.error("更换Master服务器报错!", e);
            }
        } else {
            if (Objects.equals(redisMasterServer.getIp(), masterServer.getIp()) && Objects.equals(redisMasterServer.getPort(), masterServer.getPort())) {
                //续租 9 分钟
                redisTemplate.opsForValue().set(RedisKeysEnum.masterServer, JSON.toJSONString(masterServer), bloomFilterProperties.getLeaseTerm(), bloomFilterProperties.getLeaseTermTimeUnit());
            }
        }
    }
}

package com.wfs.safecache.controller;

import com.alibaba.fastjson.JSON;
import com.wfs.safecache.config.MyRedisAutoConfiguration;
import com.wfs.safecache.entity.MasterServer;
import com.wfs.safecache.myEnum.RedisKeysEnum;
import com.wfs.safecache.properties.MyRedisProperties;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

@Controller
public class BloomFilterWeb {

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

    @RequestMapping("/bloomFilter/index.html")
    public String index() throws UnknownHostException {
        MasterServer masterServer = JSON.parseObject(redisTemplate.opsForValue().get(RedisKeysEnum.masterServer), MasterServer.class);
        if (masterServer == null) {
            throw new RuntimeException("redis中未找到" + RedisKeysEnum.masterServer + ", 无法判断Master服务器!");
        }
        InetAddress localHost = InetAddress.getLocalHost();
        String ip = localHost.getHostAddress();
        String port = environment.getProperty("local.server.port");
        if (Objects.equals(ip, masterServer.getIp()) && Objects.equals(port, masterServer.getPort())) {
            return null;//TODO 返回主页
        } else {//TODO 导致其他服务器不能进入主页了，改成拦截器，拦截定时任务的相关操作
            return "redirect:http://" + masterServer.getIp() + ":" + masterServer.getPort() + "/bloomFilter/index.html";
        }
    }
}

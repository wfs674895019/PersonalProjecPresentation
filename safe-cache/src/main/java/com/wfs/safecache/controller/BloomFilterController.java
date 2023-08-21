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
    @Autowired
    private BloomFilterService bloomFilterService;
    @Autowired
    private BloomFilterScheduleService bloomFilterScheduleService;
    @Autowired
    private SafeCacheBloomFilterProperties bloomFilterProperties;
    @Autowired
    private BloomFilterScanner bloomFilterScanner;
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

    /**************************************************************************/

    @GetMapping("/getAllBloomFilter")
    public Result getAllBloomFilter(String permissionPassword) {
        if (!bloomFilterProperties.getPermissionPassword().equals(permissionPassword)) {
            return Result.error("permissionPassword错误");
        }

        try {
            return Result.success(bloomFilterService.getAllBloomFilterEntity());
        } catch (Exception e) {
            log.error("/getAllBloomFilter报错", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/contains")
    public Result contains(@Validated BFContainsANDAddDataVo containsVo, BindingResult bindingResult) {
        if (!bloomFilterProperties.getPermissionPassword().equals(containsVo.getPermissionPassword())) {
            return Result.error("permissionPassword错误");
        }

        Result hasErrorsOrNot = hasErrorsOrNot(bindingResult);
        if (hasErrorsOrNot != null) {
            return hasErrorsOrNot;
        }

        try {
            RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(RedisKeysEnum.bloomFilterPrefix + containsVo.getBloomFilterName());
            if (bloomFilter.isExists()) {
                return Result.success(bloomFilter.contains(containsVo.getData()));
            } else {
                return Result.error("BloomFilter: " + containsVo.getBloomFilterName() + "不存在");
            }
        } catch (Exception e) {
            log.error("/contains报错, containsVo={}", containsVo, e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/update")
    public Result update(@Validated BFUpdateVo updateVo, BindingResult bindingResult) {
        if (!bloomFilterProperties.getPermissionPassword().equals(updateVo.getPermissionPassword())) {
            return Result.error("permissionPassword错误");
        }

        Result hasErrorsOrNot = hasErrorsOrNot(bindingResult);
        if (hasErrorsOrNot != null) {
            return hasErrorsOrNot;
        }

        try {
            bloomFilterService.update(updateVo.getBloomFilterName(), updateVo.getExpectedInsertions(), updateVo.getFalseProbability());
            return Result.success(updateVo.getBloomFilterName() + "更新成功", null);
        } catch (Exception e) {
            log.error("/update报错, updateVo={}", updateVo, e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/delete")
    public Result delete(@Validated BFDeleteVo deleteVo, BindingResult bindingResult) {
        if (!bloomFilterProperties.getPermissionPassword().equals(deleteVo.getPermissionPassword())) {
            return Result.error("permissionPassword错误");
        }

        Result hasErrorsOrNot = hasErrorsOrNot(bindingResult);
        if (hasErrorsOrNot != null) {
            return hasErrorsOrNot;
        }

        try {
            bloomFilterService.delete(deleteVo.getBloomFilterName());
            return Result.success(deleteVo.getBloomFilterName() + "已删除", null);
        } catch (Exception e) {
            log.error("/delete报错, deleteVo={}", deleteVo, e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/addData")
    public Result addData(@Validated BFContainsANDAddDataVo addDataVo, BindingResult bindingResult) {
        if (!bloomFilterProperties.getPermissionPassword().equals(addDataVo.getPermissionPassword())) {
            return Result.error("permissionPassword错误");
        }

        Result hasErrorsOrNot = hasErrorsOrNot(bindingResult);
        if (hasErrorsOrNot != null) {
            return hasErrorsOrNot;
        }

        try {
            RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(RedisKeysEnum.bloomFilterPrefix + addDataVo.getBloomFilterName());

            if (bloomFilter.isExists()) {
                bloomFilter.add(addDataVo.getData());
                return Result.success(addDataVo.getBloomFilterName() + "新增数据 " + addDataVo.getData() + " 成功", null);
            } else {
                return Result.error("BloomFilter: " + addDataVo.getBloomFilterName() + "不存在");
            }
        } catch (Exception e) {
            log.error("/addData报错, addDataVo={}", addDataVo, e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获得所有的定时更新任务
     */
    @GetMapping("/updateSchedule/getAllSchedule")
    public Result getAllSchedule(String permissionPassword) {
        if (!bloomFilterProperties.getPermissionPassword().equals(permissionPassword)) {
            return Result.error("permissionPassword错误");
        }

        try {
            Result checkMaster = checkMaster();
            if (checkMaster.getCode() == 1) {
                return checkMaster;
            }

            Map<String, ScheduledFuture<?>> scheduledFutureMap = bloomFilterScheduleService.getUpdateScheduledFutureMap();
            return Result.success(scheduledFutureMap);
        } catch (Exception e) {
            log.error("/updateSchedule/getAllSchedule报错", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 定时更新任务更新
     */
    @GetMapping("/updateSchedule/update")
    public Result scheduleUpdate(@Validated BFScheduleUpdateVo updateVo, BindingResult bindingResult) {
        if (!bloomFilterProperties.getPermissionPassword().equals(updateVo.getPermissionPassword())) {
            return Result.error("permissionPassword错误");
        }

        Result hasErrorsOrNot = hasErrorsOrNot(bindingResult);
        if (hasErrorsOrNot != null) {
            return hasErrorsOrNot;
        }

        try {
            Result checkMaster = checkMaster();
            if (checkMaster.getCode() == 1) {
                return checkMaster;
            }

            bloomFilterScheduleService.updateUpdateTask(updateVo);
            return Result.success();
        } catch (Exception e) {
            log.error("/updateSchedule/update报错, updateVo={}", updateVo, e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 定时更新任务删除
     */
    @GetMapping("/updateSchedule/delete")
    public Result scheduleDelete(@Validated BFScheduleDeleteVo deleteVo, BindingResult bindingResult) {
        if (!bloomFilterProperties.getPermissionPassword().equals(deleteVo.getPermissionPassword())) {
            return Result.error("permissionPassword错误");
        }

        Result hasErrorsOrNot = hasErrorsOrNot(bindingResult);
        if (hasErrorsOrNot != null) {
            return hasErrorsOrNot;
        }

        try {
            Result checkMaster = checkMaster();
            if (checkMaster.getCode() == 1) {
                return checkMaster;
            }

            bloomFilterScheduleService.deleteUpdateTask(deleteVo.getBloomFilterName());
            return Result.success();
        } catch (Exception e) {
            log.error("/updateSchedule/delete报错, deleteVo={}", deleteVo, e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 误判率监视器定时任务更新
     */
    @GetMapping("/falseRateMonitorSchedule/update")
    public Result falseRateMonitorScheduleUpdate(@Validated FRMScheduleUpdateVo updateVo, BindingResult bindingResult) {
        if (!bloomFilterProperties.getPermissionPassword().equals(updateVo.getPermissionPassword())) {
            return Result.error("permissionPassword错误");
        }

        Result hasErrorsOrNot = hasErrorsOrNot(bindingResult);
        if (hasErrorsOrNot != null) {
            return hasErrorsOrNot;
        }

        try {
            Result checkMaster = checkMaster();
            if (checkMaster.getCode() == 1) {
                return checkMaster;
            }

            bloomFilterScheduleService.updateFalseRateMonitorTask(updateVo.getCron());
            return Result.success();
        } catch (Exception e) {
            log.error("/falseRateMonitorSchedule/update报错, updateVo={}", updateVo, e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 误判率监视器定时任务删除
     */
    @GetMapping("/falseRateMonitorSchedule/delete")
    public Result falseRateMonitorScheduleDelete(String permissionPassword) {
        if (!bloomFilterProperties.getPermissionPassword().equals(permissionPassword)) {
            return Result.error("permissionPassword错误");
        }

        try {
            Result checkMaster = checkMaster();
            if (checkMaster.getCode() == 1) {
                return checkMaster;
            }

            bloomFilterScheduleService.deleteFalseRateMonitorTask();
            return Result.success();
        } catch (Exception e) {
            log.error("/falseRateMonitorSchedule/delete报错", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取master服务器
     */
    @GetMapping("/getBloomFilterMasterServer")
    public Result getBloomFilterMasterServer(String permissionPassword) {
        if (!bloomFilterProperties.getPermissionPassword().equals(permissionPassword)) {
            return Result.error("permissionPassword错误");
        }

        try {
            MasterServer masterServer = JSON.parseObject(redisTemplate.opsForValue().get(RedisKeysEnum.masterServer), MasterServer.class);
            if (masterServer != null) {
                return Result.success(masterServer);
            } else {
                return Result.success("Master服务器已挂机!", null);
            }
        } catch (Exception e) {
            log.error("/getBloomFilterMasterServer报错", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 当前服务器是否为master服务器
     */
    @GetMapping("/isBloomFilterMasterServer")
    public Result isBloomFilterMasterServer(String permissionPassword) {
        if (!bloomFilterProperties.getPermissionPassword().equals(permissionPassword)) {
            return Result.error("permissionPassword错误");
        }

        try {
            MasterServer masterServer = JSON.parseObject(redisTemplate.opsForValue().get(RedisKeysEnum.masterServer), MasterServer.class);
            if (masterServer == null) {
                return Result.success("Master服务器已挂机", null);
            }
            InetAddress localHost = InetAddress.getLocalHost();
            String ip = localHost.getHostAddress();
            String port = environment.getProperty("local.server.port");
            if (Objects.equals(ip, masterServer.getIp()) && Objects.equals(port, masterServer.getPort())) {
                return Result.success(true);
            } else {
                return Result.success(false);
            }
        } catch (Exception e) {
            log.error("/isBloomFilterMasterServer报错", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 将当前服务器设置为Master服务器，建议先在Master服务器调用/cancelMasterServer，或Master服务器已挂机
     */
    @GetMapping("/becameMasterServer")
    public Result becameMasterServer(String permissionPassword) {
        if (!bloomFilterProperties.getPermissionPassword().equals(permissionPassword)) {
            return Result.error("permissionPassword错误");
        }

        try {
            Result checkMaster = checkMaster();
            if (checkMaster.getCode() == 0) {
                return checkMaster;
            }

            redisTemplate.delete(RedisKeysEnum.masterServer);
            bloomFilterScanner.runBloomFilterApplication();
            return Result.success();
        } catch (Exception e) {
            log.error("/becameMasterServer报错", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 取消当前服务器的master身份（删除redis中注册的Master服务器信息，并删除所有的定时任务）
     */
    @GetMapping("/cancelMasterServer")
    public Result cancelMasterServer(String permissionPassword) {
        if (!bloomFilterProperties.getPermissionPassword().equals(permissionPassword)) {
            return Result.error("permissionPassword错误");
        }

        try {
            Result checkMaster = checkMaster();
            if (checkMaster.getCode() == 1) {
                return checkMaster;
            }

            redisTemplate.delete(RedisKeysEnum.masterServer);
            bloomFilterScheduleService.cancelAllUpdateScheduledFuture();
            bloomFilterScheduleService.cancelFalseRateMonitorScheduledFuture();
            log.info("已取消当前Master服务器权限");
            return Result.success();
        } catch (Exception e) {
            log.error("/cancelMasterServer报错", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 检查是否master服务器
     *
     * @return 为Master服务器返回Result.success；不为Master服务器或Master服务器已挂机返回Result.error
     */
    private Result checkMaster() throws UnknownHostException {
        MasterServer masterServer = JSON.parseObject(redisTemplate.opsForValue().get(RedisKeysEnum.masterServer), MasterServer.class);
        if (masterServer == null) {
            return Result.error("Master服务器已挂机，请先调用/bloomFilter/becameMasterServer获取Master权限");
        }
        InetAddress localHost = InetAddress.getLocalHost();
        String ip = localHost.getHostAddress();
        String port = environment.getProperty("local.server.port");
        if (Objects.equals(ip, masterServer.getIp()) && Objects.equals(port, masterServer.getPort())) {
            return Result.success("当前服务器已是Master服务器");
        } else {
            return Result.error("当前服务器不是Master服务器，无操作权限!");
        }
    }

    /**
     * 查看是否参数验证通过
     */
    private Result hasErrorsOrNot(BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            Map<String, String> map = new HashMap<>();
            for (FieldError error : bindingResult.getFieldErrors()) {
                String field = error.getField();//校验失败的字段名
                String message = error.getDefaultMessage();//校验失败的message
                map.put(field, message);
            }
            return Result.error("数据校验失败", map);
        } else {
            return null;
        }
    }
}
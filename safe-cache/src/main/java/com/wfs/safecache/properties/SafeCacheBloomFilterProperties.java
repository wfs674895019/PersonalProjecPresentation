package com.wfs.safecache.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

@ConfigurationProperties(prefix = "wfs-safe-cache.bloom-filter")
@Data
public class SafeCacheBloomFilterProperties {

    /**
     * 定时更新任务（不配置默认使用注解字段默认值，default="0 0 3 * * ?"，每日凌晨三点自动更新布隆过滤器），若需要停用定时更新，可设置updateCron="stop"
     */
    private String updateCron;

    /**
     * 项目启动时是否重置redis中已有的布隆过滤器的初始化配置信息（InitOfExpectedInsertions 和 InitOfFalseProbability）（default=true）
     */
    private boolean resetBloomFilterConfig = true;

    /**
     * 项目启动时是否重置redis中已有的BloomFilter:UpdateCron（default=true）
     */
    private boolean resetUpdateCron = true;

    /**
     * 监视误判率的定时任务，如果布隆过滤器的Redis中数据遭到非法删除或修改，也会触发更新（default="0 5/10 * * * ?"，从每小时的五分开始，每十分钟检查所有布隆过滤器的误判率）
     */
    private String falseRateMonitorCron = "0 5/10 * * * ? ";

    /**
     * 检查Master服务器租期的定时任务（default="0 0/5 * * * ?"，每五分钟检查一次Master服务器租期）
     */
    private String checkLeaseCron = "0 0/5 * * * ?";

    /**
     * 单次租期时长（default=9）
     */
    private long leaseTerm = 9;

    /**
     * 单次租期时长的时间单位
     */
    private TimeUnit leaseTermTimeUnit = TimeUnit.MINUTES;

    /**
     * 布隆过滤器初始化时，每批次从数据库插入数据的最大数据量（default=1000，每批次最多插入1000条数据）
     */
    private int maxAmountOfInsertedData = 1000;

    /**
     * 是否开启驼峰转下划线
     */
    private boolean mapUnderscoreToCamelCase = true;

    /**
     * 误判率监视器的Queries阈值，即查询次数大于queriesThreshold且误判率大于falseRateThreshold时进行布隆过滤器更新（default=100000）
     */
    private int queriesThreshold = 100000;

    /**
     * 误判率监视器的FalseRate阈值，即查询次数大于queriesThreshold且误判率大于falseRateThreshold时进行布隆过滤器更新（default=0.01）
     */
    private double falseRateThreshold = 0.01;

    /**
     * 进行布隆过滤器http操作的权限识别码（default=root）
     */
    private String permissionPassword = "root";
}

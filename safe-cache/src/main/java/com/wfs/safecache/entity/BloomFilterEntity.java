package com.wfs.safecache.entity;

import lombok.Data;

@Data
public class BloomFilterEntity {
    /**
     * 业务名称
     */
    private String businessName;

    /**
     * 布隆过滤器名称
     */
    private String bloomFilterName;

    /**
     * 插入进布隆过滤器中数据的spel语句
     */
    private String dataOfBloomFilter;

    /**
     * 布隆过滤器是否存在
     */
    private boolean isExists;

    /**
     * 布隆过滤器预期数据量
     */
    private Long expectedInsertions;

    /**
     * 布隆过滤器预期误判率
     */
    private Double falseProbability;

    /**
     * 布隆过滤器对应的Entity
     */
    private Class<?> InitOfEntity;

    /**
     * 向布隆过滤器中插入的Entity的字段（数据库表的列名）
     */
    private String InitOfFieldName;

    /**
     * number of bits in Redis memory required by this instance
     */
    private Long size;

    /**
     * Calculates probabilistic number of elements already added to Bloom filter.
     */
    private Long count;

    /**
     * hash iterations amount used per element.
     * Calculated during bloom filter initialization.
     */
    private Integer hashIterations;

    /**
     * 查询的总次数
     */
    private Long numberOfQueries;

    /**
     * 查询的误判次数
     */
    private Long numberOfFalse;

    /**
     * 布隆过滤器的实际误判率
     */
    private Double falseRate;

    /**
     * 定时任务
     */
    private String updateCron;

    /**
     * 异常信息(当Redis中出现数据丢失或认为修改非法参数时，会将异常信息保存在该字段)
     */
    private String errorMessage;
}

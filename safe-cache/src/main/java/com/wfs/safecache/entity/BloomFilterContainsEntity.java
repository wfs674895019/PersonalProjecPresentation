package com.wfs.safecache.entity;

import lombok.Data;

/**
 * 记录布隆过滤器判断结果
 */
@Data
public class BloomFilterContainsEntity {

    /**
     * 布隆过滤器名称
     */
    private String bloomFilterName;

    /**
     * 插入进布隆过滤器中数据的spel语句
     */
    private String dataOfBloomFilter;

    /**
     * 查询数据是否存在
     */
    private boolean contains;
}

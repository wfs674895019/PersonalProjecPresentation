package com.wfs.safecache.myEnum;

public class RedisKeysEnum {
    public static final String bloomFilterPrefix = "BloomFilter:";
    public static final String masterServer = "BloomFilter:MasterServer";
    public static final String updateCron = "BloomFilter:UpdateCron";
    public static final String falseRateMonitorCron = "BloomFilter:FalseRateMonitorCron";
    public static final String bloomFilterLockPrefix = "BloomFilter:Lock:";
    public static final String numberOfQueriesSuffix = ":NumberOfQueries";
    public static final String numberOfFalseSuffix = ":NumberOfFalse";
}

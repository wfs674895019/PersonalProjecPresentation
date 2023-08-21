package com.wfs.safecache.annotation;

import com.wfs.safecache.myEnum.BloomFilterEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redisson布隆过滤器，需要在对应的新增数据与修改数据的方法上添加@AddDataToBloomFilter或@@AddDataToBloomFilters
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BloomFilter {

    /**
     * 业务名称
     */
    String businessName() default "";

    /**
     * 布隆过滤器的名称，可指定多个，指定多个时，需设定匹配模式：为单匹配模式，一个布隆过滤器判断有数据就可以放行；为全匹配，所有布隆过滤器判断有数据才可以放行
     * <p>
     * 示例：
     * <p>
     * bloomFilterName={"book_name","author_name"}, dataOfBloomFilter={"'红楼梦'","'曹雪芹'"}
     * <p>
     * 适用于select * from book where book_name=? or author_name=?
     */
    String[] bloomFilterName();//TODO 还差打log，error改成warn

    /**
     * 在布隆过滤器中进行搜索的数据，仅支持spel语句（检查spel语句：idea，光标移动到该字段参数上使用alt+enter，选择 Language injections --> Spring EL）
     * 实际搜索的数据需要与fieldName字段一致<p>
     * 例如：当fieldName = "name+age"时，dataOfBloomFilter需要根据方法传入的参数不同，
     * 将dataOfBloomFilter指定为：dataOfBloomFilter = "#name+#age" 或 dataOfBloomFilter = "#person.name+#person.age"
     * <p>
     * 可指定多个，与bloomFilterName一一对应，指定多个时，为单匹配模式，一个布隆过滤器判断有数据就可以放行//TODO 多表联查 and查询需要是全匹配的
     * <p>
     * 示例：
     * <p>
     * bloomFilterName={"book_name","author_name"}, dataOfBloomFilter={"'红楼梦'","'曹雪芹'"}
     * <p>
     * 适用于select * from book where book_name=? or author_name=?
     */
    String[] dataOfBloomFilter();

    /**
     * 匹配模式，当指定多个布隆过滤器时启用（默认为全匹配模式）
     */
    BloomFilterEnum pattern() default BloomFilterEnum.FULL_MATCH;

    /**
     * 创建布隆过滤器的预期插入量，可指定多个，与bloomFilterName一一对应
     */
    long[] InitOfExpectedInsertions();

    /**
     * 创建布隆过滤器的预期错误率，如falseProbability = 0.01，可指定多个，与bloomFilterName一一对应
     */
    double[] InitOfFalseProbability();

    /**
     * 布隆过滤器对应实体类，需要标注有@TableName；标注有@TableField的字段，会根据@TableField进行SQL与字段进行映射，没有@TableField的字段默认 驼峰命名 转 下划线命名
     * <p>
     * 可指定多个，与bloomFilterName一一对应
     * <p>
     * 应用于布隆过滤器初始化阶段，用于操作数据库插入数据
     */
    Class<?>[] InitOfEntity();

    /**
     * 向布隆过滤器中插入的Entity的字段（数据库表的列名），多个字段用+号分隔，可指定多个，与bloomFilterName一一对应
     * <p>
     * 格式：fieldName = "name" 或 fieldName = "name+age"；实际插入进布隆过滤器的数据为 "张三" 或 "张三30"
     * <p>
     * 应用于布隆过滤器初始化阶段
     */
    String[] InitOfFieldName();

    /**
     * 定时任务，默认每日凌晨三点自动更新布隆过滤器
     * 注意：如果配置文件中设置 resetUpdateCron = false，则使用 Redis 中存储的 BloomFilter:UpdateCron
     */
    String updateCron() default "0 0 3 * * ?";

    /**
     * 是否使用配置文件中的 updateCron 配置（设置为true，则优先使用全局配置中的updateCron；配置文件中 updateCron 为 null 则使用注解配置）
     * 注意：如果配置文件中设置 resetUpdateCron = false，则使用 Redis 中存储的 BloomFilter:UpdateCron
     */
    boolean globalConfigCron() default true;
}

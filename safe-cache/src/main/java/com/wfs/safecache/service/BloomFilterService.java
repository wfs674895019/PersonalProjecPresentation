package com.wfs.safecache.service;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wfs.safecache.annotation.BloomFilter;
import com.wfs.safecache.config.MyRedisAutoConfiguration;
import com.wfs.safecache.entity.BloomFilterEntity;
import com.wfs.safecache.myEnum.RedisKeysEnum;
import com.wfs.safecache.properties.MyRedisProperties;
import com.wfs.safecache.properties.SafeCacheBloomFilterProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

@Service
@Slf4j
public class BloomFilterService {
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    SafeCacheBloomFilterProperties bloomFilterProperties;
    @Autowired
    private MyRedisProperties myRedisProperties;
    @Autowired
    private MyRedisAutoConfiguration myRedisAutoConfiguration;

    //所有的@BloomFilter注解
    private List<BloomFilter> bloomFilterList;

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
     * 获得所有BloomFilter注解对应的BloomFilterEntity，布隆过滤器参数为从redis中取到的新值
     */
    public List<BloomFilterEntity> getAllBloomFilterEntity() {
        List<BloomFilterEntity> bloomFilterEntityList = new ArrayList<>();

        List<BloomFilter> allBloomFilterAnnotation = getAllBloomFilterAnnotation();//获得所有的BloomFilter注解
        for (BloomFilter annotation : allBloomFilterAnnotation) {
            String[] bloomFilterNames = annotation.bloomFilterName();

            for (int i = 0; i < bloomFilterNames.length; i++) {
                BloomFilterEntity bloomFilterEntity;

                //获取bloomFilterEntity
                bloomFilterEntity = getBloomFilterEntityByAnnotation(annotation, i);

                //加到list里
                bloomFilterEntityList.add(bloomFilterEntity);
            }
        }

        return bloomFilterEntityList;
    }

    /**
     * 获得bloomFilterName对应的@BloomFilter
     */
    public BloomFilter getBloomFilterAnnotationByBloomFilterName(String bloomFilterName) {
        List<BloomFilter> allBloomFilterAnnotation = getAllBloomFilterAnnotation();//获得所有的BloomFilter注解

        for (BloomFilter annotation : allBloomFilterAnnotation) {
            String[] bloomFilterNames = annotation.bloomFilterName();
            for (int i = 0; i < bloomFilterNames.length; i++) {
                if (annotation.bloomFilterName()[i].equals(bloomFilterName)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    /**
     * 根据@BloomFilter和数组的标号获取BloomFilterEntity，如果Redis中存在参数异常，异常信息会存进BloomFilterEntity.errorMessage中
     *
     * @param annotation @BloomFilter注解
     * @param i          数组中的标号
     * @return BloomFilterEntity
     */
    private BloomFilterEntity getBloomFilterEntityByAnnotation(BloomFilter annotation, int i) {
        BloomFilterEntity bloomFilterEntity = new BloomFilterEntity();

        bloomFilterEntity.setBusinessName(annotation.businessName());
        bloomFilterEntity.setBloomFilterName(annotation.bloomFilterName()[i]);
        bloomFilterEntity.setDataOfBloomFilter(annotation.dataOfBloomFilter()[i]);
        bloomFilterEntity.setInitOfEntity(annotation.InitOfEntity()[i]);
        bloomFilterEntity.setInitOfFieldName(annotation.InitOfFieldName()[i]);

        //布隆过滤器的参数可能被更新了，所以要从redissonClient中取参数，不能取注解中的参数
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(RedisKeysEnum.bloomFilterPrefix + annotation.bloomFilterName()[i]);
        if (bloomFilter.isExists()) {//调试发现，布隆过滤器本体和配置信息，有一个存在于Redis中，就会为true；配置信息不存在下面set值时会报错
            bloomFilterEntity.setExists(true);
            try {
                //如果有人直接从Redis中修改了参数，可能会出现参数类型不匹配（比如应该是数字的，有人给改成了字母）
                bloomFilterEntity.setExpectedInsertions(bloomFilter.getExpectedInsertions());
                bloomFilterEntity.setFalseProbability(bloomFilter.getFalseProbability());
                bloomFilterEntity.setSize(bloomFilter.getSize());
                bloomFilterEntity.setCount(bloomFilter.count());
                bloomFilterEntity.setHashIterations(bloomFilter.getHashIterations());

                //获取查询和误判的统计数量
                String numberOfQueries = redisTemplate.opsForValue().get(RedisKeysEnum.bloomFilterPrefix + annotation.bloomFilterName()[i] + RedisKeysEnum.numberOfQueriesSuffix);
                String numberOfFalse = redisTemplate.opsForValue().get(RedisKeysEnum.bloomFilterPrefix + annotation.bloomFilterName()[i] + RedisKeysEnum.numberOfFalseSuffix);
                if (StringUtils.hasText(numberOfQueries) && StringUtils.hasText(numberOfFalse)) {
                    long qNum = Long.parseLong(numberOfQueries);
                    long fNum = Long.parseLong(numberOfFalse);
                    bloomFilterEntity.setNumberOfQueries(qNum);
                    bloomFilterEntity.setNumberOfFalse(fNum);
                    bloomFilterEntity.setFalseRate((double) fNum / (double) qNum);
                } else {
                    throw new RuntimeException("NumberOfQueries/NumberOfFalse数据丢失!");
                }

                //如果布隆过滤器本体被人搞破坏从Redis中给删了
                if (Boolean.FALSE.equals(redisTemplate.hasKey(RedisKeysEnum.bloomFilterPrefix + bloomFilterEntity.getBloomFilterName()))) {
                    throw new RuntimeException("布隆过滤器本体丢失!");
                }
            } catch (Exception e) {
                log.error("业务名称'{}', 布隆过滤器'{}', Redis中数据有误!", bloomFilterEntity.getBusinessName(), bloomFilterEntity.getBloomFilterName(), e);
                bloomFilterEntity.setErrorMessage("业务名称: " + bloomFilterEntity.getBusinessName() + ", 布隆过滤器: " + bloomFilterEntity.getBloomFilterName() + ", Redis中数据有误! " + e.getMessage());
            }
        } else {
            bloomFilterEntity.setExists(false);
        }

        //获取定时任务的cron
        bloomFilterEntity.setUpdateCron((String) redisTemplate.opsForHash().get(RedisKeysEnum.updateCron, annotation.bloomFilterName()[i]));
        return bloomFilterEntity;
    }

    /**
     * 获得所有的BloomFilter注解（单例模式）
     */
    public List<BloomFilter> getAllBloomFilterAnnotation() {
        if (bloomFilterList == null) {

            bloomFilterList = new ArrayList<>();
            String[] beanNames = applicationContext.getBeanDefinitionNames();// 获取所有beanNames
            for (String beanName : beanNames) {
                Object bean = applicationContext.getBean(beanName);// 获取所有bean
                Class<?> beanClass = bean.getClass();
                Method[] methods = beanClass.getDeclaredMethods();// 获取bean的所有方法
                for (Method method : methods) {
                    BloomFilter annotation = AnnotationUtils.findAnnotation(method, BloomFilter.class);//每个方法上找有没有注解
                    if (annotation != null) {// 查找到@BloomFilter
                        bloomFilterList.add(annotation);
                    }
                }
            }
        }
        return bloomFilterList;
    }

    /**
     * 更新布隆过滤器
     *
     * @param bloomFilterName    布隆过滤器名称
     * @param expectedInsertions 布隆过滤器预期插入数据量，为null则优先用Redis中的expectedInsertions，Redis中不存在则用注解参数中的
     * @param falseProbability   布隆过滤器预期误判率，为null则优先用Redis中的falseProbability，Redis中不存在则用注解参数中的
     */
    public void update(String bloomFilterName, Long expectedInsertions, Double falseProbability) throws Exception {

        List<BloomFilter> allBloomFilterAnnotation = getAllBloomFilterAnnotation();//获得所有的BloomFilter注解

        for (BloomFilter annotation : allBloomFilterAnnotation) {
            String[] bloomFilterNames = annotation.bloomFilterName();
            for (int i = 0; i < bloomFilterNames.length; i++) {
                if (annotation.bloomFilterName()[i].equals(bloomFilterName)) {//找到布隆过滤器
                    log.info("业务名称'{}', 布隆过滤器'{}', 开始更新", annotation.businessName(), bloomFilterName);

                    //如果传参是null，那么用Redis中的值/注解的值（用于传参是null的情况）
                    if (expectedInsertions == null || falseProbability == null) {
                        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(RedisKeysEnum.bloomFilterPrefix + bloomFilterName);
                        if (bloomFilter.isExists()) {//如果布隆过滤器存在，获取Redis中的最新值
                            try {
                                expectedInsertions = (expectedInsertions != null ? expectedInsertions : bloomFilter.getExpectedInsertions());
                                falseProbability = (falseProbability != null ? falseProbability : bloomFilter.getFalseProbability());
                            }
                            //如果出现参数转换失败（例如有人在Redis中改了数字变成字母，或者直接删除了字段会报java.lang.IllegalStateException: Bloom filter is not initialized!），这时用注解的值
                            catch (Exception e) {
                                expectedInsertions = (expectedInsertions != null ? expectedInsertions : annotation.InitOfExpectedInsertions()[i]);
                                falseProbability = (falseProbability != null ? falseProbability : annotation.InitOfFalseProbability()[i]);
                            }
                        } else {//如果布隆过滤器不存在，说明这个布隆过滤器已经被删除，用注解的值
                            expectedInsertions = (expectedInsertions != null ? expectedInsertions : annotation.InitOfExpectedInsertions()[i]);
                            falseProbability = (falseProbability != null ? falseProbability : annotation.InitOfFalseProbability()[i]);
                        }
                    }
                    initBloomFilter(annotation.businessName(), bloomFilterName, expectedInsertions, falseProbability, annotation.InitOfEntity()[i], annotation.InitOfFieldName()[i]);
                    return;
                }
            }
        }
        throw new IllegalArgumentException(bloomFilterName + "不存在");
    }

    /**
     * 初始化布隆过滤器
     *
     * @param businessName       业务名称
     * @param bloomFilterName    布隆过滤器名称
     * @param expectedInsertions 布隆过滤器预期插入数据量
     * @param falseProbability   布隆过滤器预期误判率
     * @param entity             插入进布隆过滤器的实体类
     * @param fieldName          插入进布隆过滤器的实体类的字段
     */
    public void initBloomFilter(String businessName, String bloomFilterName, long expectedInsertions, double falseProbability, Class<?> entity, String fieldName) throws Exception {
        //布隆过滤器初始化时，用读锁 锁住
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(RedisKeysEnum.bloomFilterLockPrefix + bloomFilterName);//TODO StampLock
        readWriteLock.writeLock().lock();

        RBloomFilter<String> bloomFilter = null;
        try {
            bloomFilter = redissonClient.getBloomFilter(RedisKeysEnum.bloomFilterPrefix + bloomFilterName);

            //如果bloomFilter已经存在，那么删除，然后重建
            if (bloomFilter.isExists()) {
                log.info("业务名称'{}', 布隆过滤器'{}'已存在, 开始删除重建", businessName, bloomFilterName);
                bloomFilter.delete();
            }

            bloomFilter.tryInit(expectedInsertions, falseProbability);// 创建布隆过滤器

            //初始化布隆过滤器，插入数据
            log.info("业务名称'{}', 布隆过滤器'{}'创建成功, ExpectedInsertions={}, FalseProbability={}, 开始初始化", businessName, bloomFilterName, expectedInsertions, falseProbability);
            addBloomFilterData(businessName, bloomFilter, entity, fieldName);

            //更新统计信息
            redisTemplate.opsForValue().set(RedisKeysEnum.bloomFilterPrefix + bloomFilterName + RedisKeysEnum.numberOfQueriesSuffix, "0");// 记录查询次数
            redisTemplate.opsForValue().set(RedisKeysEnum.bloomFilterPrefix + bloomFilterName + RedisKeysEnum.numberOfFalseSuffix, "0");// 记录误判次数
            log.info("业务名称'{}', 布隆过滤器'{}'初始化成功", businessName, bloomFilterName);
        } catch (Exception e1) {
            log.error("业务名称'{}', 布隆过滤器'{}'初始化失败!", businessName, bloomFilterName, e1);
            try {
                delete(bloomFilter);//删除初始化失败的布隆过滤器和统计信息
            } catch (Exception e2) {
                log.error("业务名称'{}', 布隆过滤器'{}'删除失败! 请手动删除初始化失败的布隆过滤器及统计信息!", businessName, bloomFilterName, e2);
                throw e2;
            }
            throw e1;
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * 删除布隆过滤器和统计信息（对接controller层）
     */
    public void delete(String bloomFilterName) {
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(RedisKeysEnum.bloomFilterLockPrefix + bloomFilterName);
        readWriteLock.writeLock().lock();
        try {
            RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(RedisKeysEnum.bloomFilterPrefix + bloomFilterName);
            delete(bloomFilter);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * 删除布隆过滤器和统计信息（初始化失败时调用，调用这个方法的代码块已经在锁里面了，不需要再加锁）
     */
    public void delete(RBloomFilter<String> bloomFilter) {
        if (bloomFilter != null) {
            bloomFilter.delete();
            redisTemplate.delete(Arrays.asList(
                    RedisKeysEnum.bloomFilterPrefix + bloomFilter.getName() + RedisKeysEnum.numberOfQueriesSuffix,
                    RedisKeysEnum.bloomFilterPrefix + bloomFilter.getName() + RedisKeysEnum.numberOfFalseSuffix));
            log.info("布隆过滤器'{}'已删除", bloomFilter.getName());
        }
    }

    /**
     * 验证@BloomFilter注解参数是否符合要求（要求数组中数量一致）
     *
     * @param annotation @BloomFilter注解
     * @return 验证通过true，不通过false
     */
    public boolean verify(BloomFilter annotation) {
        String[] bloomFilterName = annotation.bloomFilterName();
        String[] dataOfBloomFilter = annotation.dataOfBloomFilter();
        long[] expectedInsertions = annotation.InitOfExpectedInsertions();
        double[] falseProbability = annotation.InitOfFalseProbability();
        Class<?>[] entity = annotation.InitOfEntity();
        String[] fieldName = annotation.InitOfFieldName();

        //数量不一致，不创建布隆过滤器
        return bloomFilterName.length == dataOfBloomFilter.length &&
                bloomFilterName.length == expectedInsertions.length &&
                bloomFilterName.length == falseProbability.length &&
                bloomFilterName.length == entity.length &&
                bloomFilterName.length == fieldName.length;
    }

    @Autowired
    DataSource dataSource;

    /**
     * 向布隆过滤器中添加数据
     *
     * @param businessName 业务名称
     * @param bloomFilter  布隆过滤器
     * @param entity       插入进布隆过滤器的实体类
     * @param fieldName    插入进布隆过滤器的实体类的字段
     */
    private void addBloomFilterData(String businessName, RBloomFilter<String> bloomFilter, Class<?> entity, String fieldName) throws Exception {
        String[] fieldNameArray = fieldName.split("\\+");//以+号为分割，获取要添加进布隆过滤器中的字段

        //获取表名
        TableName tableNameAnnotation = entity.getAnnotation(TableName.class);
        String tableName;
        if (tableNameAnnotation == null) {
            throw new RuntimeException("业务名称: " + businessName + ", 布隆过滤器: " + bloomFilter.getName() + ", Entity: " + entity + "中没有@TableName, 无法获取要操作的数据库表!");
        } else {
            tableName = tableNameAnnotation.value();
        }

        //获取列名
        List<String> columnList = new ArrayList<>();
        for (String f : fieldNameArray) {
            Field field = entity.getDeclaredField(f);//需要用getDeclaredField，getField只能拿public的；没有这个field就直接报错，项目启动停止，不用自己处理
            TableField tableField = field.getAnnotation(TableField.class);//有没有@TableField
            if (tableField != null) {
                columnList.add(tableField.value());//有就获取列名
            } else {
                if (bloomFilterProperties.isMapUnderscoreToCamelCase()) {
                    columnList.add(convertToUnderlineCase(field.getName()));//没有就字段名驼峰式转下划线形式
                } else {
                    columnList.add(field.getName());//没有就直接用字段名
                }
            }
        }

        JdbcTemplate template = new JdbcTemplate(dataSource);

        //查询数据条数
        String countSql = "select count(*) from " + tableName;
        int count = template.queryForObject(countSql, Integer.class);

        int currentPage = 1;
        int pageSize = bloomFilterProperties.getMaxAmountOfInsertedData();
        int totalPage = (int) Math.ceil((double) count / pageSize);
        while (currentPage <= totalPage) {
            //拼接查询语句，SELECT `name`,`age` FROM `person` LIMIT 0,20
            StringBuilder pageSql = new StringBuilder("SELECT ");
            for (int i = 0; i < columnList.size(); i++) {
                pageSql.append('`').append(columnList.get(i)).append('`');
                if (i != columnList.size() - 1) {
                    pageSql.append(',');
                }
            }
            pageSql.append(" FROM ").append('`').append(tableName).append('`')
                    .append(" LIMIT ").append((currentPage - 1) * pageSize).append(',').append(pageSize);
            log.info("业务名称'{}', 布隆过滤器'{}', 查询sql: {}", businessName, bloomFilter.getName(), pageSql);

            //查询
            List<Map<String, Object>> maps = template.queryForList(pageSql.toString());

            //对每一条数据进行插入布隆过滤器
            for (Map<String, Object> map : maps) {
                StringBuilder dataOfBloomFilter = new StringBuilder();
                Collection<Object> values = map.values();
                for (Object value : values) {
                    dataOfBloomFilter.append(value);
                }
                bloomFilter.add(dataOfBloomFilter.toString());
            }

            log.info("业务名称'{}', 布隆过滤器'{}',插入数据: {}/{}", businessName, bloomFilter.getName(),
                    (currentPage - 1) * pageSize + maps.size(), count);

            currentPage++;
        }
    }

    /**
     * 驼峰式命名转下划线式命名
     */
    private String convertToUnderlineCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return "";
        }
        StringBuilder snakeCase = new StringBuilder();
        char[] chars = camelCase.toCharArray();

        for (char c : chars) {
            if (Character.isUpperCase(c)) {
                snakeCase.append("_").append(Character.toLowerCase(c));
            } else {
                snakeCase.append(c);
            }
        }
        return snakeCase.toString();
    }
}

//    /**
//     * 获取布隆过滤器ExpectedInsertion（进行百分比转换）
//     *
//     * @param expectedInsertions 布隆过滤器预期插入数据量
//     * @param mapper             插入进布隆过滤器的实体类的对应mapper
//     * @return 预期插入量 long
//     */
//    public <T> long getBloomFilterExpectedInsertions(String expectedInsertions, Class<?> mapper) {
//        if (expectedInsertions.contains("%")) {
//            long l = Long.parseLong(expectedInsertions.substring(0, expectedInsertions.indexOf('%')));
//            BaseMapper<T> baseMapper = (BaseMapper) applicationContext.getBean(mapper);
//            Long count = baseMapper.selectCount(null);
//            return count * l / 100;
//        } else {
//            return Long.parseLong(expectedInsertions);
//        }
//    }
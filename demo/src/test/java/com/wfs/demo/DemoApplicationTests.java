package com.wfs.demo;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wfs.demo.entity.Person;
import com.wfs.demo.mapper.PersonMapper;
import com.wfs.demo.service.PersonService;
import com.wfs.safecache.config.MyRedisAutoConfiguration;
import com.wfs.safecache.myEnum.RedisKeysEnum;
import com.wfs.safecache.properties.SafeCacheProperties;
import io.lettuce.core.resource.ClientResources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class DemoApplicationTests {

    @Autowired
    PersonService personService;
    @Autowired
    MyRedisAutoConfiguration myRedisAutoConfig;
    @Autowired
    PersonMapper personMapper;
    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;
    @Autowired
    RedisConnectionFactory redisConnectionFactory;
    @Autowired
    DataSource dataSource;

    @Test
    public void select2() {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        String sql="select * from person where id=1";
        List<Map<String, Object>> maps = template.queryForList(sql);
        System.out.println(maps);
    }

    @Test
    public void select() {
        IPage<Person> page = new Page<>(1, 2);
        QueryWrapper<Person> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("name","age_test");
        queryWrapper.eq("id",1);
//        personMapper.selectPage(page, queryWrapper);

//        for (Person record : page.getRecords()) {
//            System.out.println(record);
//        }
        Person person = personMapper.selectOne(queryWrapper);
        System.out.println(person);
    }

    @Test
    public void MyRedisTemplate() {
        StringRedisTemplate redisTemplate = myRedisAutoConfig.getMyRedisTemplate();
        redisTemplate.opsForValue().set("success", "abc");

        System.out.println(redisTemplate);
    }

    @Test
    public void addSchedule() {
        ScheduledFuture<?> schedule = taskScheduler.schedule(
                //1.添加任务内容(Runnable)
                () -> System.out.println(123),

                //2.设置执行周期(Trigger)
                triggerContext -> null
        );
        System.out.println(schedule);
    }

    @Test
    void insert() {
        personService.insertPerson("张三", 10, "abcdefg");
    }

    @Test
    void contextLoads() {
        IPage<Person> page = new Page<>(2, 10);
        personMapper.selectPage(page, null);
        List<Person> records = page.getRecords();
        for (Person record : records) {
            System.out.println(record.getId());
        }
    }

    @Autowired
    SafeCacheProperties annotationProperties;

    @Test
    void test() {
        Long timeout = annotationProperties.getTimeout();
        System.out.println(timeout);
        TimeUnit unit = annotationProperties.getUnit();
        System.out.println(unit);
        Boolean cluster = annotationProperties.getEnableDistributedLock();
        System.out.println(cluster);
    }

    CountDownLatch cdl = new CountDownLatch(3);

    @Test
    void test2() throws InterruptedException {
        MyThread thread1 = new MyThread();
        MyThread thread2 = new MyThread();
        MyThread thread3 = new MyThread();
        thread1.start();
        thread2.start();
        thread3.start();
        cdl.await();
    }

    class MyThread extends Thread {
        @Override
        public void run() {
            Person person = null;
//            person = personService.searchPerson(1);
            System.out.println(person);
            cdl.countDown();
        }
    }

    @Autowired
    RedissonClient redissonClient;

    @Test
    void bloom() {

        RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(RedisKeysEnum.bloomFilterPrefix + "SearchPersonBloomFilter2");
        System.out.println(bloomFilter.isExists());
        boolean b = bloomFilter.tryInit(10, 0.1);
        System.out.println(b);
        System.out.println(bloomFilter.isExists());
        boolean contains = bloomFilter.contains("1");
        System.out.println(contains);
    }

    @Test
    void bloom2() {
        RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(RedisKeysEnum.bloomFilterPrefix + "SearchPersonBloomFilter");
        bloomFilter.add("person:1");
    }

    @Autowired
    SqlSessionFactory sqlSessionFactory;

    @Test
    void bloom3() {
//        SqlSession sqlSession = new SqlSessionTemplate(sqlSessionFactory);
//        BaseMapper mapper = sqlSession.getMapper(PersonMapper.class);
//        LambdaQueryWrapper<Person> lambdaQueryWrapper=new LambdaQueryWrapper<>();
//        lambdaQueryWrapper.select(Person::getName);
//        List<Person> list = mapper.selectList(lambdaQueryWrapper);
//        System.out.println(list);
    }

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void fun() {
        String[] beanDefinitionNames = applicationContext.getBeanDefinitionNames();
        for (String beanDefinitionName : beanDefinitionNames) {
            System.out.println(beanDefinitionName);
        }
    }
}
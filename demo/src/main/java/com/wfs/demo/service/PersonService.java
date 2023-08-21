package com.wfs.demo.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wfs.demo.entity.Person;
import com.wfs.demo.mapper.PersonMapper;
import com.wfs.safecache.annotation.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class PersonService {
    @Autowired
    private PersonMapper personMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    //    @CacheHashSearch(cacheKey = "'person'", hashKey = "#id")
    @CacheStringSearch(cacheKey = "'person:'+#id", businessName = "searchPerson")
//    @BloomFilter(businessName = "searchPerson", bloomFilterName = "SearchPersonBloomFilter",
//            dataOfBloomFilter = "#id", expectedInsertions = "1000", falseProbability = 0.01,
//            mapper = PersonMapper.class, fieldName = "id")
    public Person searchPerson(Integer id) {
        String person = redisTemplate.opsForValue().get("person:" + id);
        if (person != null) {
            return JSON.parseObject(person, Person.class);
        } else {
            log.info(Thread.currentThread().getName() + "查询数据库");
            Person selectById = personMapper.selectById(id);
            redisTemplate.opsForValue().set("person:" + id, JSON.toJSONString(selectById));
            return selectById;
        }
    }

    @CacheStringSearch(cacheKey = "'person:'+#name", businessName = "searchByName")
    @BloomFilter(businessName = "searchByName", bloomFilterName = "searchByNameBloomFilter",
            dataOfBloomFilter = "#name", InitOfExpectedInsertions = 1000, InitOfFalseProbability = 0.5,
            InitOfEntity = Person.class, InitOfFieldName = "name")
    public List<Person> searchByName(String name) {
        String persons = redisTemplate.opsForValue().get("person:" + name);
        if (persons != null) {
            return JSON.parseObject(persons, List.class);
        } else {
            log.info(Thread.currentThread().getName() + "查询数据库");
            QueryWrapper<Person> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("name", name);
            List<Person> people = personMapper.selectList(queryWrapper);
            redisTemplate.opsForValue().set("person:" + name, JSON.toJSONString(people));
            return people;
        }
    }

    @BloomFilter(businessName = "searchByNameAndAge", bloomFilterName = "searchByNameAndAgeBloomFilter",
            dataOfBloomFilter = "#name+#age", InitOfExpectedInsertions = 10000, InitOfFalseProbability = 0.01,
            InitOfEntity = Person.class, InitOfFieldName = "name+age", updateCron = "0 0 4 * * ?", globalConfigCron = false)
//    @BloomFilter(businessName = "searchByNameAndAge", bloomFilterName = {"searchByNameBloomFilter", "searchByAgeBloomFilter"},
//            dataOfBloomFilter = {"#name", "#age"}, InitOfExpectedInsertions = {1000, 1000}, InitOfFalseProbability = {0.1, 0.1},
//            InitOfMapper = {PersonMapper.class, PersonMapper.class}, InitOfFieldName = {"name", "age"}, pattern = BloomFilterEnum.SINGLE_MATCH)
//    @Cacheable(value = "person-cache-able",key = "#name+#age",sync = true)
    @CacheStringSearch(businessName = "searchByNameAndAge", cacheKey = "'person:'+#name+#age", globalConfig = false, timeout = 30, unit = TimeUnit.MILLISECONDS, enableDistributedLock = true, saveNull = true, timeoutOfNull = 30, unitOfNull = TimeUnit.SECONDS)
//    @CacheHashSearch(businessName = "searchByNameAndAge",cacheKey = "'person'",hashKey = "#name+#age")
    public List<Person> searchByNameAndAge(String name, int age) {
        String persons = redisTemplate.opsForValue().get("person:" + name + "+" + age);
        if (persons != null) {
            return JSON.parseObject(persons, List.class);
        } else {
            log.info(Thread.currentThread().getName() + "查询数据库");
            LambdaQueryWrapper<Person> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Person::getName, name).eq(Person::getAge, age);
            List<Person> people = personMapper.selectList(queryWrapper);
            redisTemplate.opsForValue().set("person:" + name + "+" + age, JSON.toJSONString(people));
            return people;
        }
    }

    //    @AddDataToBloomFilter(businessName = "insertPerson", bloomFilterName = "searchByName", dataOfBloomFilter = "#name")
//    @AddDataToBloomFilter(businessName = "insertPerson", bloomFilterName = "searchByNameAndAgeBloomFilter", dataOfBloomFilter = "#name+#age")
    @AddDataToBloomFilters({
            @AddDataToBloomFilter(businessName = "insertPerson", bloomFilterName = "searchByName", dataOfBloomFilter = "#name"),
            @AddDataToBloomFilter(businessName = "insertPerson", bloomFilterName = "searchByNameAndAgeBloomFilter", dataOfBloomFilter = "#name+#age")})
    public void insertPerson(String name, int age, String message) {
        Person person = new Person();
        person.setName(name);
//        person.setAge(age);
        person.setMessage(message);
        personMapper.insert(person);
    }
}
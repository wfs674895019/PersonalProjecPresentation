package com.wfs.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wfs.demo.entity.Person;
import com.wfs.demo.mapper.PersonMapper;
import com.wfs.safecache.annotation.AddDataToBloomFilter;
import com.wfs.safecache.annotation.BloomFilter;
import com.wfs.safecache.annotation.CacheStringSearch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class PersonService {
    @Autowired
    private PersonMapper personMapper;

    /**
     * 通过姓名查询
     */
    @CacheStringSearch(cacheKey = "'person:'+#name", businessName = "searchByName")
    @BloomFilter(businessName = "searchByName", bloomFilterName = "searchByNameBloomFilter",
            dataOfBloomFilter = "#name", InitOfExpectedInsertions = 1000, InitOfFalseProbability = 0.01,
            InitOfEntity = Person.class, InitOfFieldName = "name")
    public List<Person> searchByName(String name) {
        log.info("查询数据库");
        QueryWrapper<Person> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", name);
        List<Person> people = personMapper.selectList(queryWrapper);
        return people;
    }

    /**
     * 通过姓名和年龄查询
     */
    @BloomFilter(businessName = "searchByNameAndAge", bloomFilterName = "searchByNameAndAgeBloomFilter",
            dataOfBloomFilter = "#name+#age", InitOfExpectedInsertions = 1000, InitOfFalseProbability = 0.01,
            InitOfEntity = Person.class, InitOfFieldName = "name+age")
    @CacheStringSearch(businessName = "searchByNameAndAge", cacheKey = "'person:'+#name+#age")
    public List<Person> searchByNameAndAge(String name, int age) {
        log.info("查询数据库");
        QueryWrapper<Person> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", name).eq("age", age);
        return personMapper.selectList(queryWrapper);
    }

    /**
     * 新增数据
     */
    @AddDataToBloomFilter(businessName = "insertPerson", bloomFilterName = "searchByNameBloomFilter", dataOfBloomFilter = "#name")
    @AddDataToBloomFilter(businessName = "insertPerson", bloomFilterName = "searchByNameAndAgeBloomFilter", dataOfBloomFilter = "#name+#age")
    public void insertPerson(String name, int age, String message) {
        Person person = new Person();
        person.setName(name);
        person.setAge(age);
        person.setMessage(message);
        personMapper.insert(person);
    }
}
package com.wfs.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wfs.demo.entity.Person;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PersonMapper extends BaseMapper<Person> {
}

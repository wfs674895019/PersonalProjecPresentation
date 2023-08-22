package com.wfs.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("person")
public class Person implements Serializable {
    private static final long serialVersionUID = 1L;//定义程序序列化ID

    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer age;
    private String name;
    private String message;
}

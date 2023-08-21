package com.wfs.safecache.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class BFDeleteVo {

    @NotBlank(message = "bloomFilterName不能为Null or 空字符串")
    String bloomFilterName;

    String permissionPassword;
}
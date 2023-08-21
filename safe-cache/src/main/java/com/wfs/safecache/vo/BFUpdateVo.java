package com.wfs.safecache.vo;

import com.wfs.safecache.validator.annotation.FalseProbabilityValidate;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@Data
public class BFUpdateVo {

    @NotBlank(message = "bloomFilterName不能为Null or 空字符串")
    String bloomFilterName;

    //    @ExpectedInsertionsValidate(message = "expectedInsertions需要为：数字 or 数字%")
    @Min(value = 0, message = "expectedInsertions不能小于0")
    Long expectedInsertions;

    @FalseProbabilityValidate(message = "falseProbability应为数字且范围为：0 ≤ falseProbability < 1")
    Double falseProbability;

    String permissionPassword;
}
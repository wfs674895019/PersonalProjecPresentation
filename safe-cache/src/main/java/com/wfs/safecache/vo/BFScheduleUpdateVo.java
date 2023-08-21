package com.wfs.safecache.vo;

import com.wfs.safecache.validator.annotation.CronValidate;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class BFScheduleUpdateVo {

    @NotBlank(message = "bloomFilterName不能为Null or 空字符串")
    String bloomFilterName;

    @CronValidate(message = "cron不符合格式要求")
    String cron;

    String permissionPassword;
}

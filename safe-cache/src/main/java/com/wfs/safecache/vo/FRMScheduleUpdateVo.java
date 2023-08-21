package com.wfs.safecache.vo;

import com.wfs.safecache.validator.annotation.CronValidate;
import lombok.Data;

/**
 * 误判率监视器定时任务的UpdateVo
 */
@Data
public class FRMScheduleUpdateVo {

    @CronValidate(message = "cron不符合格式要求")
    String cron;

    String permissionPassword;
}

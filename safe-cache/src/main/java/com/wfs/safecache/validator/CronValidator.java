package com.wfs.safecache.validator;

import com.wfs.safecache.validator.annotation.CronValidate;
import org.quartz.CronExpression;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class CronValidator implements ConstraintValidator<CronValidate, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        //涉及版权，不予展示

        return false;
    }
}
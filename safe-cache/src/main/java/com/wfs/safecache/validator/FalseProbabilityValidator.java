package com.wfs.safecache.validator;

import com.wfs.safecache.validator.annotation.FalseProbabilityValidate;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class FalseProbabilityValidator implements ConstraintValidator<FalseProbabilityValidate, Double> {

    @Override
    public boolean isValid(Double value, ConstraintValidatorContext context) {
//涉及版权，不予展示
        return false;
    }
}

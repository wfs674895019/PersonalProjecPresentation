package com.wfs.safecache.validator;

import com.wfs.safecache.validator.annotation.FalseProbabilityValidate;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class FalseProbabilityValidator implements ConstraintValidator<FalseProbabilityValidate, Double> {

    @Override
    public boolean isValid(Double value, ConstraintValidatorContext context) {
        if (value != null) {
            return value >= 0 && value < 1;
        } else {
            return true;
        }
    }
}

package com.wfs.safecache.validator.annotation;

import com.wfs.safecache.validator.FalseProbabilityValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * falseProbability应为数字且范围为：0 ≤ falseProbability < 1，可以为null
 */
@Target({PARAMETER, FIELD})
@Retention(RUNTIME)
@Constraint(validatedBy = FalseProbabilityValidator.class)
public @interface FalseProbabilityValidate {
//涉及版权，不予展示
}

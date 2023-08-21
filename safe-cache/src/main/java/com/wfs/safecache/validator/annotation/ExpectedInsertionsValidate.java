//package com.wfs.safecache.validator.annotation;
//
//import com.wfs.safecache.validator.ExpectedInsertionsValidator;
//
//import javax.validation.Constraint;
//import javax.validation.Payload;
//import java.lang.annotation.Retention;
//import java.lang.annotation.Target;
//
//import static java.lang.annotation.ElementType.FIELD;
//import static java.lang.annotation.ElementType.PARAMETER;
//import static java.lang.annotation.RetentionPolicy.RUNTIME;
//
///**
// * expectedInsertions需要为：数字 or 数字%，可以为null
// */
//@Target({PARAMETER, FIELD})
//@Retention(RUNTIME)
//@Constraint(validatedBy = ExpectedInsertionsValidator.class)
//public @interface ExpectedInsertionsValidate {
//
//    String message() default "";
//
//    Class<?>[] groups() default {};
//
//    Class<? extends Payload>[] payload() default {};
//}

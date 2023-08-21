//package com.wfs.safecache.validator;
//
//import com.wfs.safecache.validator.annotation.ExpectedInsertionsValidate;
//import org.springframework.util.StringUtils;
//
//import javax.validation.ConstraintValidator;
//import javax.validation.ConstraintValidatorContext;
//
//public class ExpectedInsertionsValidator implements ConstraintValidator<ExpectedInsertionsValidate, String> {
//
//    @Override
//    public boolean isValid(String value, ConstraintValidatorContext context) {
//        if (StringUtils.hasText(value)) {
//            try {
//                if (value.contains("%")) {
//                    if (value.indexOf('%') != value.length() - 1) {
//                        return false;
//                    }
//                    Long.parseLong(value.substring(0, value.indexOf('%')));
//                } else {
//                    Long.parseLong(value);
//                }
//                return true;
//            } catch (NumberFormatException e) {
//                return false;
//            }
//        } else {
//            return true;
//        }
//    }
//}

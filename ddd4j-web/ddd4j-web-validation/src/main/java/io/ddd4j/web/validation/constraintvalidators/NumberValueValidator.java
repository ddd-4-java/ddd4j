package io.ddd4j.web.validation.constraintvalidators;


import io.ddd4j.web.validation.constraints.NumberValue;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据校验注解实现类
 *
 * @author hiwepy
 * @since 2021-03-08
 */
public class NumberValueValidator implements ConstraintValidator<NumberValue, String> {

    private NumberValue numberValue;

    @Override
    public void initialize(NumberValue annotation) {
        this.numberValue = annotation;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        boolean flag = false;
        String regex = numberValue.regex();
        flag = validate(value, regex);
        if (!flag) {
            constraintValidatorContext.disableDefaultConstraintViolation();
            constraintValidatorContext.buildConstraintViolationWithTemplate(numberValue.message()).addConstraintViolation();
        }
        return flag;
    }

    private boolean validate(String str, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(str);
        return matcher.matches();
    }
}

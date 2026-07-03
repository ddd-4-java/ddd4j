package io.ddd4j.extension.validation.constraints;

import io.ddd4j.extension.validation.constraintvalidators.NumberValueValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 数值格式校验注解
 *
 * <p>校验字符串是否符合指定的数值正则表达式格式。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Constraint(validatedBy = {NumberValueValidator.class})
public @interface NumberValue {

    String regex() default "^[0-9\\-]+$";

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

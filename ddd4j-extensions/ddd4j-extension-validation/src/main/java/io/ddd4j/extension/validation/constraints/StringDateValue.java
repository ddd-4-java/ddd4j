package io.ddd4j.extension.validation.constraints;

import io.ddd4j.extension.validation.constraintvalidators.StringDateValueValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 字符串日期格式校验注解
 *
 * <p>校验字符串是否符合指定的日期格式（如 yyyy-MM-dd），使用严格的日期解析。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Constraint(validatedBy = {StringDateValueValidator.class})
public @interface StringDateValue {

    String pattern() default "yyyy-MM-dd";

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

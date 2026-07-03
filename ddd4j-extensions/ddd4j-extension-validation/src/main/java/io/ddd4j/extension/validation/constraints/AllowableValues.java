package io.ddd4j.extension.validation.constraints;


import io.ddd4j.extension.validation.constraintvalidators.AllowedValuesValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 允许值校验注解
 *
 * <p>校验字段的值是否在指定的允许值列表中，支持空值校验。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Constraint(validatedBy = AllowedValuesValidator.class)
public @interface AllowableValues {

    String message() default "invalid values";

    String allows() default "";

    boolean nullable() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}

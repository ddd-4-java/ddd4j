package io.ddd4j.data.datascope.annotation;

import io.ddd4j.data.datascope.RequiresDataPermissionsValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a value is visible under the current data permission scope.
 */
@Documented
@Constraint(validatedBy = RequiresDataPermissionsValidator.class)
@Target({
        ElementType.METHOD,
        ElementType.FIELD,
        ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR,
        ElementType.PARAMETER,
        ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresDataPermissions {

    /**
     * Business data type, such as dept, tenant, region, or project.
     */
    String dataType();

    String message() default "数据未授权";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

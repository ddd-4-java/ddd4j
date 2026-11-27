package io.ddd4j.data.datascope.annotation;

import io.ddd4j.data.datascope.RequiresDataPermissionsValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

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
     *
     * @return 当前操作得到的 <code>String</code> 结果
     */
    String dataType();

    /**
     * 执行 <code>message</code> 对应的处理。
     *
     * @return 当前操作得到的 <code>String</code> 结果
     */
    String message() default "数据未授权";

    /**
     * 提供测试夹具的空操作。
     *
     * @return 当前操作得到的 <code>Class&lt;?&gt;[]</code> 结果
     */
    Class<?>[] groups() default {};

    /**
     * 提供测试夹具的空操作。
     *
     * @return 当前操作得到的 <code>Class&lt;? extends Payload&gt;[]</code> 结果
     */
    Class<? extends Payload>[] payload() default {};
}

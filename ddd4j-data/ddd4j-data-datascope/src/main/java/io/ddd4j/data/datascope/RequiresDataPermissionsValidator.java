package io.ddd4j.data.datascope;

import io.ddd4j.data.datascope.annotation.RequiresDataPermissions;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

/**
 * Bean Validation validator for {@link RequiresDataPermissions}.
 */
public class RequiresDataPermissionsValidator implements ConstraintValidator<RequiresDataPermissions, Object> {

    /** 保存 <code>provider</code> 对应的 <code>DataScopeProvider</code> 数据。 */
    private final DataScopeProvider provider;
    /** 保存 <code>data Type</code> 对应的 <code>String</code> 数据。 */
    private String dataType;

    /** 初始化 <code>RequiresDataPermissionsValidator</code> 并委托其默认构造路径。 */
    public RequiresDataPermissionsValidator() {
        this(DataScopeProvider.nonNullAllowed());
    }

    /**
     * 初始化 <code>RequiresDataPermissionsValidator</code> 使用的 <code>provider</code>。
     *
     * @param provider 传入此操作的 <code>DataScopeProvider</code> 类型 <code>provider</code>
     */
    public RequiresDataPermissionsValidator(DataScopeProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
    }

    /**
     * 执行 <code>initialize</code>，调用 <code>annotation.dataType</code>。
     *
     * @param annotation 传入此操作的 <code>RequiresDataPermissions</code> 类型 <code>annotation</code>
     */
    @Override
    public void initialize(RequiresDataPermissions annotation) {
        this.dataType = annotation.dataType();
    }

    /**
     * 执行 <code>is Valid</code>，调用 <code>provider.hasPermissions</code>。
     *
     * @param data 随响应携带的业务数据
     * @param context 传入此操作的 <code>ConstraintValidatorContext</code> 类型 <code>context</code>
     * @return 返回表达式 <code>Objects.nonNull(data) &amp;&amp; provider.hasPermissions(dataType, data)</code>
     */
    @Override
    public boolean isValid(Object data, ConstraintValidatorContext context) {
        return Objects.nonNull(data) && provider.hasPermissions(dataType, data);
    }
}

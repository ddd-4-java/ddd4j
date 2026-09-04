package io.ddd4j.data.datascope;

import io.ddd4j.data.datascope.annotation.RequiresDataPermissions;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import java.util.Objects;

/**
 * Bean Validation validator for {@link RequiresDataPermissions}.
 */
public class RequiresDataPermissionsValidator implements ConstraintValidator<RequiresDataPermissions, Object> {

    private final DataScopeProvider provider;
    private String dataType;

    public RequiresDataPermissionsValidator() {
        this(DataScopeProvider.nonNullAllowed());
    }

    public RequiresDataPermissionsValidator(DataScopeProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
    }

    @Override
    public void initialize(RequiresDataPermissions annotation) {
        this.dataType = annotation.dataType();
    }

    @Override
    public boolean isValid(Object data, ConstraintValidatorContext context) {
        return Objects.nonNull(data) && provider.hasPermissions(dataType, data);
    }
}

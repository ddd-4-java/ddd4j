package io.ddd4j.auth.datascope;

import io.ddd4j.auth.datascope.annotation.RequiresDataPermissions;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.biz.utils.SpringContextUtils;

import java.util.Objects;

/**
 * 数据权限校验
 * Data permission verification
 *
 * @author wandl
 * @see RequiresDataPermissions
 */
@Slf4j
public class RequiresDataPermissionsValidator implements ConstraintValidator<RequiresDataPermissions, Object> {

    private String dataType;
    private DataScopeProvider provider;

    @Override
    public void initialize(RequiresDataPermissions annotation) {
        this.dataType = annotation.dataType();
        this.provider = SpringContextUtils.getContext().getApplicationContext().getBean(DataScopeProvider.class);
    }

    @Override
    public boolean isValid(Object data, ConstraintValidatorContext constraintValidatorContext) {
        // Check if the data has value
        if (Objects.isNull(data)) {
            return Boolean.FALSE;
        }
        // Get the data permission provider
        if (Objects.isNull(provider)) {
            log.warn("DataScopeProvider is not found.");
            return Boolean.FALSE;
        }
        // Check if the data has permissions
        return provider.hasPermissions(dataType, data);
    }

}

/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.auth.datascope;

import io.ddd4j.auth.datascope.annotation.RequiresDataPermissions;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
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

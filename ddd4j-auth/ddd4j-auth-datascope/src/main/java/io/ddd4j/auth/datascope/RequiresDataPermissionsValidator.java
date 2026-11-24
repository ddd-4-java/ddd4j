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
import org.springframework.extension.utils.SpringContextUtils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Objects;

/**
 * 数据权限校验
 * Data permission verification
 *
 * @author ddd4j
 * @see RequiresDataPermissions
 */
public class RequiresDataPermissionsValidator implements ConstraintValidator<RequiresDataPermissions, Object> {

    private io.ddd4j.data.datascope.RequiresDataPermissionsValidator delegate;

    @Override
    public void initialize(RequiresDataPermissions annotation) {
        DataScopeProvider provider = SpringContextUtils.getContext().getApplicationContext().getBean(DataScopeProvider.class);
        this.delegate = new io.ddd4j.data.datascope.RequiresDataPermissionsValidator(provider, annotation.dataType());
    }

    @Override
    public boolean isValid(Object data, ConstraintValidatorContext constraintValidatorContext) {
        return Objects.nonNull(delegate) && delegate.isValid(data, constraintValidatorContext);
    }

}

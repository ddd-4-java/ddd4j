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
package io.ddd4j.data.datascope;

import io.ddd4j.data.datascope.annotation.RequiresDataPermissions;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

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

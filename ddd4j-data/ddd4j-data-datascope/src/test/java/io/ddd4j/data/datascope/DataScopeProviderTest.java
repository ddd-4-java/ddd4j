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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeProviderTest {

    @Test
    void defaultProviderShouldAllowOnlyNonNullData() {
        DataScopeProvider provider = DataScopeProvider.nonNullAllowed();

        assertThat(provider.hasPermissions("dept", "001")).isTrue();
        assertThat(provider.hasPermissions("dept", null)).isFalse();
    }

    @Test
    void customProviderShouldOverrideDefaultProvider() {
        DataScopeProvider provider = (type, data) -> "tenant".equals(type) && "t1".equals(data);

        assertThat(provider.hasPermissions("tenant", "t1")).isTrue();
        assertThat(provider.hasPermissions("tenant", "t2")).isFalse();
    }

    @Test
    void validatorShouldDelegateToProvider() throws NoSuchFieldException {
        DataScopeProvider provider = (type, data) -> "project".equals(type) && "p1".equals(data);
        RequiresDataPermissionsValidator validator = new RequiresDataPermissionsValidator(provider);
        validator.initialize(Sample.class.getDeclaredField("project").getAnnotation(
                io.ddd4j.data.datascope.annotation.RequiresDataPermissions.class));

        assertThat(validator.isValid("p1", null)).isTrue();
        assertThat(validator.isValid("p2", null)).isFalse();
        assertThat(validator.isValid(null, null)).isFalse();
    }

    private static class Sample {

        @io.ddd4j.data.datascope.annotation.RequiresDataPermissions(dataType = "project")
        private String project;
    }
}

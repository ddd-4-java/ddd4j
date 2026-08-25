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

import java.util.Objects;

/**
 * Provides business-specific data permission decisions.
 */
@FunctionalInterface
public interface DataScopeProvider {

    /**
     * Default provider: preserves legacy behavior by accepting non-null values.
     */
    static DataScopeProvider nonNullAllowed() {
        return (dataType, data) -> Objects.nonNull(data);
    }

    /**
     * Returns whether the annotated data is permitted for the given data type.
     *
     * @param dataType data domain or resource type declared by the annotation
     * @param data     annotated value
     * @return true when the current context can access the data
     */
    boolean hasPermissions(String dataType, Object data);
}

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

package io.ddd4j.core.ddd.event;

import java.io.Serializable;

/**
 * 领域实体标识的最小契约。
 */
public interface EntityId extends Serializable {
    /**
     * \u8fd4\u56de\u5b9e\u4f53\u7c7b\u578b\u3002
     * @return \u5b9e\u4f53\u7c7b\u578b
     */
    EntityType getType();

    /**
     * \u8fd4\u56de\u4e0d\u5305\u542b\u7c7b\u578b\u524d\u7f00\u7684\u6807\u8bc6\u503c\u3002
     * @return \u6807\u8bc6\u503c
     */
    String asString();

    /**
     * \u8fd4\u56de\u5305\u542b\u7c7b\u578b\u524d\u7f00\u7684\u7a33\u5b9a\u6807\u8bc6\u503c\u3002
     * @return \u7c7b\u578b\u5316\u6807\u8bc6\u503c
     */
    String asTypedString();
}

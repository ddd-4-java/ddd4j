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
 * 实体类型的稳定标识。
 */
public interface EntityType extends Serializable {
    /**
     * \u8fd4\u56de\u53ef\u7528\u4e8e\u65e5\u5fd7\u3001\u5e8f\u5217\u5316\u548c\u7c7b\u578b\u5316\u6807\u8bc6\u7684\u6587\u672c\u3002
     * @return \u5b9e\u4f53\u7c7b\u578b\u6587\u672c
     */
    String asString();
}

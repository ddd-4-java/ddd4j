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

package io.ddd4j.core.cqrs.query;

/**
 * 查询属性所属的模型空间。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public enum PropertySpace {
    /** \u9886\u57df\u6a21\u578b\u5c5e\u6027\uff0c\u7531 Repository \u6620\u5c04\u4e3a\u6301\u4e45\u5316\u5c5e\u6027\u3002 */
    DOMAIN,
    /** \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\uff0c\u4ec5\u5141\u8bb8\u5728\u663e\u5f0f PO \u4f5c\u7528\u57df\u4e2d\u4f7f\u7528\u3002 */
    PERSISTENCE}

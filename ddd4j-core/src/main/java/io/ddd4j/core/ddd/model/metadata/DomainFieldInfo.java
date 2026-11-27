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
package io.ddd4j.core.ddd.model.metadata;

import lombok.Data;

import java.lang.reflect.Field;

/**
 * Domain Model 字段元数据（仿 MyBatis-Plus {@code TableFieldInfo}）。
 *
 * <p>充血查询链路中，业务方用 Domain Model 字段引用 Lambda
 * （如 {@code User::getUserName}），通过本类可查到该字段对应的 PO 数据库列名。
 *
 * <p>字段→列名的映射来源（优先级）：
 * <ol>
 *   <li>Domain 字段有 {@code @DomainField(column = "...")} → 用注解值</li>
 *   <li>Domain 字段有 {@code @DomainField(poField = "...")} → 通过 PO 元数据查对应字段的列名</li>
 *   <li>Domain 字段名 = PO 字段名（默认约定）→ 通过 PO 元数据查列名</li>
 *   <li>fallback：{@link #poColumn} 为 null，调用方决定（驼峰转下划线）</li>
 * </ol>
 *
 * @author ddd4j
 * @since 2.0.x
 */
@Data
public class DomainFieldInfo {

    /** \u5173\u8054\u9886\u57df\u5c5e\u6027\u7684 Java \u53cd\u5c04\u5b57\u6bb5\u3002 */
    private final Field field;
    /** Java \u9886\u57df\u6a21\u578b\u4e2d\u7684\u5c5e\u6027\u540d\u3002 */
    private final String property;
    /** \u5f53\u524d\u9886\u57df\u5b57\u6bb5\u5bf9\u5e94\u7684\u6301\u4e45\u5316\u5217\u540d\u3002 */
    private final String poColumn;

    /**
     * \u5efa\u7acb\u9886\u57df\u5b57\u6bb5\u4e0e\u6301\u4e45\u5316\u5217\u540d\u7684\u6620\u5c04\u3002
     * @param field \u9886\u57df\u6a21\u578b\u5b57\u6bb5
     * @param poColumn \u6301\u4e45\u5316\u5217\u540d\uff0c\u672a\u89e3\u6790\u65f6\u53ef\u4e3a null
     */
    public DomainFieldInfo(Field field, String poColumn) {
        this.field = field;
        this.property = field.getName();
        this.poColumn = poColumn;
    }

}
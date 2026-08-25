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
 * @author wandl
 * @since 2.0.x
 */
@Data
public class DomainFieldInfo {

    private final Field field;
    private final String property;
    private final String poColumn;

    public DomainFieldInfo(Field field, String poColumn) {
        this.field = field;
        this.property = field.getName();
        this.poColumn = poColumn;
    }

}
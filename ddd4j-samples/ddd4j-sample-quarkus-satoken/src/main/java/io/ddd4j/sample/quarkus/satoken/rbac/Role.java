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
package io.ddd4j.sample.quarkus.satoken.rbac;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * RBAC 角色实体。
 *
 * <p>演示用最小字段集：角色编码、显示名、描述、权限编码集合。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    /**
     * 角色编码（业务主键，如 {@code admin} / {@code user}）。
     */
    private String code;
    /**
     * 显示名。
     */
    private String displayName;
    /**
     * 描述。
     */
    private String description;
    /**
     * 角色拥有的权限编码集合。
     */
    private Set<String> permissionCodes = new HashSet<>();

}
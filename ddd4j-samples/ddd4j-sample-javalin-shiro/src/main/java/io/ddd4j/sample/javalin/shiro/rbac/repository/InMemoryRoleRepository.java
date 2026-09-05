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
package io.ddd4j.sample.javalin.shiro.rbac.repository;

import java.util.Objects;

import io.ddd4j.sample.javalin.shiro.rbac.domain.Role;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 角色仓储：内存实现。
 *
 * <p>仅用于演示 RBAC 完整 CRUD 流程。生产环境应替换为 JDBC / MyBatis / JPA 实现。
 *
 * <p>本类在所有 7 个示例（Spring/Quarkus/Javalin × Sa-Token/Shiro/Security）中<b>完全一致</b>，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class InMemoryRoleRepository {

    private final ConcurrentMap<String, Role> store = new ConcurrentHashMap<>();

    /**
     * 新增或更新角色。
     */
    public Role save(Role role) {
        store.put(role.code(), role);
        return role;
    }

    /**
     * 按编码查询角色。
     */
    public Optional<Role> findByCode(String code) {
        return Optional.ofNullable(store.get(code));
    }

    /**
     * 删除角色。
     */
    public boolean deleteByCode(String code) {
        return Objects.nonNull(store.remove(code));
    }

    /**
     * 查询全部角色。
     */
    public Collection<Role> findAll() {
        return Collections.unmodifiableCollection(store.values());
    }

    /**
     * 当前角色数量。
     */
    public int count() {
        return store.size();
    }

}
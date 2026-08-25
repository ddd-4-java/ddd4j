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
package io.ddd4j.sample.javalin.satoken.rbac.infrastructure;

import java.util.Objects;

import io.ddd4j.kit.lang.CollKit;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Role;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.RoleRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存版角色仓储（演示用）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class InMemoryRoleRepository implements RoleRepository {

    private final ConcurrentMap<String, RoleRow> rows = new ConcurrentHashMap<>();

    private static RoleRow toRow(Role role) {
        RoleRow row = new RoleRow();
        row.roleId = role.id();
        row.roleCode = role.getRoleCode();
        row.roleName = role.getRoleName();
        row.description = role.getDescription();
        row.status = role.getStatus().name();
        row.permissionIds = new ArrayList<>(role.getPermissionIds());
        return row;
    }

    private static Role toModel(RoleRow row) {
        Role role = new Role(row.roleId, row.roleCode, row.roleName, row.description, Role.Status.valueOf(row.status));
        if (CollKit.isNotEmpty(row.permissionIds)) {
            role.assignPermissions(new HashSet<>(row.permissionIds));
        }
        return role;
    }

    @Override
    public Optional<Role> findById(String id) {
        if (StrKit.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id)).map(InMemoryRoleRepository::toModel);
    }

    @Override
    public Optional<Role> findByRoleCode(String roleCode) {
        if (StrKit.isBlank(roleCode)) {
            return Optional.empty();
        }
        return rows.values().stream()
                .filter(r -> Objects.equals(roleCode, r.roleCode))
                .findFirst()
                .map(InMemoryRoleRepository::toModel);
    }

    @Override
    public List<Role> findAll() {
        return rows.values().stream()
                .map(InMemoryRoleRepository::toModel)
                .toList();
    }

    @Override
    public Role save(Role aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        rows.put(aggregate.id(), toRow(aggregate));
        return aggregate;
    }

    @Override
    public void deleteById(String id) {
        if (StrKit.isNotBlank(id)) {
            rows.remove(id);
        }
    }

    /**
     * 角色内部持久化行。
     */
    static class RoleRow {

        String roleId;
        String roleCode;
        String roleName;
        String description;
        String status;
        List<String> permissionIds;
    }
}

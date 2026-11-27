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

    /**
     * 按主键保存当前内存仓储中可读写的示例记录。
     */
    private final ConcurrentMap<String, RoleRow> rows = new ConcurrentHashMap<>();

    /**
     * 将领域模型的身份和业务字段映射为内存记录。
     *
     * @param role 待保存或对外返回的角色实体
     * @return 待保存的内存记录
     */
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

    /**
     * 将内存记录恢复为领域模型。
     *
     * @param row 当前仓储中的内存数据记录
     * @return 映射完成的领域聚合
     */
    private static Role toModel(RoleRow row) {
        Role role = new Role(row.roleId, row.roleCode, row.roleName, row.description, Role.Status.valueOf(row.status));
        if (CollKit.isNotEmpty(row.permissionIds)) {
            role.assignPermissions(new HashSet<>(row.permissionIds));
        }
        return role;
    }

    /**
     * 按主键在当前内存仓储查找对应聚合。
     *
     * @param id 待查询或移除的业务记录标识
     * @return 已找到的聚合，不存在时返回空结果
     */
    @Override
    public Optional<Role> findById(String id) {
        if (StrKit.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id)).map(InMemoryRoleRepository::toModel);
    }

    /**
     * 按角色编码查询角色。
     *
     * <p>此实现调用 <code>StrKit.isBlank(roleCode)</code>。</p>
     *
     * @param roleCode 角色编码
     * @return 实际返回<code>Optional.empty()</code>
     */
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

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>此实现调用 <code>rows.values().stream()</code>。</p>
     *
     * @return 满足条件的结果列表
     */
    @Override
    public List<Role> findAll() {
        return rows.values().stream()
                .map(InMemoryRoleRepository::toModel)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 写入或更新给定的领域对象或存储记录。
     *
     * <p>此实现调用 <code>rows.put(aggregate.id(), toRow(aggregate))</code>。</p>
     *
     * @param aggregate 传给 <code>rows.put(aggregate.id(), toRow(aggregate))</code>
     * @return 实际返回<code>aggregate</code>
     */
    @Override
    public Role save(Role aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        rows.put(aggregate.id(), toRow(aggregate));
        return aggregate;
    }

    /**
     * 按主键从当前内存仓储移除对应记录。
     *
     * @param id 待查询或移除的业务记录标识
     */
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

        /**
         * 角色记录的主键。
         */
        String roleId;
        /**
         * 在主体权限检查中使用的角色业务编码。
         */
        String roleCode;
        /**
         * 角色展示名称。
         */
        String roleName;
        /**
         * 当前角色或权限定义的说明。
         */
        String description;
        /**
         * 当前商品、账号或权限记录的业务状态。
         */
        String status;
        /**
         * 当前角色关联的权限标识列表。
         */
        List<String> permissionIds;
    }
}

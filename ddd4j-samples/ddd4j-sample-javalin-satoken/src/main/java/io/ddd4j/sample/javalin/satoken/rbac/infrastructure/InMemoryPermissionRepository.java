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

import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Permission;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.PermissionRepository;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存版权限仓储（演示用）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class InMemoryPermissionRepository implements PermissionRepository {

    /**
     * 按主键保存当前内存仓储中可读写的示例记录。
     */
    private final ConcurrentMap<String, PermissionRow> rows = new ConcurrentHashMap<>();

    /**
     * 将领域模型的身份和业务字段映射为内存记录。
     *
     * @param permission 待保存或对外返回的权限实体
     * @return 待保存的内存记录
     */
    private static PermissionRow toRow(Permission permission) {
        PermissionRow row = new PermissionRow();
        row.permissionId = permission.id();
        row.permissionCode = permission.getPermissionCode();
        row.permissionName = permission.getPermissionName();
        row.module = permission.getModule();
        row.status = permission.getStatus().name();
        return row;
    }

    /**
     * 将内存记录恢复为领域模型。
     *
     * @param row 当前仓储中的内存数据记录
     * @return 映射完成的领域聚合
     */
    private static Permission toModel(PermissionRow row) {
        return new Permission(row.permissionId, row.permissionCode, row.permissionName, row.module,
                Permission.Status.valueOf(row.status));
    }

    /**
     * 按主键在当前内存仓储查找对应聚合。
     *
     * @param id 待查询或移除的业务记录标识
     * @return 已找到的聚合，不存在时返回空结果
     */
    @Override
    public Optional<Permission> findById(String id) {
        if (StrKit.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id)).map(InMemoryPermissionRepository::toModel);
    }

    /**
     * 按权限编码查询权限。
     *
     * <p>此实现调用 <code>StrKit.isBlank(permissionCode)</code>。</p>
     *
     * @param permissionCode 权限编码
     * @return 实际返回<code>Optional.empty()</code>
     */
    @Override
    public Optional<Permission> findByPermissionCode(String permissionCode) {
        if (StrKit.isBlank(permissionCode)) {
            return Optional.empty();
        }
        return rows.values().stream()
                .filter(r -> Objects.equals(permissionCode, r.permissionCode))
                .findFirst()
                .map(InMemoryPermissionRepository::toModel);
    }

    /**
     * 查询指定模块下的权限条目。
     *
     * <p>此实现调用 <code>StrKit.isBlank(module)</code>。</p>
     *
     * @param module 所属模块
     * @return 实际返回<code>Collections.emptyList()</code>
     */
    @Override
    public List<Permission> findByModule(String module) {
        if (StrKit.isBlank(module)) {
            return Collections.emptyList();
        }
        return rows.values().stream()
                .filter(r -> Objects.equals(module, r.module))
                .map(InMemoryPermissionRepository::toModel)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>此实现调用 <code>rows.values().stream()</code>。</p>
     *
     * @return 满足条件的结果列表
     */
    @Override
    public List<Permission> findAll() {
        return rows.values().stream()
                .map(InMemoryPermissionRepository::toModel)
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
    public Permission save(Permission aggregate) {
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
     * 权限内部持久化行。
     */
    static class PermissionRow {

        /**
         * 权限记录的主键。
         */
        String permissionId;
        /**
         * 权限检查和关联使用的业务编码。
         */
        String permissionCode;
        /**
         * 权限展示名称。
         */
        String permissionName;
        /**
         * 当前权限所属的业务模块。
         */
        String module;
        /**
         * 当前商品、账号或权限记录的业务状态。
         */
        String status;
    }
}

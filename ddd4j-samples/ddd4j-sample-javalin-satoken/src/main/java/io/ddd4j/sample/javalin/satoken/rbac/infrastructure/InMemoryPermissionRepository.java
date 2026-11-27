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

    /** \u6309\u4e3b\u952e\u4fdd\u5b58\u5f53\u524d\u5185\u5b58\u4ed3\u50a8\u4e2d\u53ef\u8bfb\u5199\u7684\u793a\u4f8b\u8bb0\u5f55\u3002 */
    private final ConcurrentMap<String, PermissionRow> rows = new ConcurrentHashMap<>();

    /**
     * \u5c06\u9886\u57df\u6a21\u578b\u7684\u8eab\u4efd\u548c\u4e1a\u52a1\u5b57\u6bb5\u6620\u5c04\u4e3a\u5185\u5b58\u8bb0\u5f55\u3002
     * @param permission \u5f85\u4fdd\u5b58\u6216\u5bf9\u5916\u8fd4\u56de\u7684\u6743\u9650\u5b9e\u4f53
     * @return \u5f85\u4fdd\u5b58\u7684\u5185\u5b58\u8bb0\u5f55
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
     * \u5c06\u5185\u5b58\u8bb0\u5f55\u6062\u590d\u4e3a\u9886\u57df\u6a21\u578b\u3002
     * @param row \u5f53\u524d\u4ed3\u50a8\u4e2d\u7684\u5185\u5b58\u6570\u636e\u8bb0\u5f55
     * @return \u6620\u5c04\u5b8c\u6210\u7684\u9886\u57df\u805a\u5408
     */
    private static Permission toModel(PermissionRow row) {
        return new Permission(row.permissionId, row.permissionCode, row.permissionName, row.module,
                Permission.Status.valueOf(row.status));
    }

    /**
     * \u6309\u4e3b\u952e\u5728\u5f53\u524d\u5185\u5b58\u4ed3\u50a8\u67e5\u627e\u5bf9\u5e94\u805a\u5408\u3002
     * @param id \u5f85\u67e5\u8be2\u6216\u79fb\u9664\u7684\u4e1a\u52a1\u8bb0\u5f55\u6807\u8bc6
     * @return \u5df2\u627e\u5230\u7684\u805a\u5408\uff0c\u4e0d\u5b58\u5728\u65f6\u8fd4\u56de\u7a7a\u7ed3\u679c
     */
    @Override
    public Optional<Permission> findById(String id) {
        if (StrKit.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id)).map(InMemoryPermissionRepository::toModel);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public List<Permission> findAll() {
        return rows.values().stream()
                .map(InMemoryPermissionRepository::toModel)
                .collect(java.util.stream.Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override
    public Permission save(Permission aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        rows.put(aggregate.id(), toRow(aggregate));
        return aggregate;
    }

    /**
     * \u6309\u4e3b\u952e\u4ece\u5f53\u524d\u5185\u5b58\u4ed3\u50a8\u79fb\u9664\u5bf9\u5e94\u8bb0\u5f55\u3002
     * @param id \u5f85\u67e5\u8be2\u6216\u79fb\u9664\u7684\u4e1a\u52a1\u8bb0\u5f55\u6807\u8bc6
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

        /** \u6743\u9650\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        String permissionId;
        /** \u6743\u9650\u68c0\u67e5\u548c\u5173\u8054\u4f7f\u7528\u7684\u4e1a\u52a1\u7f16\u7801\u3002 */
        String permissionCode;
        /** \u6743\u9650\u5c55\u793a\u540d\u79f0\u3002 */
        String permissionName;
        /** \u5f53\u524d\u6743\u9650\u6240\u5c5e\u7684\u4e1a\u52a1\u6a21\u5757\u3002 */
        String module;
        /** \u5f53\u524d\u5546\u54c1\u3001\u8d26\u53f7\u6216\u6743\u9650\u8bb0\u5f55\u7684\u4e1a\u52a1\u72b6\u6001\u3002 */
        String status;
    }
}

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
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.User;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存版用户仓储（演示用）。
 *
 * <p>使用 {@link ConcurrentHashMap} 存储用户的字段映射。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class InMemoryUserRepository implements UserRepository {

    /**
     * 按用户 ID 索引的用户行。
     */
    private final ConcurrentMap<String, UserRow> rows = new ConcurrentHashMap<>();

    /**
     * \u5c06\u9886\u57df\u6a21\u578b\u7684\u8eab\u4efd\u548c\u4e1a\u52a1\u5b57\u6bb5\u6620\u5c04\u4e3a\u5185\u5b58\u8bb0\u5f55\u3002
     * @param user \u5f85\u4fdd\u5b58\u6216\u5bf9\u5916\u8fd4\u56de\u7684\u8d26\u53f7\u5b9e\u4f53
     * @return \u5f85\u4fdd\u5b58\u7684\u5185\u5b58\u8bb0\u5f55
     */
    private static UserRow toRow(User user) {
        UserRow row = new UserRow();
        row.userId = user.id();
        row.username = user.getUsername();
        row.password = user.getPassword();
        row.realName = user.getRealName();
        row.status = user.getStatus().name();
        row.roleIds = new ArrayList<>(user.getRoleIds());
        return row;
    }

    /**
     * \u5c06\u5185\u5b58\u8bb0\u5f55\u6062\u590d\u4e3a\u9886\u57df\u6a21\u578b\u3002
     * @param row \u5f53\u524d\u4ed3\u50a8\u4e2d\u7684\u5185\u5b58\u6570\u636e\u8bb0\u5f55
     * @return \u6620\u5c04\u5b8c\u6210\u7684\u9886\u57df\u805a\u5408
     */
    private static User toModel(UserRow row) {
        User user = new User(row.userId, row.username, row.password, row.realName, User.Status.valueOf(row.status));
        if (CollKit.isNotEmpty(row.roleIds)) {
            user.assignRoles(new java.util.HashSet<>(row.roleIds));
        }
        return user;
    }

    /**
     * \u6309\u4e3b\u952e\u5728\u5f53\u524d\u5185\u5b58\u4ed3\u50a8\u67e5\u627e\u5bf9\u5e94\u805a\u5408\u3002
     * @param id \u5f85\u67e5\u8be2\u6216\u79fb\u9664\u7684\u4e1a\u52a1\u8bb0\u5f55\u6807\u8bc6
     * @return \u5df2\u627e\u5230\u7684\u805a\u5408\uff0c\u4e0d\u5b58\u5728\u65f6\u8fd4\u56de\u7a7a\u7ed3\u679c
     */
    @Override
    public Optional<User> findById(String id) {
        if (StrKit.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id)).map(InMemoryUserRepository::toModel);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<User> findByUsername(String username) {
        if (StrKit.isBlank(username)) {
            return Optional.empty();
        }
        return rows.values().stream()
                .filter(r -> Objects.equals(username, r.username))
                .findFirst()
                .map(InMemoryUserRepository::toModel);
    }

    /** {@inheritDoc} */
    @Override
    public List<User> findByStatus(User.Status status) {
        return rows.values().stream()
                .filter(r -> Objects.equals(status.name(), r.status))
                .map(InMemoryUserRepository::toModel)
                .collect(java.util.stream.Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override
    public List<User> findAll() {
        return rows.values().stream()
                .map(InMemoryUserRepository::toModel)
                .collect(java.util.stream.Collectors.toList());
    }

    // ============================ 模型与行转换 ============================

    /** {@inheritDoc} */
    @Override
    public User save(User aggregate) {
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
     * 用户内部持久化行。
     */
    static class UserRow {

        /** \u8d26\u53f7\u8eab\u4efd\u6216\u7528\u6237\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        String userId;
        /** \u5f53\u524d\u8eab\u4efd\u51ed\u636e\u4f7f\u7528\u7684\u7528\u6237\u540d\u3002 */
        String username;
        /** \u5185\u5b58\u8d26\u53f7\u8bb0\u5f55\u7528\u4e8e\u68c0\u67e5\u51ed\u636e\u7684\u53e3\u4ee4\u5b57\u6bb5\u3002 */
        String password;
        /** \u8d26\u53f7\u8d44\u6599\u4e2d\u7684\u771f\u5b9e\u59d3\u540d\u3002 */
        String realName;
        /** \u5f53\u524d\u5546\u54c1\u3001\u8d26\u53f7\u6216\u6743\u9650\u8bb0\u5f55\u7684\u4e1a\u52a1\u72b6\u6001\u3002 */
        String status;
        /** \u8981\u5173\u8054\u5230\u8d26\u53f7\u7684\u89d2\u8272\u6807\u8bc6\u5217\u8868\u3002 */
        List<String> roleIds;
    }
}

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
     * 用户 ID -> 用户行
     */
    private final ConcurrentMap<String, UserRow> rows = new ConcurrentHashMap<>();

    /**
     * 将领域模型的身份和业务字段映射为内存记录。
     *
     * @param user 待保存或对外返回的账号实体
     * @return 待保存的内存记录
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
     * 将内存记录恢复为领域模型。
     *
     * @param row 当前仓储中的内存数据记录
     * @return 映射完成的领域聚合
     */
    private static User toModel(UserRow row) {
        User user = new User(row.userId, row.username, row.password, row.realName, User.Status.valueOf(row.status));
        if (CollKit.isNotEmpty(row.roleIds)) {
            user.assignRoles(new java.util.HashSet<>(row.roleIds));
        }
        return user;
    }

    /**
     * 按主键在当前内存仓储查找对应聚合。
     *
     * @param id 待查询或移除的业务记录标识
     * @return 已找到的聚合，不存在时返回空结果
     */
    @Override
    public Optional<User> findById(String id) {
        if (StrKit.isBlank(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id)).map(InMemoryUserRepository::toModel);
    }

    /**
     * 按用户名查找用户。
     *
     * <p>此实现调用 <code>StrKit.isBlank(username)</code>。</p>
     *
     * @param username 用户名
     * @return 实际返回<code>Optional.empty()</code>
     */
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

    /**
     * 查询匹配指定状态的记录。
     *
     * <p>此实现调用 <code>rows.values().stream()</code>。</p>
     *
     * @param status 状态
     * @return 用户列表
     */
    @Override
    public List<User> findByStatus(User.Status status) {
        return rows.values().stream()
                .filter(r -> Objects.equals(status.name(), r.status))
                .map(InMemoryUserRepository::toModel)
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
    public List<User> findAll() {
        return rows.values().stream()
                .map(InMemoryUserRepository::toModel)
                .collect(java.util.stream.Collectors.toList());
    }

    // ============================ 模型与行转换 ============================

    /**
     * 写入或更新给定的领域对象或存储记录。
     *
     * <p>此实现调用 <code>rows.put(aggregate.id(), toRow(aggregate))</code>。</p>
     *
     * @param aggregate 传给 <code>rows.put(aggregate.id(), toRow(aggregate))</code>
     * @return 实际返回<code>aggregate</code>
     */
    @Override
    public User save(User aggregate) {
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
     * 用户内部持久化行。
     */
    static class UserRow {

        /**
         * 账号身份或用户记录的主键。
         */
        String userId;
        /**
         * 当前身份凭据使用的用户名。
         */
        String username;
        /**
         * 内存账号记录用于检查凭据的口令字段。
         */
        String password;
        /**
         * 账号资料中的真实姓名。
         */
        String realName;
        /**
         * 当前商品、账号或权限记录的业务状态。
         */
        String status;
        /**
         * 要关联到账号的角色标识列表。
         */
        List<String> roleIds;
    }
}

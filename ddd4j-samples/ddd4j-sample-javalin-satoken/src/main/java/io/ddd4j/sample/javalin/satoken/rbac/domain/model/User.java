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
package io.ddd4j.sample.javalin.satoken.rbac.domain.model;

import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.kit.lang.StrKit;
import lombok.Getter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 用户聚合根（RBAC 模型）。
 *
 * <p>用户通过被赋予角色获得权限。本聚合根负责用户基础信息维护，
 * 权限集合由 {@code RbacService} 通过用户的角色聚合得到（角色继承）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public class User extends AggregateRoot<String> {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private final String userId;
    /**
     * 用户名（登录账号）
     */
    private final String username;
    /**
     * 创建时间
     */
    private final Instant createdAt;
    /**
     * 用户的角色 ID 集合
     */
    private final Set<String> roleIds = new HashSet<>();
    /**
     * 密码（演示用明文，生产应使用哈希）
     */
    private String password;
    /**
     * 真实姓名
     */
    private String realName;
    /**
     * 状态
     */
    private Status status;

    public User(String userId, String username, String password, String realName, Status status) {
        if (StrKit.isBlank(userId)) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (StrKit.isBlank(username)) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (StrKit.isBlank(password)) {
            throw new IllegalArgumentException("password must not be blank");
        }
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.realName = realName;
        this.status = Objects.requireNonNullElse(status, Status.ENABLED);
        this.createdAt = Instant.now();
    }

    /**
     * 修改密码。
     */
    public void changePassword(String newPassword) {
        if (StrKit.isNotBlank(newPassword)) {
            this.password = newPassword;
        }
    }

    /**
     * 修改真实姓名。
     */
    public void rename(String realName) {
        this.realName = realName;
    }

    /**
     * 启用。
     */
    public void enable() {
        this.status = Status.ENABLED;
    }

    /**
     * 禁用。
     */
    public void disable() {
        this.status = Status.DISABLED;
    }

    /**
     * 分配角色（替换）。
     */
    public void assignRoles(Set<String> newRoleIds) {
        if (Objects.nonNull(newRoleIds)) {
            this.roleIds.clear();
            this.roleIds.addAll(newRoleIds);
        }
    }

    /**
     * 添加单个角色。
     */
    public void addRole(String roleId) {
        if (StrKit.isNotBlank(roleId)) {
            this.roleIds.add(roleId);
        }
    }

    /**
     * 移除角色。
     */
    public void removeRole(String roleId) {
        this.roleIds.remove(roleId);
    }

    @Override
    public String id() {
        return userId;
    }

    /**
     * 用户状态。
     */
    public enum Status {
        ENABLED, DISABLED
    }
}
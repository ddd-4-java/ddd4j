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
package io.ddd4j.sample.javalin.shiro.rbac.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * RBAC 用户聚合根。
 *
 * <p>用户通过 {@link #roles()} 持有的角色集合间接获得权限码集合（{@link #permissions()}）。
 * 权限码集合是 {@link RbacService} 派生计算的缓存视图，便于 {@link io.ddd4j.core.subject.SubjectDataProvider}
 * 直接读取。
 *
 * <p>本类在所有 7 个示例（Spring/Quarkus/Javalin × Sa-Token/Shiro/Security）中<b>完全一致</b>，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class User {

    /**
     * 用户登录 ID（全局唯一，作为 Shiro Subject 的 principal）
     */
    private final String loginId;
    /**
     * 用户密码（演示用，明文存储；生产环境应替换为 BCrypt / Argon2）
     */
    private final String password;
    /**
     * 用户名（可选展示名）
     */
    private final String displayName;
    /**
     * 用户被授予的角色编码集合
     */
    private final Set<String> roles;
    /**
     * 用户直接持有的权限码集合（除角色外的额外权限）
     */
    private final Set<String> permissions;

    public User(String loginId, String password) {
        this(loginId, password, null, Collections.emptySet(), Collections.emptySet());
    }

    public User(String loginId, String password, String displayName, Set<String> roles, Set<String> permissions) {
        this.loginId = Objects.requireNonNull(loginId, "loginId must not be null");
        this.password = Objects.requireNonNull(password, "password must not be null");
        this.displayName = displayName;
        this.roles = Collections.unmodifiableSet(new LinkedHashSet<>(roles));
        this.permissions = Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
    }

    /**
     * 工厂方法：构造用户并附带角色编码。
     *
     * @param loginId  登录 ID
     * @param password 密码
     * @param roles    角色编码列表
     * @return 用户实例
     */
    public static User of(String loginId, String password, String... roles) {
        Set<String> set = new LinkedHashSet<>();
        Collections.addAll(set, roles);
        return new User(loginId, password, null, set, Collections.emptySet());
    }

    public String loginId() {
        return loginId;
    }

    public String password() {
        return password;
    }

    public String displayName() {
        return displayName;
    }

    public Set<String> roles() {
        return roles;
    }

    public Set<String> permissions() {
        return permissions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User)) {
            return false;
        }
        User user = (User) o;
        return Objects.equals(loginId, user.loginId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loginId);
    }

    @Override
    public String toString() {
        return "User{" + loginId + ", roles=" + roles + "}";
    }

}
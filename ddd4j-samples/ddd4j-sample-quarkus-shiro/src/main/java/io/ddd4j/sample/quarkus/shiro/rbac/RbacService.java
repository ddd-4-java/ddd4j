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
package io.ddd4j.sample.quarkus.shiro.rbac;

import io.ddd4j.kit.lang.CollKit;

import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;

import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.*;

/**
 * RBAC 业务服务：统一封装登录、登出、当前用户、用户/角色/权限 CRUD 等业务逻辑。
 *
 * <p>本类与 Sa-Token 版 RbacService 业务逻辑完全一致，仅注入方式使用 CDI（@Inject）。
 * Resource 层只做 HTTP 适配。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class RbacService {

    @Inject
    InMemoryUserRepository userRepository;

    @Inject
    InMemoryRoleRepository roleRepository;

    @Inject
    InMemoryPermissionRepository permissionRepository;

    // ============ 认证 ============

    private static WebApplicationException forbidden(String reason) {
        return new WebApplicationException(Response.status(Response.Status.FORBIDDEN)
                .entity(R.of(403, reason)).build());
    }

    /**
     * 登录：根据用户名 + 密码校验，构造 AuthPrincipal 并调用 {@link SubjectKit#login(AuthRequest)}。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 令牌和当前主体信息
     */
    public Map<String, Object> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + username));
        if (user.isDisabled()) {
            throw new IllegalStateException("user is disabled: " + username);
        }
        if (!Objects.equals(user.getPassword(), password)) {
            throw new IllegalArgumentException("invalid password");
        }

        AuthPrincipal principal = new AuthPrincipal()
                .setLoginId(user.getId())
                .setUserId(user.getId())
                .setUserCode(user.getUsername())
                .setRoleCode(user.getRoleCodes().isEmpty() ? null : user.getRoleCodes().iterator().next())
                .setRoles(buildRolePairs(user.getRoleCodes()))
                .setPerms(aggregatePermissions(user.getRoleCodes()));

        AuthRequest request = AuthRequest.of(user.getId()).setTimeout(7200);
        request.setPrincipal(principal);
        String token = SubjectKit.login(request);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("principal", principal);
        return result;
    }

    /**
     * 登出：调用 {@link SubjectKit#logout()}。
     */
    public void logout() {
        SubjectKit.logout();
    }

    /**
     * 当前用户：调用 {@link SubjectKit#getPrincipal()}。
     *
     * @return 认证状态和主体信息
     */
    public Map<String, Object> me() {
        AuthPrincipal principal = SubjectKit.getPrincipal();
        Map<String, Object> result = new HashMap<>();
        if (Objects.isNull(principal)) {
            result.put("authenticated", false);
            return result;
        }
        result.put("authenticated", true);
        result.put("loginId", principal.getLoginId());
        result.put("userId", principal.getUserId());
        result.put("userCode", principal.getUserCode());
        result.put("roleCode", principal.getRoleCode());
        result.put("roles", principal.getRoles());
        result.put("perms", principal.getPerms());
        return result;
    }

    /**
     * 权限校验（运行时）：调用 {@link SubjectKit#hasPermission(String)}。
     *
     * @param permission 权限
     * @return 拥有权限时为 true
     */
    public boolean hasPermission(String permission) {
        return SubjectKit.hasPermission(permission);
    }

    /**
     * 角色校验（运行时）：调用 {@link SubjectKit#hasRole(String)}。
     *
     * @param role 角色
     * @return 拥有角色时为 true
     */
    public boolean hasRole(String role) {
        return SubjectKit.hasRole(role);
    }

    // ============ 用户 CRUD ============

    /**
     * 登录状态：调用 {@link SubjectKit#isLogin()}。
     *
     * @return 已登录时为 true
     */
    public boolean isLogin() {
        return SubjectKit.isLogin();
    }

    /**
     * 列出全部用户。
     *
     * @return 用户列表
     */
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    /**
     * 按标识查找用户。
     *
     * @param id 标识
     * @return 匹配的用户
     */
    public User findUser(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + id));
    }

    /**
     * 创建用户。
     *
     * @param user 用户
     * @return 已保存的用户
     */
    public User createUser(User user) {
        if (StrKit.isBlank(user.getId())) {
            throw new IllegalArgumentException("user.id must not be blank");
        }
        if (userRepository.findById(user.getId()).isPresent()) {
            throw new IllegalStateException("user already exists: " + user.getId());
        }
        return userRepository.save(user);
    }

    /**
     * 更新用户资料和角色。
     *
     * @param id 标识
     * @param patch 待更新字段
     * @return 已更新的用户
     */
    public User updateUser(String id, User patch) {
        User existing = findUser(id);
        if (Objects.nonNull(patch.getUsername())) {
            existing.setUsername(patch.getUsername());
        }
        if (Objects.nonNull(patch.getDisplayName())) {
            existing.setDisplayName(patch.getDisplayName());
        }
        if (Objects.nonNull(patch.getPassword())) {
            existing.setPassword(patch.getPassword());
        }
        if (Objects.nonNull(patch.getRoleCodes())) {
            existing.setRoleCodes(new HashSet<>(patch.getRoleCodes()));
        }
        existing.setDisabled(patch.isDisabled());
        return userRepository.save(existing);
    }

    /**
     * 删除用户。
     *
     * @param id 标识
     * @return 删除成功时为 true
     */
    public boolean deleteUser(String id) {
        return userRepository.deleteById(id);
    }

    /**
     * 为用户分配角色。
     *
     * @param userId 用户标识
     * @param roleCode 角色编码
     * @return 已更新的用户
     */
    public User assignRole(String userId, String roleCode) {
        roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("role not found: " + roleCode));
        return userRepository.addRole(userId, roleCode);
    }

    // ============ 角色 CRUD ============

    /**
     * 撤销用户角色。
     *
     * @param userId 用户标识
     * @param roleCode 角色编码
     * @return 已更新的用户
     */
    public User revokeRole(String userId, String roleCode) {
        return userRepository.removeRole(userId, roleCode);
    }

    /**
     * 列出全部角色。
     *
     * @return 角色列表
     */
    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    /**
     * 按编码查找角色。
     *
     * @param code 业务编码
     * @return 匹配的角色
     */
    public Role findRole(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("role not found: " + code));
    }

    /**
     * 创建角色。
     *
     * @param role 角色
     * @return 已保存的角色
     */
    public Role createRole(Role role) {
        if (StrKit.isBlank(role.getCode())) {
            throw new IllegalArgumentException("role.code must not be blank");
        }
        if (roleRepository.findByCode(role.getCode()).isPresent()) {
            throw new IllegalStateException("role already exists: " + role.getCode());
        }
        return roleRepository.save(role);
    }

    /**
     * 更新角色资料。
     *
     * @param code 业务编码
     * @param patch 待更新字段
     * @return 已更新的角色
     */
    public Role updateRole(String code, Role patch) {
        Role existing = findRole(code);
        if (Objects.nonNull(patch.getDisplayName())) {
            existing.setDisplayName(patch.getDisplayName());
        }
        if (Objects.nonNull(patch.getDescription())) {
            existing.setDescription(patch.getDescription());
        }
        if (Objects.nonNull(patch.getPermissionCodes())) {
            existing.setPermissionCodes(new HashSet<>(patch.getPermissionCodes()));
        }
        return roleRepository.save(existing);
    }

    /**
     * 删除角色。
     *
     * @param code 业务编码
     * @return 删除成功时为 true
     */
    public boolean deleteRole(String code) {
        return roleRepository.deleteByCode(code);
    }

    /**
     * 为角色授予权限。
     *
     * @param roleCode 角色编码
     * @param permissionCode 权限编码
     * @return 已更新的角色
     */
    public Role grantPermission(String roleCode, String permissionCode) {
        permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new IllegalArgumentException("permission not found: " + permissionCode));
        return roleRepository.addPermission(roleCode, permissionCode);
    }

    // ============ 权限 CRUD ============

    /**
     * 撤销角色权限。
     *
     * @param roleCode 角色编码
     * @param permissionCode 权限编码
     * @return 已更新的角色
     */
    public Role revokePermission(String roleCode, String permissionCode) {
        return roleRepository.removePermission(roleCode, permissionCode);
    }

    /**
     * 列出全部权限。
     *
     * @return 权限列表
     */
    public List<Permission> listPermissions() {
        return permissionRepository.findAll();
    }

    /**
     * 按编码查找权限。
     *
     * @param code 业务编码
     * @return 匹配的权限
     */
    public Permission findPermission(String code) {
        return permissionRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("permission not found: " + code));
    }

    /**
     * 创建权限。
     *
     * @param permission 权限
     * @return 已保存的权限
     */
    public Permission createPermission(Permission permission) {
        if (StrKit.isBlank(permission.getCode())) {
            throw new IllegalArgumentException("permission.code must not be blank");
        }
        if (permissionRepository.findByCode(permission.getCode()).isPresent()) {
            throw new IllegalStateException("permission already exists: " + permission.getCode());
        }
        return permissionRepository.save(permission);
    }

    /**
     * 更新权限资料。
     *
     * @param code 业务编码
     * @param patch 待更新字段
     * @return 已更新的权限
     */
    public Permission updatePermission(String code, Permission patch) {
        Permission existing = findPermission(code);
        if (Objects.nonNull(patch.getDisplayName())) {
            existing.setDisplayName(patch.getDisplayName());
        }
        if (Objects.nonNull(patch.getDescription())) {
            existing.setDescription(patch.getDescription());
        }
        return permissionRepository.save(existing);
    }

    // ============ 工具方法 ============

    /**
     * 删除权限。
     *
     * @param code 业务编码
     * @return 删除成功时为 true
     */
    public boolean deletePermission(String code) {
        return permissionRepository.deleteByCode(code);
    }

    private List<AuthPrincipal.RolePair> buildRolePairs(Set<String> roleCodes) {
        if (CollKit.isEmpty(roleCodes)) {
            return Collections.emptyList();
        }
        List<AuthPrincipal.RolePair> pairs = new ArrayList<>();
        for (String code : roleCodes) {
            roleRepository.findByCode(code).ifPresent(role -> {
                AuthPrincipal.RolePair pair = new AuthPrincipal.RolePair();
                pair.setRoleCode(role.getCode());
                pair.setRoleName(role.getDisplayName());
                pairs.add(pair);
            });
        }
        return pairs;
    }

    // ============ Shiro 鉴权封装（手工调用，等价于 Sa-Token 注解） ============

    private Set<String> aggregatePermissions(Set<String> roleCodes) {
        if (CollKit.isEmpty(roleCodes)) {
            return Collections.emptySet();
        }
        Set<String> aggregated = new HashSet<>();
        for (String code : roleCodes) {
            roleRepository.findByCode(code)
                    .ifPresent(role -> {
                        if (Objects.nonNull(role.getPermissionCodes())) {
                            aggregated.addAll(role.getPermissionCodes());
                        }
                    });
        }
        return aggregated;
    }

    /**
     * 要求当前请求已登录，否则抛出 401。
     */
    public void requireLogin() {
        if (!SubjectKit.isLogin()) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(R.of(401, "unauthenticated")).build());
        }
    }

    /**
     * 要求当前用户拥有指定角色，否则抛出 403。
     *
     * @param role 角色
     */
    public void requireRole(String role) {
        requireLogin();
        if (!SubjectKit.hasRole(role)) {
            throw forbidden("role required: " + role);
        }
    }

    /**
     * 要求当前用户拥有指定权限码，否则抛出 403。
     *
     * @param permission 权限
     */
    public void requirePermission(String permission) {
        requireLogin();
        if (!SubjectKit.hasPermission(permission)) {
            throw forbidden("permission required: " + permission);
        }
    }

    /**
     * 要求当前用户同时拥有指定角色 + 权限码（AND 模式）。
     *
     * @param role 角色
     * @param permission 权限
     */
    public void requireRoleAndPermission(String role, String permission) {
        requireLogin();
        if (!SubjectKit.hasRole(role)) {
            throw forbidden("role required: " + role);
        }
        if (!SubjectKit.hasPermission(permission)) {
            throw forbidden("permission required: " + permission);
        }
    }

}

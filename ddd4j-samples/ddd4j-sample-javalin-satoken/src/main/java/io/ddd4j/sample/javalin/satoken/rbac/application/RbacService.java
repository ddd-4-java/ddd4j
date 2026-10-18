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
package io.ddd4j.sample.javalin.satoken.rbac.application;
import java.util.Objects;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Permission;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Role;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.User;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.PermissionRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.RoleRepository;
import io.ddd4j.sample.javalin.satoken.rbac.domain.repository.UserRepository;

import java.util.*;

/**
 * RBAC 业务服务：用户/角色/权限的统一编排。
 *
 * <p>本类是所有 7 个 RBAC 示例（Spring/Quarkus/Javalin × Sa-Token/Shiro/Security）中<b>完全一致</b>的业务代码，
 * 切换底层鉴权框架（sa-token/shiro/security）时无需修改任何业务代码，仅替换依赖即可。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>用户登录：构造 {@link AuthPrincipal} 并调用 {@link SubjectKit#login(AuthRequest)}</li>
 *   <li>用户/角色/权限的 CRUD 入口</li>
 *   <li>解析当前登录用户的角色/权限（实现 {@link SubjectDataProvider}）</li>
 *   <li>便捷鉴权查询：{@link #hasRole(String)} / {@link #hasPermission(String)} / {@link #hasAnyRole(String...)}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RbacService implements SubjectDataProvider {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    /**
     * 创建 RBAC 服务。
     *
     * @param userRepository 用户仓储
     * @param roleRepository 角色仓储
     * @param permissionRepository 权限仓储
     */
    public RbacService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    // ============================ 用户登录 ============================

    /**
     * 登录：校验用户名/密码并建立会话。
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录成功返回 token，失败返回 null
     */
    public String login(String username, String password) {
        Objects.requireNonNull(username, "username must not be null");
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return null;
        }
        User user = userOpt.get();
        if (user.getStatus() != User.Status.ENABLED) {
            return null;
        }
        if (!Objects.equals(user.getPassword(), password)) {
            return null;
        }
        // 构造 AuthPrincipal（携带 userId / username / 角色编码）
        Set<String> roleCodes = new LinkedHashSet<>();
        for (String roleId : user.getRoleIds()) {
            roleRepository.findById(roleId).ifPresent(r -> roleCodes.add(r.getRoleCode()));
        }
        AuthPrincipal principal = new AuthPrincipal()
                .setLoginId(user.getUserId())
                .setUserId(user.getUserId())
                .setUserCode(user.getUsername())
                .setRoleCode(roleCodes.stream().findFirst().orElse(null))
                .setRoles(buildRolePairs(roleCodes));
        // 登录：调用 SubjectKit 统一入口（sa-token/shiro/security 各自实现）
        AuthRequest request = AuthRequest.of(user.getUserId()).setTimeout(7200);
        request.setPrincipal(principal);
        return SubjectKit.login(request);
    }

    /**
     * 登出当前会话。
     */
    public void logout() {
        SubjectKit.logout();
    }

    /**
     * 当前用户信息（认证主体）。
     *
     * @return 当前主体
     */
    public AuthPrincipal me() {
        return SubjectKit.getPrincipal();
    }

    /**
     * 是否已登录。
     *
     * @return 已登录时为 true
     */
    public boolean isLogin() {
        return SubjectKit.isLogin();
    }

    /**
     * 踢人下线。
     *
     * @param userId 用户标识
     */
    public void kickout(String userId) {
        SubjectKit.kickout(userId);
    }

    // ============================ 用户 CRUD ============================

    /**
     * 列出全部用户。
     *
     * @return 用户列表
     */
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    /**
     * 按标识获取用户。
     *
     * @param userId 用户标识
     * @return 用户
     */
    public User getUser(String userId) {
        return userRepository.findById(userId).orElse(null);
    }

    /**
     * 创建用户。
     *
     * @param userId 用户标识
     * @param username 用户名
     * @param password 密码
     * @param realName 真实姓名
     * @return 新用户
     */
    public User createUser(String userId, String username, String password, String realName) {
        User user = new User(userId, username, password, realName, User.Status.ENABLED);
        return userRepository.save(user);
    }

    /**
     * 更新用户资料。
     *
     * @param userId 用户标识
     * @param realName 真实姓名
     * @param password 密码
     * @param status 状态
     * @return 已更新的用户
     */
    public User updateUser(String userId, String realName, String password, User.Status status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        if (Objects.nonNull(realName)) {
            user.rename(realName);
        }
        if (StrKit.isNotEmpty(password)) {
            user.changePassword(password);
        }
        if (Objects.nonNull(status)) {
            if (status == User.Status.ENABLED) {
                user.enable();
            } else {
                user.disable();
            }
        }
        return userRepository.save(user);
    }

    /**
     * 删除用户。
     *
     * @param userId 用户标识
     */
    public void deleteUser(String userId) {
        userRepository.deleteById(userId);
    }

    /**
     * 给用户分配角色（按角色 ID 全量替换）。
     *
     * @param userId 用户标识
     * @param roleIds 角色标识集合
     * @return 已更新的用户
     */
    public User assignRolesToUser(String userId, Set<String> roleIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        user.assignRoles(new HashSet<>(roleIds));
        return userRepository.save(user);
    }

    /**
     * 查询用户拥有的所有角色编码。
     *
     * @param userId 用户标识
     * @return 角色编码集合
     */
    public Set<String> listRoleCodesOfUser(String userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    Set<String> codes = new LinkedHashSet<>();
                    for (String roleId : user.getRoleIds()) {
                        roleRepository.findById(roleId).ifPresent(r -> codes.add(r.getRoleCode()));
                    }
                    return codes;
                })
                .orElseGet(LinkedHashSet::new);
    }

    /**
     * 查询用户拥有的所有权限编码（含角色继承）。
     *
     * @param userId 用户标识
     * @return 权限编码集合
     */
    public Set<String> listPermissionCodesOfUser(String userId) {
        Set<String> perms = new LinkedHashSet<>();
        User user = userRepository.findById(userId).orElse(null);
        if (Objects.isNull(user)) {
            return perms;
        }
        for (String roleId : user.getRoleIds()) {
            Role role = roleRepository.findById(roleId).orElse(null);
            if (Objects.isNull(role)) {
                continue;
            }
            for (String permissionId : role.getPermissionIds()) {
                permissionRepository.findById(permissionId).ifPresent(p -> perms.add(p.getPermissionCode()));
            }
        }
        return perms;
    }

    // ============================ 角色 CRUD ============================

    /**
     * 列出全部角色。
     *
     * @return 角色列表
     */
    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    /**
     * 按标识获取角色。
     *
     * @param roleId 角色标识
     * @return 角色
     */
    public Role getRole(String roleId) {
        return roleRepository.findById(roleId).orElse(null);
    }

    /**
     * 创建角色。
     *
     * @param roleId 角色标识
     * @param roleCode 角色编码
     * @param roleName 角色名称
     * @param description 描述
     * @return 新角色
     */
    public Role createRole(String roleId, String roleCode, String roleName, String description) {
        Role role = new Role(roleId, roleCode, roleName, description, Role.Status.ENABLED);
        return roleRepository.save(role);
    }

    /**
     * 更新角色资料。
     *
     * @param roleId 角色标识
     * @param roleName 角色名称
     * @param description 描述
     * @param status 状态
     * @return 已更新的角色
     */
    public Role updateRole(String roleId, String roleName, String description, Role.Status status) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("role not found: " + roleId));
        role.rename(roleName, description);
        if (Objects.nonNull(status)) {
            if (status == Role.Status.ENABLED) {
                role.enable();
            } else {
                role.disable();
            }
        }
        return roleRepository.save(role);
    }

    /**
     * 删除角色。
     *
     * @param roleId 角色标识
     */
    public void deleteRole(String roleId) {
        roleRepository.deleteById(roleId);
    }

    /**
     * 给角色分配权限（按权限 ID 全量替换）。
     *
     * @param roleId 角色标识
     * @param permissionIds 权限标识集合
     * @return 已更新的角色
     */
    public Role assignPermissionsToRole(String roleId, Set<String> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("role not found: " + roleId));
        role.assignPermissions(new HashSet<>(permissionIds));
        return roleRepository.save(role);
    }

    /**
     * 查询某个角色的所有权限编码。
     *
     * @param roleId 角色标识
     * @return 权限编码集合
     */
    public Set<String> listPermissionCodesOfRole(String roleId) {
        return roleRepository.findById(roleId)
                .map(role -> {
                    Set<String> codes = new LinkedHashSet<>();
                    for (String permissionId : role.getPermissionIds()) {
                        permissionRepository.findById(permissionId).ifPresent(p -> codes.add(p.getPermissionCode()));
                    }
                    return codes;
                })
                .orElseGet(LinkedHashSet::new);
    }

    // ============================ 权限 CRUD ============================

    /**
     * 列出全部权限。
     *
     * @return 权限列表
     */
    public List<Permission> listPermissions() {
        return permissionRepository.findAll();
    }

    /**
     * 按标识获取权限。
     *
     * @param permissionId 权限标识
     * @return 权限
     */
    public Permission getPermission(String permissionId) {
        return permissionRepository.findById(permissionId).orElse(null);
    }

    /**
     * 创建权限。
     *
     * @param permissionId 权限标识
     * @param permissionCode 权限编码
     * @param permissionName 权限名称
     * @param module 所属模块
     * @return 新权限
     */
    public Permission createPermission(String permissionId, String permissionCode, String permissionName, String module) {
        Permission permission = new Permission(permissionId, permissionCode, permissionName, module, Permission.Status.ENABLED);
        return permissionRepository.save(permission);
    }

    /**
     * 更新权限资料。
     *
     * @param permissionId 权限标识
     * @param permissionName 权限名称
     * @param module 所属模块
     * @param status 状态
     * @return 已更新的权限
     */
    public Permission updatePermission(String permissionId, String permissionName, String module, Permission.Status status) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("permission not found: " + permissionId));
        permission.rename(permissionName, module);
        if (Objects.nonNull(status)) {
            if (status == Permission.Status.ENABLED) {
                permission.enable();
            } else {
                permission.disable();
            }
        }
        return permissionRepository.save(permission);
    }

    /**
     * 删除权限。
     *
     * @param permissionId 权限标识
     */
    public void deletePermission(String permissionId) {
        permissionRepository.deleteById(permissionId);
    }

    // ============================ 鉴权便捷门面 ============================

    /**
     * 检查当前主体是否拥有角色。
     *
     * @param roleCode 角色编码
     * @return 拥有角色时为 true
     */
    public boolean hasRole(String roleCode) {
        return SubjectKit.hasRole(roleCode);
    }

    /**
     * 检查当前主体是否拥有任一角色。
     *
     * @param roleCodes 角色编码集合
     * @return 拥有任一角色时为 true
     */
    public boolean hasAnyRole(String... roleCodes) {
        return SubjectKit.hasAnyRole(roleCodes);
    }

    /**
     * 检查当前主体是否拥有权限。
     *
     * @param permission 权限编码
     * @return 拥有权限时为 true
     */
    public boolean hasPermission(String permission) {
        return SubjectKit.hasPermission(permission);
    }

    // ============================ SubjectDataProvider 实现 ============================

    @Override
    public List<String> getPermissionList(AuthPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        Object userId = principal.getUserId();
        if (Objects.isNull(userId)) {
            return List.of();
        }
        return new ArrayList<>(listPermissionCodesOfUser(String.valueOf(userId)));
    }

    @Override
    public List<String> getRoleList(AuthPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        Object userId = principal.getUserId();
        if (Objects.isNull(userId)) {
            return List.of();
        }
        return new ArrayList<>(listRoleCodesOfUser(String.valueOf(userId)));
    }

    // ============================ 私有辅助 ============================

    private List<AuthPrincipal.RolePair> buildRolePairs(Set<String> roleCodes) {
        List<AuthPrincipal.RolePair> pairs = new ArrayList<>();
        for (String code : roleCodes) {
            roleRepository.findByRoleCode(code).ifPresent(role -> {
                AuthPrincipal.RolePair pair = new AuthPrincipal.RolePair();
                pair.setRoleId(role.getRoleId());
                pair.setRoleCode(role.getRoleCode());
                pair.setRoleName(role.getRoleName());
                pairs.add(pair);
            });
        }
        return pairs;
    }

}

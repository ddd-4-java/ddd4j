package io.ddd4j.sample.javalin.shiro.rbac.service;

import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Objects;

import io.ddd4j.sample.javalin.shiro.rbac.domain.Permission;
import io.ddd4j.sample.javalin.shiro.rbac.domain.Role;
import io.ddd4j.sample.javalin.shiro.rbac.domain.User;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryPermissionRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryRoleRepository;
import io.ddd4j.sample.javalin.shiro.rbac.repository.InMemoryUserRepository;

import java.util.*;

/**
 * RBAC 业务服务：用户 / 角色 / 权限 CRUD + 派生计算。
 *
 * <p>封装 RBAC 领域的全部业务规则，控制器仅负责 HTTP 适配，业务逻辑全部下沉到本服务。
 * Shiro / Sa-Token / Security 等鉴权框架适配通过 {@code SubjectKit} + {@code SubjectDataProvider}
 * SPI 完成，业务代码不感知具体框架。
 *
 * <p>本类在所有 7 个示例（Spring/Quarkus/Javalin × Sa-Token/Shiro/Security）中<b>完全一致</b>，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RbacService {

    private final InMemoryUserRepository userRepository;
    private final InMemoryRoleRepository roleRepository;
    private final InMemoryPermissionRepository permissionRepository;

    public RbacService(InMemoryUserRepository userRepository,
                       InMemoryRoleRepository roleRepository,
                       InMemoryPermissionRepository permissionRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.permissionRepository = Objects.requireNonNull(permissionRepository, "permissionRepository must not be null");
    }

    // ============================ User CRUD ============================

    /**
     * 复制权限集合（不可变）。
     */
    public static Set<String> copyPerms(Set<String> perms) {
        return Objects.isNull(perms) ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(perms));
    }

    /**
     * 创建用户。
     */
    public User createUser(String loginId, String password, String displayName, Set<String> roleCodes, Set<String> permissions) {
        if (userRepository.findByLoginId(loginId).isPresent()) {
            throw new IllegalStateException("user already exists: " + loginId);
        }
        // 校验角色编码存在性
        validateRoleCodes(roleCodes);
        User user = new User(loginId, password, displayName, roleCodes, permissions);
        return userRepository.save(user);
    }

    /**
     * 保存用户（upsert 语义，用于种子数据初始化）。
     */
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    /**
     * 更新用户。
     */
    public User updateUser(String loginId, String displayName, String password, Set<String> roleCodes, Set<String> permissions) {
        User existing = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new NoSuchElementException("user not found: " + loginId));
        validateRoleCodes(roleCodes);
        User updated = new User(
                existing.loginId(),
                Objects.nonNull(password) ? password : existing.password(),
                Objects.nonNull(displayName) ? displayName : existing.displayName(),
                Objects.nonNull(roleCodes) ? roleCodes : existing.roles(),
                Objects.nonNull(permissions) ? permissions : existing.permissions());
        return userRepository.save(updated);
    }

    /**
     * 删除用户。
     */
    public void deleteUser(String loginId) {
        if (!userRepository.deleteByLoginId(loginId)) {
            throw new NoSuchElementException("user not found: " + loginId);
        }
    }

    /**
     * 查询用户。
     */
    public User findUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new NoSuchElementException("user not found: " + loginId));
    }

    // ============================ Role CRUD ============================

    /**
     * 列出全部用户。
     */
    public Collection<User> listUsers() {
        return userRepository.findAll();
    }

    /**
     * 创建角色。
     */
    public Role createRole(String code, String name, Set<String> permissionCodes) {
        if (roleRepository.findByCode(code).isPresent()) {
            throw new IllegalStateException("role already exists: " + code);
        }
        return roleRepository.save(new Role(code, name, permissionCodes));
    }

    /**
     * 保存角色（upsert 语义，用于种子数据初始化）。
     */
    public Role saveRole(Role role) {
        return roleRepository.save(role);
    }

    /**
     * 更新角色。
     */
    public Role updateRole(String code, String name, Set<String> permissionCodes) {
        Role existing = roleRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("role not found: " + code));
        Role updated = new Role(
                existing.code(),
                Objects.nonNull(name) ? name : existing.name(),
                Objects.nonNull(permissionCodes) ? permissionCodes : existing.permissions());
        return roleRepository.save(updated);
    }

    /**
     * 删除角色。
     */
    public void deleteRole(String code) {
        if (!roleRepository.deleteByCode(code)) {
            throw new NoSuchElementException("role not found: " + code);
        }
    }

    /**
     * 查询角色。
     */
    public Role findRole(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("role not found: " + code));
    }

    // ============================ Permission CRUD ============================

    /**
     * 列出全部角色。
     */
    public Collection<Role> listRoles() {
        return roleRepository.findAll();
    }

    /**
     * 创建权限。
     */
    public Permission createPermission(String code, String description) {
        if (permissionRepository.findByCode(code).isPresent()) {
            throw new IllegalStateException("permission already exists: " + code);
        }
        return permissionRepository.save(new Permission(code, description));
    }

    /**
     * 保存权限（upsert 语义，用于种子数据初始化）。
     */
    public Permission savePermission(Permission permission) {
        return permissionRepository.save(permission);
    }

    /**
     * 删除权限。
     */
    public void deletePermission(String code) {
        if (!permissionRepository.deleteByCode(code)) {
            throw new NoSuchElementException("permission not found: " + code);
        }
    }

    /**
     * 查询权限。
     */
    public Permission findPermission(String code) {
        return permissionRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("permission not found: " + code));
    }

    // ============================ 派生计算 ============================

    /**
     * 列出全部权限。
     */
    public Collection<Permission> listPermissions() {
        return permissionRepository.findAll();
    }

    /**
     * 派生用户的最终权限码集合：用户直接权限 ∪ 角色持有的权限。
     */
    public Set<String> computeEffectivePermissions(User user) {
        Set<String> all = new LinkedHashSet<>(user.permissions());
        for (String roleCode : user.roles()) {
            roleRepository.findByCode(roleCode).ifPresent(role -> all.addAll(role.permissions()));
        }
        return Collections.unmodifiableSet(all);
    }

    /**
     * 校验角色编码全部存在。
     */
    private void validateRoleCodes(Set<String> roleCodes) {
        if (Objects.isNull(roleCodes)) {
            return;
        }
        for (String code : roleCodes) {
            if (roleRepository.findByCode(code).isEmpty()) {
                throw new NoSuchElementException("role not found: " + code);
            }
        }
    }

    /**
     * 校验账号密码（明文比对，仅供演示）。
     */
    public User authenticate(String loginId, String password) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new NoSuchElementException("user not found: " + loginId));
        if (!Objects.equals(user.password(), password)) {
            throw new IllegalArgumentException("invalid credentials");
        }
        return user;
    }

}
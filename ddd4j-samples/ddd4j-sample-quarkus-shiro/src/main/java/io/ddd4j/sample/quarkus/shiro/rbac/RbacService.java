package io.ddd4j.sample.quarkus.shiro.rbac;

import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    /**
     * 登录：根据用户名 + 密码校验，构造 AuthPrincipal 并调用 {@link SubjectKit#login(AuthRequest)}。
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
     */
    public Map<String, Object> me() {
        AuthPrincipal principal = SubjectKit.getPrincipal();
        Map<String, Object> result = new HashMap<>();
        if (principal == null) {
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
     */
    public boolean hasPermission(String permission) {
        return SubjectKit.hasPermission(permission);
    }

    /**
     * 角色校验（运行时）：调用 {@link SubjectKit#hasRole(String)}。
     */
    public boolean hasRole(String role) {
        return SubjectKit.hasRole(role);
    }

    /**
     * 登录状态：调用 {@link SubjectKit#isLogin()}。
     */
    public boolean isLogin() {
        return SubjectKit.isLogin();
    }

    // ============ 用户 CRUD ============

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    public User findUser(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + id));
    }

    public User createUser(User user) {
        if (user.getId() == null || user.getId().isBlank()) {
            throw new IllegalArgumentException("user.id must not be blank");
        }
        if (userRepository.findById(user.getId()).isPresent()) {
            throw new IllegalStateException("user already exists: " + user.getId());
        }
        return userRepository.save(user);
    }

    public User updateUser(String id, User patch) {
        User existing = findUser(id);
        if (patch.getUsername() != null) {
            existing.setUsername(patch.getUsername());
        }
        if (patch.getDisplayName() != null) {
            existing.setDisplayName(patch.getDisplayName());
        }
        if (patch.getPassword() != null) {
            existing.setPassword(patch.getPassword());
        }
        if (patch.getRoleCodes() != null) {
            existing.setRoleCodes(new HashSet<>(patch.getRoleCodes()));
        }
        existing.setDisabled(patch.isDisabled());
        return userRepository.save(existing);
    }

    public boolean deleteUser(String id) {
        return userRepository.deleteById(id);
    }

    public User assignRole(String userId, String roleCode) {
        roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("role not found: " + roleCode));
        return userRepository.addRole(userId, roleCode);
    }

    public User revokeRole(String userId, String roleCode) {
        return userRepository.removeRole(userId, roleCode);
    }

    // ============ 角色 CRUD ============

    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    public Role findRole(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("role not found: " + code));
    }

    public Role createRole(Role role) {
        if (role.getCode() == null || role.getCode().isBlank()) {
            throw new IllegalArgumentException("role.code must not be blank");
        }
        if (roleRepository.findByCode(role.getCode()).isPresent()) {
            throw new IllegalStateException("role already exists: " + role.getCode());
        }
        return roleRepository.save(role);
    }

    public Role updateRole(String code, Role patch) {
        Role existing = findRole(code);
        if (patch.getDisplayName() != null) {
            existing.setDisplayName(patch.getDisplayName());
        }
        if (patch.getDescription() != null) {
            existing.setDescription(patch.getDescription());
        }
        if (patch.getPermissionCodes() != null) {
            existing.setPermissionCodes(new HashSet<>(patch.getPermissionCodes()));
        }
        return roleRepository.save(existing);
    }

    public boolean deleteRole(String code) {
        return roleRepository.deleteByCode(code);
    }

    public Role grantPermission(String roleCode, String permissionCode) {
        permissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new IllegalArgumentException("permission not found: " + permissionCode));
        return roleRepository.addPermission(roleCode, permissionCode);
    }

    public Role revokePermission(String roleCode, String permissionCode) {
        return roleRepository.removePermission(roleCode, permissionCode);
    }

    // ============ 权限 CRUD ============

    public List<Permission> listPermissions() {
        return permissionRepository.findAll();
    }

    public Permission findPermission(String code) {
        return permissionRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("permission not found: " + code));
    }

    public Permission createPermission(Permission permission) {
        if (permission.getCode() == null || permission.getCode().isBlank()) {
            throw new IllegalArgumentException("permission.code must not be blank");
        }
        if (permissionRepository.findByCode(permission.getCode()).isPresent()) {
            throw new IllegalStateException("permission already exists: " + permission.getCode());
        }
        return permissionRepository.save(permission);
    }

    public Permission updatePermission(String code, Permission patch) {
        Permission existing = findPermission(code);
        if (patch.getDisplayName() != null) {
            existing.setDisplayName(patch.getDisplayName());
        }
        if (patch.getDescription() != null) {
            existing.setDescription(patch.getDescription());
        }
        return permissionRepository.save(existing);
    }

    public boolean deletePermission(String code) {
        return permissionRepository.deleteByCode(code);
    }

    // ============ 工具方法 ============

    private List<AuthPrincipal.RolePair> buildRolePairs(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
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

    private Set<String> aggregatePermissions(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> aggregated = new HashSet<>();
        for (String code : roleCodes) {
            roleRepository.findByCode(code)
                    .ifPresent(role -> {
                        if (role.getPermissionCodes() != null) {
                            aggregated.addAll(role.getPermissionCodes());
                        }
                    });
        }
        return aggregated;
    }

    // ============ Shiro 鉴权封装（手工调用，等价于 Sa-Token 注解） ============

    /**
     * 要求当前请求已登录，否则抛出 401。
     */
    public void requireLogin() {
        if (!SubjectKit.isLogin()) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(R.fail(401, "unauthenticated")).build());
        }
    }

    /**
     * 要求当前用户拥有指定角色，否则抛出 403。
     */
    public void requireRole(String role) {
        requireLogin();
        if (!SubjectKit.hasRole(role)) {
            throw forbidden("role required: " + role);
        }
    }

    /**
     * 要求当前用户拥有指定权限码，否则抛出 403。
     */
    public void requirePermission(String permission) {
        requireLogin();
        if (!SubjectKit.hasPermission(permission)) {
            throw forbidden("permission required: " + permission);
        }
    }

    /**
     * 要求当前用户同时拥有指定角色 + 权限码（AND 模式）。
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

    private static WebApplicationException forbidden(String reason) {
        return new WebApplicationException(Response.status(Response.Status.FORBIDDEN)
                .entity(R.fail(403, reason)).build());
    }

}
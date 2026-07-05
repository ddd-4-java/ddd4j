package io.ddd4j.sample.spring.security.rbac;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * RBAC 业务服务：聚合用户/角色/权限的 CRUD + 登录/鉴权。
 *
 * <p>本类是 RBAC 演示的<b>业务核心</b>——HTTP 控制器仅做协议适配，
 * 所有业务逻辑委托本服务完成。
 *
 * <p>本类在所有示例（Spring/Security × ddd4j）中<b>完全一致</b>，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Service
public class RbacService {

    private final InMemoryUserRepository userRepository;
    private final InMemoryRoleRepository roleRepository;
    private final InMemoryPermissionRepository permissionRepository;

    public RbacService(InMemoryUserRepository userRepository,
                       InMemoryRoleRepository roleRepository,
                       InMemoryPermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    // ============== 用户管理 ==============

    public User createUser(String username, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("username already exists: " + username);
        }
        User user = new User(null, username, password);
        return userRepository.save(user);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public Optional<User> findUserById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> findUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    public boolean deleteUser(String id) {
        return userRepository.deleteById(id);
    }

    public User assignRoleToUser(String userId, String roleCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        if (!roleRepository.existsByCode(roleCode)) {
            throw new IllegalArgumentException("role not found: " + roleCode);
        }
        user.addRole(roleCode);
        return userRepository.save(user);
    }

    public User revokeRoleFromUser(String userId, String roleCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        user.removeRole(roleCode);
        return userRepository.save(user);
    }

    // ============== 角色管理 ==============

    public Role createRole(String code, String name, String description, Set<String> permissionCodes) {
        if (roleRepository.existsByCode(code)) {
            throw new IllegalArgumentException("role already exists: " + code);
        }
        Role role = new Role(code, name, description);
        if (permissionCodes != null) {
            permissionCodes.forEach(p -> {
                if (!permissionRepository.existsByCode(p)) {
                    throw new IllegalArgumentException("permission not found: " + p);
                }
                role.addPermission(p);
            });
        }
        return roleRepository.save(role);
    }

    public Role saveRole(Role role) {
        return roleRepository.save(role);
    }

    public Optional<Role> findRoleByCode(String code) {
        return roleRepository.findByCode(code);
    }

    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    public boolean deleteRole(String code) {
        return roleRepository.deleteByCode(code);
    }

    public Role grantPermissionToRole(String roleCode, String permissionCode) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("role not found: " + roleCode));
        if (!permissionRepository.existsByCode(permissionCode)) {
            throw new IllegalArgumentException("permission not found: " + permissionCode);
        }
        role.addPermission(permissionCode);
        return roleRepository.save(role);
    }

    public Role revokePermissionFromRole(String roleCode, String permissionCode) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalArgumentException("role not found: " + roleCode));
        role.removePermission(permissionCode);
        return roleRepository.save(role);
    }

    // ============== 权限管理 ==============

    public Permission createPermission(String code, String name, String description) {
        if (permissionRepository.existsByCode(code)) {
            throw new IllegalArgumentException("permission already exists: " + code);
        }
        return permissionRepository.save(new Permission(code, name, description));
    }

    public Permission savePermission(Permission permission) {
        return permissionRepository.save(permission);
    }

    public Optional<Permission> findPermissionByCode(String code) {
        return permissionRepository.findByCode(code);
    }

    public List<Permission> listPermissions() {
        return permissionRepository.findAll();
    }

    public boolean deletePermission(String code) {
        return permissionRepository.deleteByCode(code);
    }

    // ============== 鉴权（认证 + 授权） ==============

    /**
     * 登录：SubjectKit.login(AuthRequest)
     */
    public Map<String, Object> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + username));
        if (!user.isEnabled() || !Objects.equals(user.getPassword(), password)) {
            throw new IllegalArgumentException("invalid username or password");
        }
        AuthPrincipal principal = new AuthPrincipal()
                .setLoginId(user.getId())
                .setUserId(user.getId())
                .setRoleCode(user.getRoleCodes().stream().findFirst().orElse("user"));

        AuthRequest request = AuthRequest.of(user.getId()).setTimeout(7200);
        request.setPrincipal(principal);
        String token = SubjectKit.login(request);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("principal", principal);
        result.put("user", user);
        return result;
    }

    /**
     * 登出：SubjectKit.logout()
     */
    public Map<String, Object> logout() {
        SubjectKit.logout();
        return Map.of("success", true);
    }

    /**
     * 当前用户：SubjectKit.getPrincipal()
     */
    public Map<String, Object> me() {
        AuthPrincipal principal = SubjectKit.getPrincipal();
        if (Objects.isNull(principal)) {
            return Map.of("authenticated", false);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("authenticated", true);
        result.put("loginId", principal.getLoginId());
        result.put("userId", principal.getUserId());
        result.put("roleCode", principal.getRoleCode());
        return result;
    }

    /**
     * 权限校验：SubjectKit.hasPermission()
     */
    public Map<String, Object> checkPermission(String permission) {
        boolean has = SubjectKit.hasPermission(permission);
        return Map.of("permission", permission, "has", has);
    }

    /**
     * 角色校验：SubjectKit.hasRole()
     */
    public Map<String, Object> checkRole(String role) {
        boolean has = SubjectKit.hasRole(role);
        return Map.of("role", role, "has", has);
    }

    /**
     * 登录状态：SubjectKit.isLogin()
     */
    public Map<String, Object> status() {
        return Map.of("login", SubjectKit.isLogin());
    }

}
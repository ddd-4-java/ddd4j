package io.ddd4j.sample.spring.shiro.rbac.application;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.spring.shiro.rbac.domain.model.Permission;
import io.ddd4j.sample.spring.shiro.rbac.domain.model.Role;
import io.ddd4j.sample.spring.shiro.rbac.domain.model.User;
import io.ddd4j.sample.spring.shiro.rbac.domain.repository.PermissionRepository;
import io.ddd4j.sample.spring.shiro.rbac.domain.repository.RoleRepository;
import io.ddd4j.sample.spring.shiro.rbac.domain.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * RBAC 业务服务：用户/角色/权限的统一编排。
 *
 * <p>本类是所有 7 个 RBAC 示例（Spring/Quarkus/Javalin × Sa-Token/Shiro/Security）中<b>完全一致</b>的业务代码，
 * 切换底层鉴权框架（sa-token/shiro/security）时无需修改任何业务代码，仅替换依赖即可。
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li>用户登录：构造 {@link AuthPrincipal} 并调用 {@link SubjectKit#login(AuthRequest)}</li>
 *   <li>用户/角色/权限的 CRUD 入口</li>
 *   <li>解析当前登录用户的角色/权限（实现 {@link SubjectDataProvider}）</li>
 *   <li>便捷鉴权查询：{@link #hasRole(String)} / {@link #hasPermission(String)} / {@link #hasAnyRole(String...)}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Service
public class RbacService implements SubjectDataProvider {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

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
        request.extra("credential", password);
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
     */
    public AuthPrincipal me() {
        return SubjectKit.getPrincipal();
    }

    /**
     * 是否已登录。
     */
    public boolean isLogin() {
        return SubjectKit.isLogin();
    }

    /**
     * 踢人下线。
     */
    public void kickout(String userId) {
        SubjectKit.kickout(userId);
    }

    // ============================ 用户 CRUD ============================

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    public User getUser(String userId) {
        return userRepository.findById(userId).orElse(null);
    }

    public User createUser(String userId, String username, String password, String realName) {
        User user = new User(userId, username, password, realName, User.Status.ENABLED);
        return userRepository.save(user);
    }

    public User updateUser(String userId, String realName, String password, User.Status status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        if (realName != null) {
            user.rename(realName);
        }
        if (password != null && !password.isEmpty()) {
            user.changePassword(password);
        }
        if (status != null) {
            if (status == User.Status.ENABLED) {
                user.enable();
            } else {
                user.disable();
            }
        }
        return userRepository.save(user);
    }

    public void deleteUser(String userId) {
        userRepository.deleteById(userId);
    }

    /**
     * 给用户分配角色（按角色 ID 全量替换）。
     */
    public User assignRolesToUser(String userId, Set<String> roleIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        user.assignRoles(new HashSet<>(roleIds));
        return userRepository.save(user);
    }

    /**
     * 查询用户拥有的所有角色编码。
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
     */
    public Set<String> listPermissionCodesOfUser(String userId) {
        Set<String> perms = new LinkedHashSet<>();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return perms;
        }
        for (String roleId : user.getRoleIds()) {
            Role role = roleRepository.findById(roleId).orElse(null);
            if (role == null) {
                continue;
            }
            for (String permissionId : role.getPermissionIds()) {
                permissionRepository.findById(permissionId).ifPresent(p -> perms.add(p.getPermissionCode()));
            }
        }
        return perms;
    }

    // ============================ 角色 CRUD ============================

    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    public Role getRole(String roleId) {
        return roleRepository.findById(roleId).orElse(null);
    }

    public Role createRole(String roleId, String roleCode, String roleName, String description) {
        Role role = new Role(roleId, roleCode, roleName, description, Role.Status.ENABLED);
        return roleRepository.save(role);
    }

    public Role updateRole(String roleId, String roleName, String description, Role.Status status) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("role not found: " + roleId));
        role.rename(roleName, description);
        if (status != null) {
            if (status == Role.Status.ENABLED) {
                role.enable();
            } else {
                role.disable();
            }
        }
        return roleRepository.save(role);
    }

    public void deleteRole(String roleId) {
        roleRepository.deleteById(roleId);
    }

    /**
     * 给角色分配权限（按权限 ID 全量替换）。
     */
    public Role assignPermissionsToRole(String roleId, Set<String> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("role not found: " + roleId));
        role.assignPermissions(new HashSet<>(permissionIds));
        return roleRepository.save(role);
    }

    /**
     * 查询某个角色的所有权限编码。
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

    public List<Permission> listPermissions() {
        return permissionRepository.findAll();
    }

    public Permission getPermission(String permissionId) {
        return permissionRepository.findById(permissionId).orElse(null);
    }

    public Permission createPermission(String permissionId, String permissionCode, String permissionName, String module) {
        Permission permission = new Permission(permissionId, permissionCode, permissionName, module, Permission.Status.ENABLED);
        return permissionRepository.save(permission);
    }

    public Permission updatePermission(String permissionId, String permissionName, String module, Permission.Status status) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new IllegalArgumentException("permission not found: " + permissionId));
        permission.rename(permissionName, module);
        if (status != null) {
            if (status == Permission.Status.ENABLED) {
                permission.enable();
            } else {
                permission.disable();
            }
        }
        return permissionRepository.save(permission);
    }

    public void deletePermission(String permissionId) {
        permissionRepository.deleteById(permissionId);
    }

    // ============================ 鉴权便捷门面 ============================

    public boolean hasRole(String roleCode) {
        return SubjectKit.hasRole(roleCode);
    }

    public boolean hasAnyRole(String... roleCodes) {
        return SubjectKit.hasAnyRole(roleCodes);
    }

    public boolean hasPermission(String permission) {
        return SubjectKit.hasPermission(permission);
    }

    // ============================ SubjectDataProvider 实现 ============================

    @Override
    public List<String> getPermissionList(AuthPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        Object userId = principal.getUserId();
        if (userId == null) {
            return List.of();
        }
        return new ArrayList<>(listPermissionCodesOfUser(String.valueOf(userId)));
    }

    @Override
    public List<String> getRoleList(AuthPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        Object userId = principal.getUserId();
        if (userId == null) {
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

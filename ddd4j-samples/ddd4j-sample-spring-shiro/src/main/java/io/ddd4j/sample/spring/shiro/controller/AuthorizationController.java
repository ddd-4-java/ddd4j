package io.ddd4j.sample.spring.shiro.controller;

import io.ddd4j.sample.spring.shiro.rbac.application.RbacService;
import io.ddd4j.sample.spring.shiro.rbac.domain.model.Permission;
import io.ddd4j.sample.spring.shiro.rbac.domain.model.Role;
import io.ddd4j.sample.spring.shiro.rbac.domain.model.User;
import io.ddd4j.sample.spring.shiro.rbac.domain.repository.PermissionRepository;
import io.ddd4j.sample.spring.shiro.rbac.domain.repository.RoleRepository;
import io.ddd4j.sample.spring.shiro.rbac.domain.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 授权管理控制器：用户 / 角色 / 权限的 CRUD。
 *
 * <p>本控制器是 RBAC 授权管理面，提供完整的 RBAC 资源管理 API：
 * <ul>
 *   <li>{@code /admin/users} — 用户 CRUD + 分配角色 + 查询权限（含角色继承）</li>
 *   <li>{@code /admin/roles} — 角色 CRUD + 分配权限</li>
 *   <li>{@code /admin/permissions} — 权限 CRUD</li>
 * </ul>
 *
 * <p>本控制器与认证框架（sa-token/shiro/security）解耦，仅通过 {@link RbacService} 操作仓储。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/admin")
public class AuthorizationController {

    private final RbacService rbacService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public AuthorizationController(RbacService rbacService,
                                   UserRepository userRepository,
                                   RoleRepository roleRepository,
                                   PermissionRepository permissionRepository) {
        this.rbacService = rbacService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    // ============================ 用户管理 ============================

    /**
     * 创建用户。
     */
    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody CreateUserRequest req) {
        User user = rbacService.createUser(req.userId(), req.username(), req.password(), req.realName());
        return Map.of("success", true, "userId", user.id());
    }

    /**
     * 用户列表。
     */
    @GetMapping("/users")
    public List<User> listUsers() {
        return rbacService.listUsers();
    }

    /**
     * 用户详情。
     */
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable("id") String id) {
        return rbacService.getUser(id);
    }

    /**
     * 更新用户。
     */
    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable("id") String id, @RequestBody UpdateUserRequest req) {
        User updated = rbacService.updateUser(id, req.realName(), req.password(), req.status());
        return Map.of("success", true, "userId", updated.id());
    }

    /**
     * 删除用户。
     */
    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable("id") String id) {
        rbacService.deleteUser(id);
        return Map.of("success", true, "deleted", id);
    }

    /**
     * 给用户分配角色（全量替换）。
     */
    @PostMapping("/users/{id}/roles")
    public Map<String, Object> assignRolesToUser(@PathVariable("id") String id,
                                                 @RequestBody AssignRolesRequest req) {
        User user = rbacService.assignRolesToUser(id, new HashSet<>(req.roleIds()));
        return Map.of("success", true, "userId", user.id(), "roleIds", user.getRoleIds());
    }

    /**
     * 获取用户所有权限（含角色继承）。
     */
    @GetMapping("/users/{id}/permissions")
    public Map<String, Object> getUserPermissions(@PathVariable("id") String id) {
        Set<String> roleCodes = rbacService.listRoleCodesOfUser(id);
        Set<String> permissionCodes = rbacService.listPermissionCodesOfUser(id);
        return Map.of("userId", id, "roles", roleCodes, "permissions", permissionCodes);
    }

    // ============================ 角色管理 ============================

    /**
     * 创建角色。
     */
    @PostMapping("/roles")
    public Map<String, Object> createRole(@RequestBody CreateRoleRequest req) {
        Role role = rbacService.createRole(req.roleId(), req.roleCode(), req.roleName(), req.description());
        return Map.of("success", true, "roleId", role.id());
    }

    /**
     * 角色列表。
     */
    @GetMapping("/roles")
    public List<Role> listRoles() {
        return rbacService.listRoles();
    }

    /**
     * 角色详情。
     */
    @GetMapping("/roles/{id}")
    public Role getRole(@PathVariable("id") String id) {
        return rbacService.getRole(id);
    }

    /**
     * 更新角色。
     */
    @PutMapping("/roles/{id}")
    public Map<String, Object> updateRole(@PathVariable("id") String id, @RequestBody UpdateRoleRequest req) {
        Role updated = rbacService.updateRole(id, req.roleName(), req.description(), req.status());
        return Map.of("success", true, "roleId", updated.id());
    }

    /**
     * 删除角色。
     */
    @DeleteMapping("/roles/{id}")
    public Map<String, Object> deleteRole(@PathVariable("id") String id) {
        rbacService.deleteRole(id);
        return Map.of("success", true, "deleted", id);
    }

    /**
     * 给角色分配权限（全量替换）。
     */
    @PostMapping("/roles/{id}/permissions")
    public Map<String, Object> assignPermissionsToRole(@PathVariable("id") String id,
                                                       @RequestBody AssignPermissionsRequest req) {
        Role role = rbacService.assignPermissionsToRole(id, new HashSet<>(req.permissionIds()));
        return Map.of("success", true, "roleId", role.id(), "permissionIds", role.getPermissionIds());
    }

    /**
     * 获取角色的权限编码集合。
     */
    @GetMapping("/roles/{id}/permissions")
    public Map<String, Object> getRolePermissions(@PathVariable("id") String id) {
        Set<String> codes = rbacService.listPermissionCodesOfRole(id);
        return Map.of("roleId", id, "permissions", codes);
    }

    // ============================ 权限管理 ============================

    /**
     * 创建权限。
     */
    @PostMapping("/permissions")
    public Map<String, Object> createPermission(@RequestBody CreatePermissionRequest req) {
        Permission permission = rbacService.createPermission(req.permissionId(), req.permissionCode(),
                req.permissionName(), req.module());
        return Map.of("success", true, "permissionId", permission.id());
    }

    /**
     * 权限列表。
     */
    @GetMapping("/permissions")
    public List<Permission> listPermissions() {
        return rbacService.listPermissions();
    }

    /**
     * 权限详情。
     */
    @GetMapping("/permissions/{id}")
    public Permission getPermission(@PathVariable("id") String id) {
        return rbacService.getPermission(id);
    }

    /**
     * 更新权限。
     */
    @PutMapping("/permissions/{id}")
    public Map<String, Object> updatePermission(@PathVariable("id") String id,
                                                @RequestBody UpdatePermissionRequest req) {
        Permission updated = rbacService.updatePermission(id, req.permissionName(), req.module(), req.status());
        return Map.of("success", true, "permissionId", updated.id());
    }

    /**
     * 删除权限。
     */
    @DeleteMapping("/permissions/{id}")
    public Map<String, Object> deletePermission(@PathVariable("id") String id) {
        rbacService.deletePermission(id);
        return Map.of("success", true, "deleted", id);
    }

    // ============================ 请求/响应 DTO ============================

    public record CreateUserRequest(String userId, String username, String password, String realName) {
    }

    public record UpdateUserRequest(String realName, String password, User.Status status) {
    }

    public record AssignRolesRequest(List<String> roleIds) {
    }

    public record CreateRoleRequest(String roleId, String roleCode, String roleName, String description) {
    }

    public record UpdateRoleRequest(String roleName, String description, Role.Status status) {
    }

    public record AssignPermissionsRequest(List<String> permissionIds) {
    }

    public record CreatePermissionRequest(String permissionId, String permissionCode, String permissionName,
                                          String module) {
    }

    public record UpdatePermissionRequest(String permissionName, String module, Permission.Status status) {
    }
}
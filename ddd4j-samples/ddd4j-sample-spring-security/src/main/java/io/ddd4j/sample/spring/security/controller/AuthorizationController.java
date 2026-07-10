package io.ddd4j.sample.spring.security.controller;

import io.ddd4j.sample.spring.security.rbac.Permission;
import io.ddd4j.sample.spring.security.rbac.RbacService;
import io.ddd4j.sample.spring.security.rbac.Role;
import io.ddd4j.sample.spring.security.rbac.User;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 授权管理控制器（Spring Security）：用户 / 角色 / 权限的 CRUD。
 *
 * <p>所有端点均使用 {@link PreAuthorize} 做方法级权限校验（{@code @EnableMethodSecurity}）。
 *
 * <p>权限矩阵：
 * <ul>
 *   <li>用户相关：增删改需要 {@code admin} 角色；列表仅需 {@code user:list} 权限</li>
 *   <li>角色相关：增删改需要 {@code admin} 角色 + {@code user:add}/{@code user:delete}</li>
 *   <li>权限相关：增删需要 {@code admin} 角色 + {@code user:delete} 组合</li>
 * </ul>
 *
 * <p>HTTP 协议适配层：业务逻辑全部委托 {@link RbacService}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/auth")
public class AuthorizationController {

    private final RbacService rbacService;

    public AuthorizationController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    // ============== 用户管理 ==============

    /**
     * 查询用户列表：仅需 user:list 权限。
     */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('user:list')")
    public List<User> listUsers() {
        return rbacService.listUsers();
    }

    /**
     * 查询用户：需要登录。
     */
    @GetMapping("/users/{id}")
    @PreAuthorize("isAuthenticated()")
    public User getUser(@PathVariable String id) {
        return rbacService.findUserById(id)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + id));
    }

    /**
     * 新增用户：admin 角色 + user:add 权限。
     */
    @PostMapping("/users")
    @PreAuthorize("hasRole('admin') and hasAuthority('user:add')")
    public User createUser(@RequestParam String username, @RequestParam String password) {
        return rbacService.createUser(username, password);
    }

    /**
     * 删除用户：admin 角色 + user:delete 权限（组合鉴权示例）。
     */
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('admin') and hasAuthority('user:delete')")
    public Map<String, Object> deleteUser(@PathVariable String id) {
        boolean ok = rbacService.deleteUser(id);
        return Map.of("deleted", id, "success", ok);
    }

    /**
     * 给用户分配角色：admin 角色 + user:add 权限。
     */
    @PutMapping("/users/{id}/roles/{roleCode}")
    @PreAuthorize("hasRole('admin') and hasAuthority('user:add')")
    public User assignRole(@PathVariable String id, @PathVariable String roleCode) {
        return rbacService.assignRoleToUser(id, roleCode);
    }

    /**
     * 撤销用户角色：admin 角色 + user:delete 权限。
     */
    @DeleteMapping("/users/{id}/roles/{roleCode}")
    @PreAuthorize("hasRole('admin') and hasAuthority('user:delete')")
    public User revokeRole(@PathVariable String id, @PathVariable String roleCode) {
        return rbacService.revokeRoleFromUser(id, roleCode);
    }

    // ============== 角色管理 ==============

    /**
     * 查询角色列表：仅需 user:list 权限。
     */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('user:list')")
    public List<Role> listRoles() {
        return rbacService.listRoles();
    }

    /**
     * 查询角色：需要登录。
     */
    @GetMapping("/roles/{code}")
    @PreAuthorize("isAuthenticated()")
    public Role getRole(@PathVariable String code) {
        return rbacService.findRoleByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("role not found: " + code));
    }

    /**
     * 新增角色：admin 角色 + user:add 权限。
     */
    @PostMapping("/roles")
    @PreAuthorize("hasRole('admin') and hasAuthority('user:add')")
    public Role createRole(@RequestParam String code,
                           @RequestParam String name,
                           @RequestParam(required = false) String description,
                           @RequestParam(required = false) Set<String> permissionCodes) {
        return rbacService.createRole(code, name, description, permissionCodes);
    }

    /**
     * 删除角色：admin 角色 + user:delete 权限。
     */
    @DeleteMapping("/roles/{code}")
    @PreAuthorize("hasRole('admin') and hasAuthority('user:delete')")
    public Map<String, Object> deleteRole(@PathVariable String code) {
        boolean ok = rbacService.deleteRole(code);
        return Map.of("deleted", code, "success", ok);
    }

    /**
     * 给角色授予权限：admin 角色 + user:add 权限。
     */
    @PutMapping("/roles/{code}/permissions/{permissionCode}")
    @PreAuthorize("hasRole('admin') and hasAuthority('user:add')")
    public Role grantPermission(@PathVariable String code, @PathVariable String permissionCode) {
        return rbacService.grantPermissionToRole(code, permissionCode);
    }

    /**
     * 撤销角色权限：admin 角色 + user:delete 权限。
     */
    @DeleteMapping("/roles/{code}/permissions/{permissionCode}")
    @PreAuthorize("hasRole('admin') and hasAuthority('user:delete')")
    public Role revokePermission(@PathVariable String code, @PathVariable String permissionCode) {
        return rbacService.revokePermissionFromRole(code, permissionCode);
    }

    // ============== 权限管理 ==============

    /**
     * 查询权限列表：仅需 user:list 权限。
     */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('user:list')")
    public List<Permission> listPermissions() {
        return rbacService.listPermissions();
    }

    /**
     * 查询权限：需要登录。
     */
    @GetMapping("/permissions/{code}")
    @PreAuthorize("isAuthenticated()")
    public Permission getPermission(@PathVariable String code) {
        return rbacService.findPermissionByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("permission not found: " + code));
    }

    /**
     * 新增权限：admin 角色 + user:add 权限。
     */
    @PostMapping("/permissions")
    @PreAuthorize("hasRole('admin') and hasAuthority('user:add')")
    public Permission createPermission(@RequestBody Permission permission) {
        return rbacService.createPermission(
                permission.getCode(), permission.getName(), permission.getDescription());
    }

    /**
     * 删除权限：admin 角色 + user:delete 权限。
     */
    @DeleteMapping("/permissions/{code}")
    @PreAuthorize("hasRole('admin') and hasAuthority('user:delete')")
    public Map<String, Object> deletePermission(@PathVariable String code) {
        boolean ok = rbacService.deletePermission(code);
        return Map.of("deleted", code, "success", ok);
    }

}
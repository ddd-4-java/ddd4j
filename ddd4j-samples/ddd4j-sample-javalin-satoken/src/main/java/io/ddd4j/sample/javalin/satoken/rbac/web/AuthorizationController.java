package io.ddd4j.sample.javalin.satoken.rbac.web;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.javalin.satoken.rbac.application.RbacService;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Permission;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Role;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.User;
import io.javalin.apibuilder.EndpointGroup;

import java.util.*;

import static io.javalin.apibuilder.ApiBuilder.*;

/**
 * 授权管理控制器：用户 / 角色 / 权限的 CRUD。
 *
 * <p>本控制器是 RBAC 授权管理面，提供完整的 RBAC 资源管理 API：
 * <ul>
 *   <li>{@code /rbac/admin/users} — 用户 CRUD + 分配角色 + 查询权限（含角色继承）</li>
 *   <li>{@code /rbac/admin/roles} — 角色 CRUD + 分配权限</li>
 *   <li>{@code /rbac/admin/permissions} — 权限 CRUD</li>
 * </ul>
 *
 * <p>本控制器与认证框架（sa-token/shiro/security）解耦，仅通过 {@link RbacService} 操作仓储。
 * 业务代码（User/Role/Permission/Repository/Service）与 Spring 示例
 * {@code ddd4j-sample-spring-satoken} 完全一致，仅 Controller 层使用 Javalin 风格。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class AuthorizationController {

    private final RbacService rbacService;

    public AuthorizationController(RbacService rbacService) {
        this.rbacService = Objects.requireNonNull(rbacService, "rbacService must not be null");
    }

    /**
     * 注册 RBAC 授权管理路由（建议在 {@code path("rbac", ...)} 内调用，使路径变为 {@code /rbac/admin/*}）。
     */
    public EndpointGroup routes() {
        return () -> {
            // ============================ 用户管理 ============================

            // POST /admin/users —— 创建用户
            post("/admin/users", ctx -> {
                CreateUserRequest req = ctx.bodyAsClass(CreateUserRequest.class);
                User user = rbacService.createUser(req.userId(), req.username(), req.password(), req.realName());
                ctx.status(201).json(R.ok(Map.of("userId", user.id())));
            });

            // GET /admin/users —— 用户列表
            get("/admin/users", ctx -> ctx.json(R.ok(rbacService.listUsers())));

            // GET /admin/users/{id} —— 用户详情
            get("/admin/users/{id}", ctx -> ctx.json(R.ok(rbacService.getUser(ctx.pathParam("id")))));

            // PUT /admin/users/{id} —— 更新用户
            put("/admin/users/{id}", ctx -> {
                UpdateUserRequest req = ctx.bodyAsClass(UpdateUserRequest.class);
                User updated = rbacService.updateUser(ctx.pathParam("id"), req.realName(), req.password(), req.status());
                ctx.json(R.ok(Map.of("userId", updated.id())));
            });

            // DELETE /admin/users/{id} —— 删除用户
            delete("/admin/users/{id}", ctx -> {
                String id = ctx.pathParam("id");
                rbacService.deleteUser(id);
                ctx.json(R.ok(Map.of("deleted", id)));
            });

            // POST /admin/users/{id}/roles —— 给用户分配角色（全量替换）
            post("/admin/users/{id}/roles", ctx -> {
                String id = ctx.pathParam("id");
                AssignRolesRequest req = ctx.bodyAsClass(AssignRolesRequest.class);
                User user = rbacService.assignRolesToUser(id, new HashSet<>(req.roleIds()));
                ctx.json(R.ok(Map.of("userId", user.id(), "roleIds", user.getRoleIds())));
            });

            // GET /admin/users/{id}/permissions —— 获取用户所有权限（含角色继承）
            get("/admin/users/{id}/permissions", ctx -> {
                String id = ctx.pathParam("id");
                Set<String> roleCodes = rbacService.listRoleCodesOfUser(id);
                Set<String> permissionCodes = rbacService.listPermissionCodesOfUser(id);
                ctx.json(R.ok(Map.of("userId", id, "roles", roleCodes, "permissions", permissionCodes)));
            });

            // ============================ 角色管理 ============================

            // POST /admin/roles —— 创建角色
            post("/admin/roles", ctx -> {
                CreateRoleRequest req = ctx.bodyAsClass(CreateRoleRequest.class);
                Role role = rbacService.createRole(req.roleId(), req.roleCode(), req.roleName(), req.description());
                ctx.status(201).json(R.ok(Map.of("roleId", role.id())));
            });

            // GET /admin/roles —— 角色列表
            get("/admin/roles", ctx -> ctx.json(R.ok(rbacService.listRoles())));

            // GET /admin/roles/{id} —— 角色详情
            get("/admin/roles/{id}", ctx -> ctx.json(R.ok(rbacService.getRole(ctx.pathParam("id")))));

            // PUT /admin/roles/{id} —— 更新角色
            put("/admin/roles/{id}", ctx -> {
                UpdateRoleRequest req = ctx.bodyAsClass(UpdateRoleRequest.class);
                Role updated = rbacService.updateRole(ctx.pathParam("id"), req.roleName(), req.description(), req.status());
                ctx.json(R.ok(Map.of("roleId", updated.id())));
            });

            // DELETE /admin/roles/{id} —— 删除角色
            delete("/admin/roles/{id}", ctx -> {
                String id = ctx.pathParam("id");
                rbacService.deleteRole(id);
                ctx.json(R.ok(Map.of("deleted", id)));
            });

            // POST /admin/roles/{id}/permissions —— 给角色分配权限（全量替换）
            post("/admin/roles/{id}/permissions", ctx -> {
                String id = ctx.pathParam("id");
                AssignPermissionsRequest req = ctx.bodyAsClass(AssignPermissionsRequest.class);
                Role role = rbacService.assignPermissionsToRole(id, new HashSet<>(req.permissionIds()));
                ctx.json(R.ok(Map.of("roleId", role.id(), "permissionIds", role.getPermissionIds())));
            });

            // GET /admin/roles/{id}/permissions —— 获取角色的权限编码集合
            get("/admin/roles/{id}/permissions", ctx -> {
                String id = ctx.pathParam("id");
                Set<String> codes = rbacService.listPermissionCodesOfRole(id);
                ctx.json(R.ok(Map.of("roleId", id, "permissions", codes)));
            });

            // ============================ 权限管理 ============================

            // POST /admin/permissions —— 创建权限
            post("/admin/permissions", ctx -> {
                CreatePermissionRequest req = ctx.bodyAsClass(CreatePermissionRequest.class);
                Permission permission = rbacService.createPermission(req.permissionId(), req.permissionCode(),
                        req.permissionName(), req.module());
                ctx.status(201).json(R.ok(Map.of("permissionId", permission.id())));
            });

            // GET /admin/permissions —— 权限列表
            get("/admin/permissions", ctx -> ctx.json(R.ok(rbacService.listPermissions())));

            // GET /admin/permissions/{id} —— 权限详情
            get("/admin/permissions/{id}", ctx -> ctx.json(R.ok(rbacService.getPermission(ctx.pathParam("id")))));

            // PUT /admin/permissions/{id} —— 更新权限
            put("/admin/permissions/{id}", ctx -> {
                UpdatePermissionRequest req = ctx.bodyAsClass(UpdatePermissionRequest.class);
                Permission updated = rbacService.updatePermission(ctx.pathParam("id"), req.permissionName(), req.module(), req.status());
                ctx.json(R.ok(Map.of("permissionId", updated.id())));
            });

            // DELETE /admin/permissions/{id} —— 删除权限
            delete("/admin/permissions/{id}", ctx -> {
                String id = ctx.pathParam("id");
                rbacService.deletePermission(id);
                ctx.json(R.ok(Map.of("deleted", id)));
            });
        };
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
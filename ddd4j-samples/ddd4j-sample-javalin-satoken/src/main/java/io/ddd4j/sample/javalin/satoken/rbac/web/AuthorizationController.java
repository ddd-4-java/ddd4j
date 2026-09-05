package io.ddd4j.sample.javalin.satoken.rbac.web;

import java.util.Collections;
import java.util.Objects;

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
 * 外部 {@code ddd4j-boot-sample-auth-satoken} 保持同一 Subject 鉴权语义，仅 Controller 层使用 Javalin 风格。
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
                ctx.json(R.ok(Collections.singletonMap("deleted", id)));
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
                ctx.json(R.ok(Collections.singletonMap("deleted", id)));
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
                ctx.json(R.ok(Collections.singletonMap("deleted", id)));
            });
        };
    }

    // ============================ 请求/响应 DTO ============================public final class CreateUserRequest {
        private final String userId;
        private final String username;
        private final String password;
        private final String realName;

        public CreateUserRequest(String userId, String username, String password, String realName) {
            this.userId = userId;
            this.username = username;
            this.password = password;
            this.realName = realName;
        }
        public String userId() { return userId; }
        public String username() { return username; }
        public String password() { return password; }
        public String realName() { return realName; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        CreateUserRequest other = (CreateUserRequest) o;
            return Objects.equals(this.userId, other.userId) && Objects.equals(this.username, other.username) && Objects.equals(this.password, other.password) && Objects.equals(this.realName, other.realName);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(userId, username, password, realName); }
        @Override
        public String toString() {
            return "CreateUserRequest{" + "userId=" + userId + ", " + "username=" + username + ", " + "password=" + password + ", " + "realName=" + realName + "}";
        }
    
    }public final class UpdateUserRequest {
        private final String realName;
        private final String password;
        private final User.Status status;

        public UpdateUserRequest(String realName, String password, User.Status status) {
            this.realName = realName;
            this.password = password;
            this.status = status;
        }
        public String realName() { return realName; }
        public String password() { return password; }
        public User.Status status() { return status; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        UpdateUserRequest other = (UpdateUserRequest) o;
            return Objects.equals(this.realName, other.realName) && Objects.equals(this.password, other.password) && Objects.equals(this.status, other.status);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(realName, password, status); }
        @Override
        public String toString() {
            return "UpdateUserRequest{" + "realName=" + realName + ", " + "password=" + password + ", " + "status=" + status + "}";
        }
    
    }public final class AssignRolesRequest {
        private final List<String> roleIds;

        public AssignRolesRequest(List<String> roleIds) {
            this.roleIds = roleIds;
        }
        public List<String> roleIds() { return roleIds; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        AssignRolesRequest other = (AssignRolesRequest) o;
            return Objects.equals(this.roleIds, other.roleIds);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(roleIds); }
        @Override
        public String toString() {
            return "AssignRolesRequest{" + "roleIds=" + roleIds + "}";
        }
    
    }public final class CreateRoleRequest {
        private final String roleId;
        private final String roleCode;
        private final String roleName;
        private final String description;

        public CreateRoleRequest(String roleId, String roleCode, String roleName, String description) {
            this.roleId = roleId;
            this.roleCode = roleCode;
            this.roleName = roleName;
            this.description = description;
        }
        public String roleId() { return roleId; }
        public String roleCode() { return roleCode; }
        public String roleName() { return roleName; }
        public String description() { return description; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        CreateRoleRequest other = (CreateRoleRequest) o;
            return Objects.equals(this.roleId, other.roleId) && Objects.equals(this.roleCode, other.roleCode) && Objects.equals(this.roleName, other.roleName) && Objects.equals(this.description, other.description);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(roleId, roleCode, roleName, description); }
        @Override
        public String toString() {
            return "CreateRoleRequest{" + "roleId=" + roleId + ", " + "roleCode=" + roleCode + ", " + "roleName=" + roleName + ", " + "description=" + description + "}";
        }
    
    }public final class UpdateRoleRequest {
        private final String roleName;
        private final String description;
        private final Role.Status status;

        public UpdateRoleRequest(String roleName, String description, Role.Status status) {
            this.roleName = roleName;
            this.description = description;
            this.status = status;
        }
        public String roleName() { return roleName; }
        public String description() { return description; }
        public Role.Status status() { return status; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        UpdateRoleRequest other = (UpdateRoleRequest) o;
            return Objects.equals(this.roleName, other.roleName) && Objects.equals(this.description, other.description) && Objects.equals(this.status, other.status);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(roleName, description, status); }
        @Override
        public String toString() {
            return "UpdateRoleRequest{" + "roleName=" + roleName + ", " + "description=" + description + ", " + "status=" + status + "}";
        }
    
    }public final class AssignPermissionsRequest {
        private final List<String> permissionIds;

        public AssignPermissionsRequest(List<String> permissionIds) {
            this.permissionIds = permissionIds;
        }
        public List<String> permissionIds() { return permissionIds; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        AssignPermissionsRequest other = (AssignPermissionsRequest) o;
            return Objects.equals(this.permissionIds, other.permissionIds);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(permissionIds); }
        @Override
        public String toString() {
            return "AssignPermissionsRequest{" + "permissionIds=" + permissionIds + "}";
        }
    
    }public final class CreatePermissionRequest {
        private final String permissionId;
        private final String permissionCode;
        private final String permissionName;
        private final String module;

        public CreatePermissionRequest(String permissionId, String permissionCode, String permissionName, String module) {
            this.permissionId = permissionId;
            this.permissionCode = permissionCode;
            this.permissionName = permissionName;
            this.module = module;
        }
        public String permissionId() { return permissionId; }
        public String permissionCode() { return permissionCode; }
        public String permissionName() { return permissionName; }
        public String module() { return module; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        CreatePermissionRequest other = (CreatePermissionRequest) o;
            return Objects.equals(this.permissionId, other.permissionId) && Objects.equals(this.permissionCode, other.permissionCode) && Objects.equals(this.permissionName, other.permissionName) && Objects.equals(this.module, other.module);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(permissionId, permissionCode, permissionName, module); }
        @Override
        public String toString() {
            return "CreatePermissionRequest{" + "permissionId=" + permissionId + ", " + "permissionCode=" + permissionCode + ", " + "permissionName=" + permissionName + ", " + "module=" + module + "}";
        }
    
    }public final class UpdatePermissionRequest {
        private final String permissionName;
        private final String module;
        private final Permission.Status status;

        public UpdatePermissionRequest(String permissionName, String module, Permission.Status status) {
            this.permissionName = permissionName;
            this.module = module;
            this.status = status;
        }
        public String permissionName() { return permissionName; }
        public String module() { return module; }
        public Permission.Status status() { return status; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        UpdatePermissionRequest other = (UpdatePermissionRequest) o;
            return Objects.equals(this.permissionName, other.permissionName) && Objects.equals(this.module, other.module) && Objects.equals(this.status, other.status);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(permissionName, module, status); }
        @Override
        public String toString() {
            return "UpdatePermissionRequest{" + "permissionName=" + permissionName + ", " + "module=" + module + ", " + "status=" + status + "}";
        }
    
    }

}

package io.ddd4j.sample.javalin.satoken.rbac.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

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

    public final static class CreateUserRequest {

        private static final long serialVersionUID = 0L;

        private final String userId;

        private final String username;

        private final String password;

        private final String realName;

        @JsonCreator()
        public CreateUserRequest(@JsonProperty("userId") String userId, @JsonProperty("username") String username, @JsonProperty("password") String password, @JsonProperty("realName") String realName) {
            this.userId = userId;
            this.username = username;
            this.password = password;
            this.realName = realName;
        }

        @JsonProperty("userId")
        public String userId() {
            return userId;
        }

        @JsonProperty("username")
        public String username() {
            return username;
        }

        @JsonProperty("password")
        public String password() {
            return password;
        }

        @JsonProperty("realName")
        public String realName() {
            return realName;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            CreateUserRequest other = (CreateUserRequest) obj;
            return Objects.equals(this.userId, other.userId) && Objects.equals(this.username, other.username) && Objects.equals(this.password, other.password) && Objects.equals(this.realName, other.realName);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(userId);
            result = 31 * result + Objects.hashCode(username);
            result = 31 * result + Objects.hashCode(password);
            result = 31 * result + Objects.hashCode(realName);
            return result;
        }

        @Override
        public String toString() {
            return "CreateUserRequest[userId=" + userId + ", username=" + username + ", password=" + password + ", realName=" + realName + "]";
        }
    }

    public final static class UpdateUserRequest {

        private static final long serialVersionUID = 0L;

        private final String realName;

        private final String password;

        private final User.Status status;

        @JsonCreator()
        public UpdateUserRequest(@JsonProperty("realName") String realName, @JsonProperty("password") String password, @JsonProperty("status") User.Status status) {
            this.realName = realName;
            this.password = password;
            this.status = status;
        }

        @JsonProperty("realName")
        public String realName() {
            return realName;
        }

        @JsonProperty("password")
        public String password() {
            return password;
        }

        @JsonProperty("status")
        public User.Status status() {
            return status;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            UpdateUserRequest other = (UpdateUserRequest) obj;
            return Objects.equals(this.realName, other.realName) && Objects.equals(this.password, other.password) && Objects.equals(this.status, other.status);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(realName);
            result = 31 * result + Objects.hashCode(password);
            result = 31 * result + Objects.hashCode(status);
            return result;
        }

        @Override
        public String toString() {
            return "UpdateUserRequest[realName=" + realName + ", password=" + password + ", status=" + status + "]";
        }
    }

    public final static class AssignRolesRequest {

        private static final long serialVersionUID = 0L;

        private final List<String> roleIds;

        @JsonCreator()
        public AssignRolesRequest(@JsonProperty("roleIds") List<String> roleIds) {
            this.roleIds = roleIds;
        }

        @JsonProperty("roleIds")
        public List<String> roleIds() {
            return roleIds;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            AssignRolesRequest other = (AssignRolesRequest) obj;
            return Objects.equals(this.roleIds, other.roleIds);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(roleIds);
            return result;
        }

        @Override
        public String toString() {
            return "AssignRolesRequest[roleIds=" + roleIds + "]";
        }
    }

    public final static class CreateRoleRequest {

        private static final long serialVersionUID = 0L;

        private final String roleId;

        private final String roleCode;

        private final String roleName;

        private final String description;

        @JsonCreator()
        public CreateRoleRequest(@JsonProperty("roleId") String roleId, @JsonProperty("roleCode") String roleCode, @JsonProperty("roleName") String roleName, @JsonProperty("description") String description) {
            this.roleId = roleId;
            this.roleCode = roleCode;
            this.roleName = roleName;
            this.description = description;
        }

        @JsonProperty("roleId")
        public String roleId() {
            return roleId;
        }

        @JsonProperty("roleCode")
        public String roleCode() {
            return roleCode;
        }

        @JsonProperty("roleName")
        public String roleName() {
            return roleName;
        }

        @JsonProperty("description")
        public String description() {
            return description;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            CreateRoleRequest other = (CreateRoleRequest) obj;
            return Objects.equals(this.roleId, other.roleId) && Objects.equals(this.roleCode, other.roleCode) && Objects.equals(this.roleName, other.roleName) && Objects.equals(this.description, other.description);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(roleId);
            result = 31 * result + Objects.hashCode(roleCode);
            result = 31 * result + Objects.hashCode(roleName);
            result = 31 * result + Objects.hashCode(description);
            return result;
        }

        @Override
        public String toString() {
            return "CreateRoleRequest[roleId=" + roleId + ", roleCode=" + roleCode + ", roleName=" + roleName + ", description=" + description + "]";
        }
    }

    public final static class UpdateRoleRequest {

        private static final long serialVersionUID = 0L;

        private final String roleName;

        private final String description;

        private final Role.Status status;

        @JsonCreator()
        public UpdateRoleRequest(@JsonProperty("roleName") String roleName, @JsonProperty("description") String description, @JsonProperty("status") Role.Status status) {
            this.roleName = roleName;
            this.description = description;
            this.status = status;
        }

        @JsonProperty("roleName")
        public String roleName() {
            return roleName;
        }

        @JsonProperty("description")
        public String description() {
            return description;
        }

        @JsonProperty("status")
        public Role.Status status() {
            return status;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            UpdateRoleRequest other = (UpdateRoleRequest) obj;
            return Objects.equals(this.roleName, other.roleName) && Objects.equals(this.description, other.description) && Objects.equals(this.status, other.status);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(roleName);
            result = 31 * result + Objects.hashCode(description);
            result = 31 * result + Objects.hashCode(status);
            return result;
        }

        @Override
        public String toString() {
            return "UpdateRoleRequest[roleName=" + roleName + ", description=" + description + ", status=" + status + "]";
        }
    }

    public final static class AssignPermissionsRequest {

        private static final long serialVersionUID = 0L;

        private final List<String> permissionIds;

        @JsonCreator()
        public AssignPermissionsRequest(@JsonProperty("permissionIds") List<String> permissionIds) {
            this.permissionIds = permissionIds;
        }

        @JsonProperty("permissionIds")
        public List<String> permissionIds() {
            return permissionIds;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            AssignPermissionsRequest other = (AssignPermissionsRequest) obj;
            return Objects.equals(this.permissionIds, other.permissionIds);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(permissionIds);
            return result;
        }

        @Override
        public String toString() {
            return "AssignPermissionsRequest[permissionIds=" + permissionIds + "]";
        }
    }

    public final static class CreatePermissionRequest {

        private static final long serialVersionUID = 0L;

        private final String permissionId;

        private final String permissionCode;

        private final String permissionName;

        private final String module;

        @JsonCreator()
        public CreatePermissionRequest(@JsonProperty("permissionId") String permissionId, @JsonProperty("permissionCode") String permissionCode, @JsonProperty("permissionName") String permissionName, @JsonProperty("module") String module) {
            this.permissionId = permissionId;
            this.permissionCode = permissionCode;
            this.permissionName = permissionName;
            this.module = module;
        }

        @JsonProperty("permissionId")
        public String permissionId() {
            return permissionId;
        }

        @JsonProperty("permissionCode")
        public String permissionCode() {
            return permissionCode;
        }

        @JsonProperty("permissionName")
        public String permissionName() {
            return permissionName;
        }

        @JsonProperty("module")
        public String module() {
            return module;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            CreatePermissionRequest other = (CreatePermissionRequest) obj;
            return Objects.equals(this.permissionId, other.permissionId) && Objects.equals(this.permissionCode, other.permissionCode) && Objects.equals(this.permissionName, other.permissionName) && Objects.equals(this.module, other.module);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(permissionId);
            result = 31 * result + Objects.hashCode(permissionCode);
            result = 31 * result + Objects.hashCode(permissionName);
            result = 31 * result + Objects.hashCode(module);
            return result;
        }

        @Override
        public String toString() {
            return "CreatePermissionRequest[permissionId=" + permissionId + ", permissionCode=" + permissionCode + ", permissionName=" + permissionName + ", module=" + module + "]";
        }
    }

    public final static class UpdatePermissionRequest {

        private static final long serialVersionUID = 0L;

        private final String permissionName;

        private final String module;

        private final Permission.Status status;

        @JsonCreator()
        public UpdatePermissionRequest(@JsonProperty("permissionName") String permissionName, @JsonProperty("module") String module, @JsonProperty("status") Permission.Status status) {
            this.permissionName = permissionName;
            this.module = module;
            this.status = status;
        }

        @JsonProperty("permissionName")
        public String permissionName() {
            return permissionName;
        }

        @JsonProperty("module")
        public String module() {
            return module;
        }

        @JsonProperty("status")
        public Permission.Status status() {
            return status;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            UpdatePermissionRequest other = (UpdatePermissionRequest) obj;
            return Objects.equals(this.permissionName, other.permissionName) && Objects.equals(this.module, other.module) && Objects.equals(this.status, other.status);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(permissionName);
            result = 31 * result + Objects.hashCode(module);
            result = 31 * result + Objects.hashCode(status);
            return result;
        }

        @Override
        public String toString() {
            return "UpdatePermissionRequest[permissionName=" + permissionName + ", module=" + module + ", status=" + status + "]";
        }
    }

}

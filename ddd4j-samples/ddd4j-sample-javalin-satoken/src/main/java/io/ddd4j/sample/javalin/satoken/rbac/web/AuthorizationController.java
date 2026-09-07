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
package io.ddd4j.sample.javalin.satoken.rbac.web;

import java.util.Objects;

import io.ddd4j.core.api.R;
import io.ddd4j.sample.javalin.satoken.rbac.application.RbacService;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Permission;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.Role;
import io.ddd4j.sample.javalin.satoken.rbac.domain.model.User;
import io.javalin.apibuilder.EndpointGroup;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
                ctx.status(201).json(R.ok(Java8Maps.of("userId", user.id())));
            });

            // GET /admin/users —— 用户列表
            get("/admin/users", ctx -> ctx.json(R.ok(rbacService.listUsers())));

            // GET /admin/users/{id} —— 用户详情
            get("/admin/users/{id}", ctx -> ctx.json(R.ok(rbacService.getUser(ctx.pathParam("id")))));

            // PUT /admin/users/{id} —— 更新用户
            put("/admin/users/{id}", ctx -> {
                UpdateUserRequest req = ctx.bodyAsClass(UpdateUserRequest.class);
                User updated = rbacService.updateUser(ctx.pathParam("id"), req.realName(), req.password(), req.status());
                ctx.json(R.ok(Java8Maps.of("userId", updated.id())));
            });

            // DELETE /admin/users/{id} —— 删除用户
            delete("/admin/users/{id}", ctx -> {
                String id = ctx.pathParam("id");
                rbacService.deleteUser(id);
                ctx.json(R.ok(Java8Maps.of("deleted", id)));
            });

            // POST /admin/users/{id}/roles —— 给用户分配角色（全量替换）
            post("/admin/users/{id}/roles", ctx -> {
                String id = ctx.pathParam("id");
                AssignRolesRequest req = ctx.bodyAsClass(AssignRolesRequest.class);
                User user = rbacService.assignRolesToUser(id, new HashSet<>(req.roleIds()));
                ctx.json(R.ok(Java8Maps.of("userId", user.id(), "roleIds", user.getRoleIds())));
            });

            // GET /admin/users/{id}/permissions —— 获取用户所有权限（含角色继承）
            get("/admin/users/{id}/permissions", ctx -> {
                String id = ctx.pathParam("id");
                Set<String> roleCodes = rbacService.listRoleCodesOfUser(id);
                Set<String> permissionCodes = rbacService.listPermissionCodesOfUser(id);
                ctx.json(R.ok(Java8Maps.of("userId", id, "roles", roleCodes, "permissions", permissionCodes)));
            });

            // ============================ 角色管理 ============================

            // POST /admin/roles —— 创建角色
            post("/admin/roles", ctx -> {
                CreateRoleRequest req = ctx.bodyAsClass(CreateRoleRequest.class);
                Role role = rbacService.createRole(req.roleId(), req.roleCode(), req.roleName(), req.description());
                ctx.status(201).json(R.ok(Java8Maps.of("roleId", role.id())));
            });

            // GET /admin/roles —— 角色列表
            get("/admin/roles", ctx -> ctx.json(R.ok(rbacService.listRoles())));

            // GET /admin/roles/{id} —— 角色详情
            get("/admin/roles/{id}", ctx -> ctx.json(R.ok(rbacService.getRole(ctx.pathParam("id")))));

            // PUT /admin/roles/{id} —— 更新角色
            put("/admin/roles/{id}", ctx -> {
                UpdateRoleRequest req = ctx.bodyAsClass(UpdateRoleRequest.class);
                Role updated = rbacService.updateRole(ctx.pathParam("id"), req.roleName(), req.description(), req.status());
                ctx.json(R.ok(Java8Maps.of("roleId", updated.id())));
            });

            // DELETE /admin/roles/{id} —— 删除角色
            delete("/admin/roles/{id}", ctx -> {
                String id = ctx.pathParam("id");
                rbacService.deleteRole(id);
                ctx.json(R.ok(Java8Maps.of("deleted", id)));
            });

            // POST /admin/roles/{id}/permissions —— 给角色分配权限（全量替换）
            post("/admin/roles/{id}/permissions", ctx -> {
                String id = ctx.pathParam("id");
                AssignPermissionsRequest req = ctx.bodyAsClass(AssignPermissionsRequest.class);
                Role role = rbacService.assignPermissionsToRole(id, new HashSet<>(req.permissionIds()));
                ctx.json(R.ok(Java8Maps.of("roleId", role.id(), "permissionIds", role.getPermissionIds())));
            });

            // GET /admin/roles/{id}/permissions —— 获取角色的权限编码集合
            get("/admin/roles/{id}/permissions", ctx -> {
                String id = ctx.pathParam("id");
                Set<String> codes = rbacService.listPermissionCodesOfRole(id);
                ctx.json(R.ok(Java8Maps.of("roleId", id, "permissions", codes)));
            });

            // ============================ 权限管理 ============================

            // POST /admin/permissions —— 创建权限
            post("/admin/permissions", ctx -> {
                CreatePermissionRequest req = ctx.bodyAsClass(CreatePermissionRequest.class);
                Permission permission = rbacService.createPermission(req.permissionId(), req.permissionCode(),
                        req.permissionName(), req.module());
                ctx.status(201).json(R.ok(Java8Maps.of("permissionId", permission.id())));
            });

            // GET /admin/permissions —— 权限列表
            get("/admin/permissions", ctx -> ctx.json(R.ok(rbacService.listPermissions())));

            // GET /admin/permissions/{id} —— 权限详情
            get("/admin/permissions/{id}", ctx -> ctx.json(R.ok(rbacService.getPermission(ctx.pathParam("id")))));

            // PUT /admin/permissions/{id} —— 更新权限
            put("/admin/permissions/{id}", ctx -> {
                UpdatePermissionRequest req = ctx.bodyAsClass(UpdatePermissionRequest.class);
                Permission updated = rbacService.updatePermission(ctx.pathParam("id"), req.permissionName(), req.module(), req.status());
                ctx.json(R.ok(Java8Maps.of("permissionId", updated.id())));
            });

            // DELETE /admin/permissions/{id} —— 删除权限
            delete("/admin/permissions/{id}", ctx -> {
                String id = ctx.pathParam("id");
                rbacService.deletePermission(id);
                ctx.json(R.ok(Java8Maps.of("deleted", id)));
            });
        };
    }

    // ============================ 请求/响应 DTO ============================

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateUserRequest {
        private String userId; private String username; private String password; private String realName;
        public String userId() { return userId; } public String username() { return username; }
        public String password() { return password; } public String realName() { return realName; }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UpdateUserRequest {
        private String realName; private String password; private User.Status status;
        public String realName() { return realName; } public String password() { return password; }
        public User.Status status() { return status; }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class AssignRolesRequest {
        private List<String> roleIds;
        public List<String> roleIds() { return roleIds; }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateRoleRequest {
        private String roleId; private String roleCode; private String roleName; private String description;
        public String roleId() { return roleId; } public String roleCode() { return roleCode; }
        public String roleName() { return roleName; } public String description() { return description; }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UpdateRoleRequest {
        private String roleName; private String description; private Role.Status status;
        public String roleName() { return roleName; } public String description() { return description; }
        public Role.Status status() { return status; }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class AssignPermissionsRequest {
        private List<String> permissionIds;
        public List<String> permissionIds() { return permissionIds; }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreatePermissionRequest {
        private String permissionId; private String permissionCode; private String permissionName; private String module;
        public String permissionId() { return permissionId; } public String permissionCode() { return permissionCode; }
        public String permissionName() { return permissionName; } public String module() { return module; }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UpdatePermissionRequest {
        private String permissionName; private String module; private Permission.Status status;
        public String permissionName() { return permissionName; } public String module() { return module; }
        public Permission.Status status() { return status; }
    }

}

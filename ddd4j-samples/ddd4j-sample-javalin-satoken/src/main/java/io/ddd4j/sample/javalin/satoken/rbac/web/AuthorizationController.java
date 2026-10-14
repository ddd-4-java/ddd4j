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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

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

    /**
     * 创建授权控制器。
     *
     * @param rbacService RBAC 服务
     */
    public AuthorizationController(RbacService rbacService) {
        this.rbacService = Objects.requireNonNull(rbacService, "rbacService must not be null");
    }

    /**
     * 注册 RBAC 授权管理路由（建议在 {@code path("rbac", ...)} 内调用，使路径变为 {@code /rbac/admin/*}）。
     *
     * @return 路由组
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

    /**
     * 创建用户请求。
     */
    @Data
    @NoArgsConstructor
    public static class CreateUserRequest {

        private static final long serialVersionUID = 0L;

        private String userId;

        private String username;

        @ToString.Exclude
        private String password;

        private String realName;

        /**
         * 创建创建用户请求。
         *
         * @param userId 用户标识
         * @param username 用户名
         * @param password 密码
         * @param realName 真实姓名
         */
        @JsonCreator()
        public CreateUserRequest(@JsonProperty("userId") String userId, @JsonProperty("username") String username, @JsonProperty("password") String password, @JsonProperty("realName") String realName) {
            this.userId = userId;
            this.username = username;
            this.password = password;
            this.realName = realName;
        }

        /**
         * 读取用户标识。
         *
         * @return 用户标识
         */
        @JsonProperty("userId")
        public String userId() {
            return userId;
        }

        /**
         * 读取用户名。
         *
         * @return 用户名
         */
        @JsonProperty("username")
        public String username() {
            return username;
        }

        /**
         * 读取密码。
         *
         * @return 密码
         */
        @JsonProperty("password")
        public String password() {
            return password;
        }

        /**
         * 读取真实姓名。
         *
         * @return 真实姓名
         */
        @JsonProperty("realName")
        public String realName() {
            return realName;
        }

    }

    /**
     * 更新用户请求。
     */
    @Data
    @NoArgsConstructor
    public static class UpdateUserRequest {

        private static final long serialVersionUID = 0L;

        private String realName;

        @ToString.Exclude
        private String password;

        private User.Status status;

        /**
         * 创建更新用户请求。
         *
         * @param realName 真实姓名
         * @param password 密码
         * @param status 状态
         */
        @JsonCreator()
        public UpdateUserRequest(@JsonProperty("realName") String realName, @JsonProperty("password") String password, @JsonProperty("status") User.Status status) {
            this.realName = realName;
            this.password = password;
            this.status = status;
        }

        /**
         * 读取真实姓名。
         *
         * @return 真实姓名
         */
        @JsonProperty("realName")
        public String realName() {
            return realName;
        }

        /**
         * 读取密码。
         *
         * @return 密码
         */
        @JsonProperty("password")
        public String password() {
            return password;
        }

        /**
         * 读取状态。
         *
         * @return 状态
         */
        @JsonProperty("status")
        public User.Status status() {
            return status;
        }

    }

    /**
     * 分配角色请求。
     */
    @Data
    @NoArgsConstructor
    public static class AssignRolesRequest {

        private static final long serialVersionUID = 0L;

        private List<String> roleIds;

        /**
         * 创建分配角色请求。
         *
         * @param roleIds 角色标识集合
         */
        @JsonCreator()
        public AssignRolesRequest(@JsonProperty("roleIds") List<String> roleIds) {
            this.roleIds = roleIds;
        }

        /**
         * 读取角色标识集合。
         *
         * @return 角色标识集合
         */
        @JsonProperty("roleIds")
        public List<String> roleIds() {
            return roleIds;
        }

    }

    /**
     * 创建角色请求。
     */
    @Data
    @NoArgsConstructor
    public static class CreateRoleRequest {

        private static final long serialVersionUID = 0L;

        private String roleId;

        private String roleCode;

        private String roleName;

        private String description;

        /**
         * 创建创建角色请求。
         *
         * @param roleId 角色标识
         * @param roleCode 角色编码
         * @param roleName 角色名称
         * @param description 描述
         */
        @JsonCreator()
        public CreateRoleRequest(@JsonProperty("roleId") String roleId, @JsonProperty("roleCode") String roleCode, @JsonProperty("roleName") String roleName, @JsonProperty("description") String description) {
            this.roleId = roleId;
            this.roleCode = roleCode;
            this.roleName = roleName;
            this.description = description;
        }

        /**
         * 读取角色标识。
         *
         * @return 角色标识
         */
        @JsonProperty("roleId")
        public String roleId() {
            return roleId;
        }

        /**
         * 读取角色编码。
         *
         * @return 角色编码
         */
        @JsonProperty("roleCode")
        public String roleCode() {
            return roleCode;
        }

        /**
         * 读取角色名称。
         *
         * @return 角色名称
         */
        @JsonProperty("roleName")
        public String roleName() {
            return roleName;
        }

        /**
         * 读取描述。
         *
         * @return 描述
         */
        @JsonProperty("description")
        public String description() {
            return description;
        }

    }

    /**
     * 更新角色请求。
     */
    @Data
    @NoArgsConstructor
    public static class UpdateRoleRequest {

        private static final long serialVersionUID = 0L;

        private String roleName;

        private String description;

        private Role.Status status;

        /**
         * 创建更新角色请求。
         *
         * @param roleName 角色名称
         * @param description 描述
         * @param status 状态
         */
        @JsonCreator()
        public UpdateRoleRequest(@JsonProperty("roleName") String roleName, @JsonProperty("description") String description, @JsonProperty("status") Role.Status status) {
            this.roleName = roleName;
            this.description = description;
            this.status = status;
        }

        /**
         * 读取角色名称。
         *
         * @return 角色名称
         */
        @JsonProperty("roleName")
        public String roleName() {
            return roleName;
        }

        /**
         * 读取描述。
         *
         * @return 描述
         */
        @JsonProperty("description")
        public String description() {
            return description;
        }

        /**
         * 读取状态。
         *
         * @return 状态
         */
        @JsonProperty("status")
        public Role.Status status() {
            return status;
        }

    }

    /**
     * 分配权限请求。
     */
    @Data
    @NoArgsConstructor
    public static class AssignPermissionsRequest {

        private static final long serialVersionUID = 0L;

        private List<String> permissionIds;

        /**
         * 创建分配权限请求。
         *
         * @param permissionIds 权限标识集合
         */
        @JsonCreator()
        public AssignPermissionsRequest(@JsonProperty("permissionIds") List<String> permissionIds) {
            this.permissionIds = permissionIds;
        }

        /**
         * 读取权限标识集合。
         *
         * @return 权限标识集合
         */
        @JsonProperty("permissionIds")
        public List<String> permissionIds() {
            return permissionIds;
        }

    }

    /**
     * 创建权限请求。
     */
    @Data
    @NoArgsConstructor
    public static class CreatePermissionRequest {

        private static final long serialVersionUID = 0L;

        private String permissionId;

        private String permissionCode;

        private String permissionName;

        private String module;

        /**
         * 创建创建权限请求。
         *
         * @param permissionId 权限标识
         * @param permissionCode 权限编码
         * @param permissionName 权限名称
         * @param module 所属模块
         */
        @JsonCreator()
        public CreatePermissionRequest(@JsonProperty("permissionId") String permissionId, @JsonProperty("permissionCode") String permissionCode, @JsonProperty("permissionName") String permissionName, @JsonProperty("module") String module) {
            this.permissionId = permissionId;
            this.permissionCode = permissionCode;
            this.permissionName = permissionName;
            this.module = module;
        }

        /**
         * 读取权限标识。
         *
         * @return 权限标识
         */
        @JsonProperty("permissionId")
        public String permissionId() {
            return permissionId;
        }

        /**
         * 读取权限编码。
         *
         * @return 权限编码
         */
        @JsonProperty("permissionCode")
        public String permissionCode() {
            return permissionCode;
        }

        /**
         * 读取权限名称。
         *
         * @return 权限名称
         */
        @JsonProperty("permissionName")
        public String permissionName() {
            return permissionName;
        }

        /**
         * 读取所属模块。
         *
         * @return 所属模块
         */
        @JsonProperty("module")
        public String module() {
            return module;
        }

    }

    /**
     * 更新权限请求。
     */
    @Data
    @NoArgsConstructor
    public static class UpdatePermissionRequest {

        private static final long serialVersionUID = 0L;

        private String permissionName;

        private String module;

        private Permission.Status status;

        /**
         * 创建更新权限请求。
         *
         * @param permissionName 权限名称
         * @param module 所属模块
         * @param status 状态
         */
        @JsonCreator()
        public UpdatePermissionRequest(@JsonProperty("permissionName") String permissionName, @JsonProperty("module") String module, @JsonProperty("status") Permission.Status status) {
            this.permissionName = permissionName;
            this.module = module;
            this.status = status;
        }

        /**
         * 读取权限名称。
         *
         * @return 权限名称
         */
        @JsonProperty("permissionName")
        public String permissionName() {
            return permissionName;
        }

        /**
         * 读取所属模块。
         *
         * @return 所属模块
         */
        @JsonProperty("module")
        public String module() {
            return module;
        }

        /**
         * 读取状态。
         *
         * @return 状态
         */
        @JsonProperty("status")
        public Permission.Status status() {
            return status;
        }

    }

}

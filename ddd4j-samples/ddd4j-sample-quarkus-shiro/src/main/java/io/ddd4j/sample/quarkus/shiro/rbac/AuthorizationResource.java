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
package io.ddd4j.sample.quarkus.shiro.rbac;

import io.ddd4j.core.api.R;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.List;

/**
 * RBAC 管理资源（JAX-RS）：用户 / 角色 / 权限 CRUD。
 *
 * <p>REST 端点：
 * <pre>
 *   GET    /admin/users                    列出全部用户           (login + user:list)
 *   GET    /admin/users/{id}              按 ID 查询用户          (login)
 *   POST   /admin/users                    创建用户               (login + admin role)
 *   PUT    /admin/users/{id}              更新用户               (login + admin role)
 *   DELETE /admin/users/{id}              删除用户               (login + admin role + user:delete)
 *   POST   /admin/users/{id}/roles/{code}  绑定角色到用户          (login + admin role)
 *   DELETE /admin/users/{id}/roles/{code}  解除用户角色           (login + admin role)
 *
 *   GET    /admin/roles                    列出全部角色           (login)
 *   GET    /admin/roles/{code}            按编码查询角色         (login)
 *   POST   /admin/roles                    创建角色               (login + admin role)
 *   PUT    /admin/roles/{code}            更新角色               (login + admin role)
 *   DELETE /admin/roles/{code}            删除角色               (login + admin role)
 *   POST   /admin/roles/{code}/permissions/{pcode}  授予角色权限 (login + admin role)
 *   DELETE /admin/roles/{code}/permissions/{pcode}  解除角色权限 (login + admin role)
 *
 *   GET    /admin/permissions              列出全部权限           (login)
 *   POST   /admin/permissions              创建权限               (login + admin role)
 *   DELETE /admin/permissions/{code}      删除权限               (login + admin role)
 * </pre>
 *
 * <p>本类仅做 HTTP 适配 + 鉴权调用，业务逻辑全部委托 {@link RbacService}。
 * 由于 Apache Shiro 在 Quarkus 中无开箱即用的方法级注解集成，鉴权通过显式调用
 * {@link RbacService#requireLogin()} / {@link RbacService#requirePermission(String)} /
 * {@link RbacService#requireRole(String)} 完成（与 Sa-Token 注解
 * {@code @SaCheckLogin} / {@code @SaCheckPermission} / {@code @SaCheckRole} 行为一致）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AuthorizationResource {

    /** \u6267\u884c\u57fa\u4e8e\u89d2\u8272\u7684\u6388\u6743\u4e0e\u5206\u914d\u64cd\u4f5c\u7684\u5e94\u7528\u670d\u52a1\u3002 */
    @Inject
    RbacService rbacService;

    // ============ User CRUD ============

    /**
     * \u9759\u6001\u5de5\u5177\uff1a\u629b\u51fa 401/403 \u54cd\u5e94\u3002
     *
     * @return \u8868\u793a\u5f53\u524d\u8bf7\u6c42\u672a\u901a\u8fc7\u8eab\u4efd\u9a8c\u8bc1\u7684\u54cd\u5e94
     */
    static WebApplicationException unauthorized() {
        return new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                .entity(R.of(401, "unauthenticated")).build());
    }

    /**
     * \u7528\u8bca\u65ad\u539f\u56e0\u6784\u9020\u5f53\u524d\u793a\u4f8b\u7684\u8bbf\u95ee\u62d2\u7edd\u7ed3\u679c\u3002
     * @param reason \u7528\u4e8e\u54cd\u5e94\u62d2\u7edd\u539f\u56e0\u7684\u8bf4\u660e
     * @return \u62d2\u7edd\u5f53\u524d\u8bf7\u6c42\u65f6\u8fd4\u56de\u7684\u4e1a\u52a1\u6216 HTTP \u9519\u8bef
     */
    static WebApplicationException forbidden(String reason) {
        return new WebApplicationException(Response.status(Response.Status.FORBIDDEN)
                .entity(R.of(403, reason)).build());
    }

    /**
     * 列出全部用户。
     *
     * @return 用户列表
     */
    @GET
    @Path("/users")
    public R<List<User>> listUsers() {
        rbacService.requireLogin();
        rbacService.requirePermission("user:list");
        return R.ok(rbacService.listUsers());
    }

    /**
     * 按标识查找用户。
     *
     * @param id 资源标识
     * @return 用户
     */
    @GET
    @Path("/users/{id}")
    public R<User> findUser(@PathParam("id") String id) {
        rbacService.requireLogin();
        return R.ok(rbacService.findUser(id));
    }

    /**
     * 创建用户。
     *
     * @param user 用户
     * @return 已创建的用户
     */
    @POST
    @Path("/users")
    @Consumes(MediaType.APPLICATION_JSON)
    public R<User> createUser(User user) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("user created", rbacService.createUser(user));
    }

    /**
     * 更新用户。
     *
     * @param id    资源标识
     * @param patch 待更新字段
     * @return 已更新的用户
     */
    @PUT
    @Path("/users/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public R<User> updateUser(@PathParam("id") String id, User patch) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("user updated", rbacService.updateUser(id, patch));
    }

    /**
     * 删除用户。
     *
     * @param id 资源标识
     * @return 删除结果
     */
    @DELETE
    @Path("/users/{id}")
    public R<Boolean> deleteUser(@PathParam("id") String id) {
        rbacService.requireLogin();
        rbacService.requireRoleAndPermission("admin", "user:delete");
        return R.ok("user deleted", rbacService.deleteUser(id));
    }

    // ============ Role CRUD ============

    /**
     * 为用户分配角色。
     *
     * @param id   资源标识
     * @param code 资源编码
     * @return 已更新的用户
     */
    @POST
    @Path("/users/{id}/roles/{code}")
    public R<User> assignRole(@PathParam("id") String id, @PathParam("code") String code) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("role assigned", rbacService.assignRole(id, code));
    }

    /**
     * 撤销用户角色。
     *
     * @param id   资源标识
     * @param code 资源编码
     * @return 已更新的用户
     */
    @DELETE
    @Path("/users/{id}/roles/{code}")
    public R<User> revokeRole(@PathParam("id") String id, @PathParam("code") String code) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("role revoked", rbacService.revokeRole(id, code));
    }

    /**
     * 列出全部角色。
     *
     * @return 角色列表
     */
    @GET
    @Path("/roles")
    public R<List<Role>> listRoles() {
        rbacService.requireLogin();
        return R.ok(rbacService.listRoles());
    }

    /**
     * 按编码查找角色。
     *
     * @param code 资源编码
     * @return 角色
     */
    @GET
    @Path("/roles/{code}")
    public R<Role> findRole(@PathParam("code") String code) {
        rbacService.requireLogin();
        return R.ok(rbacService.findRole(code));
    }

    /**
     * 创建角色。
     *
     * @param role 角色编码
     * @return 已创建的角色
     */
    @POST
    @Path("/roles")
    @Consumes(MediaType.APPLICATION_JSON)
    public R<Role> createRole(Role role) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("role created", rbacService.createRole(role));
    }

    /**
     * 更新角色。
     *
     * @param code  资源编码
     * @param patch 待更新字段
     * @return 已更新的角色
     */
    @PUT
    @Path("/roles/{code}")
    @Consumes(MediaType.APPLICATION_JSON)
    public R<Role> updateRole(@PathParam("code") String code, Role patch) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("role updated", rbacService.updateRole(code, patch));
    }

    /**
     * 删除角色。
     *
     * @param code 资源编码
     * @return 删除结果
     */
    @DELETE
    @Path("/roles/{code}")
    public R<Boolean> deleteRole(@PathParam("code") String code) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("role deleted", rbacService.deleteRole(code));
    }

    // ============ Permission CRUD ============

    /**
     * 为角色授予权限。
     *
     * @param code  资源编码
     * @param pcode 权限编码
     * @return 已更新的角色
     */
    @POST
    @Path("/roles/{code}/permissions/{pcode}")
    public R<Role> grantPermission(@PathParam("code") String code, @PathParam("pcode") String pcode) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("permission granted", rbacService.grantPermission(code, pcode));
    }

    /**
     * 撤销角色权限。
     *
     * @param code  资源编码
     * @param pcode 权限编码
     * @return 已更新的角色
     */
    @DELETE
    @Path("/roles/{code}/permissions/{pcode}")
    public R<Role> revokePermission(@PathParam("code") String code, @PathParam("pcode") String pcode) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("permission revoked", rbacService.revokePermission(code, pcode));
    }

    /**
     * 列出全部权限。
     *
     * @return 权限列表
     */
    @GET
    @Path("/permissions")
    public R<List<Permission>> listPermissions() {
        rbacService.requireLogin();
        return R.ok(rbacService.listPermissions());
    }

    /**
     * 创建权限。
     *
     * @param permission 权限编码
     * @return 已创建的权限
     */
    @POST
    @Path("/permissions")
    @Consumes(MediaType.APPLICATION_JSON)
    public R<Permission> createPermission(Permission permission) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("permission created", rbacService.createPermission(permission));
    }

    /**
     * 删除权限。
     *
     * @param code 资源编码
     * @return 删除结果
     */
    @DELETE
    @Path("/permissions/{code}")
    public R<Boolean> deletePermission(@PathParam("code") String code) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("permission deleted", rbacService.deletePermission(code));
    }

}

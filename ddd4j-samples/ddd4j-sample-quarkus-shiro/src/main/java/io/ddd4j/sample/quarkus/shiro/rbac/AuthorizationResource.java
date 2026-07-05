package io.ddd4j.sample.quarkus.shiro.rbac;

import io.ddd4j.core.api.R;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
@Consumes(MediaType.APPLICATION_JSON)
public class AuthorizationResource {

    @Inject
    RbacService rbacService;

    // ============ User CRUD ============

    @GET
    @Path("/users")
    public R<List<User>> listUsers() {
        rbacService.requireLogin();
        rbacService.requirePermission("user:list");
        return R.ok(rbacService.listUsers());
    }

    @GET
    @Path("/users/{id}")
    public R<User> findUser(@PathParam("id") String id) {
        rbacService.requireLogin();
        return R.ok(rbacService.findUser(id));
    }

    @POST
    @Path("/users")
    public R<User> createUser(User user) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("user created", rbacService.createUser(user));
    }

    @PUT
    @Path("/users/{id}")
    public R<User> updateUser(@PathParam("id") String id, User patch) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("user updated", rbacService.updateUser(id, patch));
    }

    @DELETE
    @Path("/users/{id}")
    public R<Boolean> deleteUser(@PathParam("id") String id) {
        rbacService.requireLogin();
        rbacService.requireRoleAndPermission("admin", "user:delete");
        return R.ok("user deleted", rbacService.deleteUser(id));
    }

    @POST
    @Path("/users/{id}/roles/{code}")
    public R<User> assignRole(@PathParam("id") String id, @PathParam("code") String code) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("role assigned", rbacService.assignRole(id, code));
    }

    @DELETE
    @Path("/users/{id}/roles/{code}")
    public R<User> revokeRole(@PathParam("id") String id, @PathParam("code") String code) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("role revoked", rbacService.revokeRole(id, code));
    }

    // ============ Role CRUD ============

    @GET
    @Path("/roles")
    public R<List<Role>> listRoles() {
        rbacService.requireLogin();
        return R.ok(rbacService.listRoles());
    }

    @GET
    @Path("/roles/{code}")
    public R<Role> findRole(@PathParam("code") String code) {
        rbacService.requireLogin();
        return R.ok(rbacService.findRole(code));
    }

    @POST
    @Path("/roles")
    public R<Role> createRole(Role role) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("role created", rbacService.createRole(role));
    }

    @PUT
    @Path("/roles/{code}")
    public R<Role> updateRole(@PathParam("code") String code, Role patch) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("role updated", rbacService.updateRole(code, patch));
    }

    @DELETE
    @Path("/roles/{code}")
    public R<Boolean> deleteRole(@PathParam("code") String code) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("role deleted", rbacService.deleteRole(code));
    }

    @POST
    @Path("/roles/{code}/permissions/{pcode}")
    public R<Role> grantPermission(@PathParam("code") String code, @PathParam("pcode") String pcode) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("permission granted", rbacService.grantPermission(code, pcode));
    }

    @DELETE
    @Path("/roles/{code}/permissions/{pcode}")
    public R<Role> revokePermission(@PathParam("code") String code, @PathParam("pcode") String pcode) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("permission revoked", rbacService.revokePermission(code, pcode));
    }

    // ============ Permission CRUD ============

    @GET
    @Path("/permissions")
    public R<List<Permission>> listPermissions() {
        rbacService.requireLogin();
        return R.ok(rbacService.listPermissions());
    }

    @POST
    @Path("/permissions")
    public R<Permission> createPermission(Permission permission) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("permission created", rbacService.createPermission(permission));
    }

    @DELETE
    @Path("/permissions/{code}")
    public R<Boolean> deletePermission(@PathParam("code") String code) {
        rbacService.requireLogin();
        rbacService.requireRole("admin");
        return R.ok("permission deleted", rbacService.deletePermission(code));
    }

    /**
     * 静态工具：抛出 401/403 响应。
     */
    static WebApplicationException unauthorized() {
        return new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                .entity(R.fail(401, "unauthenticated")).build());
    }

    static WebApplicationException forbidden(String reason) {
        return new WebApplicationException(Response.status(Response.Status.FORBIDDEN)
                .entity(R.fail(403, reason)).build());
    }

}
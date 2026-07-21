package io.ddd4j.sample.quarkus.satoken.rbac;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import io.ddd4j.core.api.R;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * RBAC 管理资源（JAX-RS）：用户 / 角色 / 权限 CRUD。
 *
 * <p>REST 端点：
 * <pre>
 *   GET    /admin/users                    列出全部用户           (@SaCheckPermission("user:list"))
 *   GET    /admin/users/{id}              按 ID 查询用户          (@SaCheckLogin + @SaCheckPermission)
 *   POST   /admin/users                    创建用户               (@SaCheckRole("admin"))
 *   PUT    /admin/users/{id}              更新用户               (@SaCheckRole("admin"))
 *   DELETE /admin/users/{id}              删除用户               (@SaCheckRole + @SaCheckPermission)
 *   POST   /admin/users/{id}/roles/{code}  绑定角色到用户          (@SaCheckRole("admin"))
 *   DELETE /admin/users/{id}/roles/{code}  解除用户角色           (@SaCheckRole("admin"))
 *
 *   GET    /admin/roles                    列出全部角色           (@SaCheckLogin)
 *   GET    /admin/roles/{code}            按编码查询角色         (@SaCheckLogin)
 *   POST   /admin/roles                    创建角色               (@SaCheckRole("admin"))
 *   PUT    /admin/roles/{code}            更新角色               (@SaCheckRole("admin"))
 *   DELETE /admin/roles/{code}            删除角色               (@SaCheckRole("admin"))
 *   POST   /admin/roles/{code}/permissions/{pcode}  授予角色权限 (@SaCheckRole("admin"))
 *   DELETE /admin/roles/{code}/permissions/{pcode}  解除角色权限 (@SaCheckRole("admin"))
 *
 *   GET    /admin/permissions              列出全部权限           (@SaCheckLogin)
 *   POST   /admin/permissions              创建权限               (@SaCheckRole("admin"))
 *   DELETE /admin/permissions/{code}      删除权限               (@SaCheckRole("admin"))
 * </pre>
 *
 * <p>本类仅做 HTTP 适配 + 注解鉴权，业务逻辑全部委托 {@link RbacService}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AuthorizationResource {

    @Inject
    RbacService rbacService;

    // ============ User CRUD ============

    @GET
    @Path("/users")
    @SaCheckLogin
    @SaCheckPermission("user:list")
    public R<List<User>> listUsers() {
        return R.ok(rbacService.listUsers());
    }

    @GET
    @Path("/users/{id}")
    @SaCheckLogin
    public R<User> findUser(@PathParam("id") String id) {
        return R.ok(rbacService.findUser(id));
    }

    @POST
    @Path("/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @SaCheckRole("admin")
    public R<User> createUser(User user) {
        return R.ok("user created", rbacService.createUser(user));
    }

    @PUT
    @Path("/users/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @SaCheckRole("admin")
    public R<User> updateUser(@PathParam("id") String id, User patch) {
        return R.ok("user updated", rbacService.updateUser(id, patch));
    }

    @DELETE
    @Path("/users/{id}")
    @SaCheckRole(value = "admin", mode = SaMode.AND)
    @SaCheckPermission("user:delete")
    public R<Boolean> deleteUser(@PathParam("id") String id) {
        return R.ok("user deleted", rbacService.deleteUser(id));
    }

    @POST
    @Path("/users/{id}/roles/{code}")
    @SaCheckRole("admin")
    public R<User> assignRole(@PathParam("id") String id, @PathParam("code") String code) {
        return R.ok("role assigned", rbacService.assignRole(id, code));
    }

    @DELETE
    @Path("/users/{id}/roles/{code}")
    @SaCheckRole("admin")
    public R<User> revokeRole(@PathParam("id") String id, @PathParam("code") String code) {
        return R.ok("role revoked", rbacService.revokeRole(id, code));
    }

    // ============ Role CRUD ============

    @GET
    @Path("/roles")
    @SaCheckLogin
    public R<List<Role>> listRoles() {
        return R.ok(rbacService.listRoles());
    }

    @GET
    @Path("/roles/{code}")
    @SaCheckLogin
    public R<Role> findRole(@PathParam("code") String code) {
        return R.ok(rbacService.findRole(code));
    }

    @POST
    @Path("/roles")
    @Consumes(MediaType.APPLICATION_JSON)
    @SaCheckRole("admin")
    public R<Role> createRole(Role role) {
        return R.ok("role created", rbacService.createRole(role));
    }

    @PUT
    @Path("/roles/{code}")
    @Consumes(MediaType.APPLICATION_JSON)
    @SaCheckRole("admin")
    public R<Role> updateRole(@PathParam("code") String code, Role patch) {
        return R.ok("role updated", rbacService.updateRole(code, patch));
    }

    @DELETE
    @Path("/roles/{code}")
    @SaCheckRole("admin")
    public R<Boolean> deleteRole(@PathParam("code") String code) {
        return R.ok("role deleted", rbacService.deleteRole(code));
    }

    @POST
    @Path("/roles/{code}/permissions/{pcode}")
    @SaCheckRole("admin")
    public R<Role> grantPermission(@PathParam("code") String code, @PathParam("pcode") String pcode) {
        return R.ok("permission granted", rbacService.grantPermission(code, pcode));
    }

    @DELETE
    @Path("/roles/{code}/permissions/{pcode}")
    @SaCheckRole("admin")
    public R<Role> revokePermission(@PathParam("code") String code, @PathParam("pcode") String pcode) {
        return R.ok("permission revoked", rbacService.revokePermission(code, pcode));
    }

    // ============ Permission CRUD ============

    @GET
    @Path("/permissions")
    @SaCheckLogin
    public R<List<Permission>> listPermissions() {
        return R.ok(rbacService.listPermissions());
    }

    @POST
    @Path("/permissions")
    @Consumes(MediaType.APPLICATION_JSON)
    @SaCheckRole("admin")
    public R<Permission> createPermission(Permission permission) {
        return R.ok("permission created", rbacService.createPermission(permission));
    }

    @DELETE
    @Path("/permissions/{code}")
    @SaCheckRole("admin")
    public R<Boolean> deletePermission(@PathParam("code") String code) {
        return R.ok("permission deleted", rbacService.deletePermission(code));
    }

}

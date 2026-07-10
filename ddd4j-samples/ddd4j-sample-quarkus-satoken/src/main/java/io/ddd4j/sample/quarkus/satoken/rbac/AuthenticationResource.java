package io.ddd4j.sample.quarkus.satoken.rbac;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import io.ddd4j.core.api.R;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * 鉴权业务资源（JAX-RS）：登录、登出、当前用户、权限 / 角色校验。
 *
 * <p>REST 端点：
 * <pre>
 *   POST   /auth/login            登录（用户名+密码），返回 token + principal
 *   POST   /auth/logout           登出              (@SaCheckLogin)
 *   GET    /auth/me               当前用户          (@SaCheckLogin)
 *   GET    /auth/status           登录状态
 *   GET    /auth/check/permission 运行时权限校验    (@SaCheckLogin)
 *   GET    /auth/check/role       运行时角色校验    (@SaCheckLogin)
 *
 *   GET    /auth/users            业务侧 - 用户列表  (@SaCheckLogin + @SaCheckPermission("user:list"))
 *   POST   /auth/orders/{id}/pay  业务侧 - 订单支付  (@SaCheckLogin + @SaCheckPermission("order:pay"))
 *   DELETE /auth/users/{id}       业务侧 - 删除用户  (@SaCheckLogin + @SaCheckRole("admin") + @SaCheckPermission("user:delete"))
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthenticationResource {

    @Inject
    RbacService rbacService;

    // ============ 登录 / 登出 / 当前用户 ============

    /**
     * 登录：用户名 + 密码，签发 Token 并返回 Principal。
     */
    @POST
    @Path("/login")
    public R<Map<String, Object>> login(LoginRequest request) {
        return R.ok("login success", rbacService.login(request.username(), request.password()));
    }

    /**
     * 登出。
     */
    @POST
    @Path("/logout")
    @SaCheckLogin
    public R<Map<String, Object>> logout() {
        rbacService.logout();
        return R.ok(Map.of("success", true));
    }

    /**
     * 当前登录用户信息。
     */
    @GET
    @Path("/me")
    @SaCheckLogin
    public R<Map<String, Object>> me() {
        return R.ok(rbacService.me());
    }

    /**
     * 登录状态。
     */
    @GET
    @Path("/status")
    public R<Map<String, Object>> status() {
        return R.ok(Map.of("login", rbacService.isLogin()));
    }

    // ============ 运行时权限 / 角色校验 ============

    /**
     * 运行时权限校验（手动调用 SubjectKit.hasPermission）。
     */
    @GET
    @Path("/check/permission")
    @SaCheckLogin
    public R<Map<String, Object>> checkPermission(@QueryParam("permission") String permission) {
        Map<String, Object> result = new HashMap<>();
        result.put("permission", permission);
        result.put("has", rbacService.hasPermission(permission));
        return R.ok(result);
    }

    /**
     * 运行时角色校验（手动调用 SubjectKit.hasRole）。
     */
    @GET
    @Path("/check/role")
    @SaCheckLogin
    public R<Map<String, Object>> checkRole(@QueryParam("role") String role) {
        Map<String, Object> result = new HashMap<>();
        result.put("role", role);
        result.put("has", rbacService.hasRole(role));
        return R.ok(result);
    }

    // ============ 业务侧鉴权示范 ============

    /**
     * 业务侧 - 查询用户列表：需要登录 + {@code user:list} 权限。
     */
    @GET
    @Path("/users")
    @SaCheckLogin
    @SaCheckPermission("user:list")
    public R<Object> businessListUsers() {
        return R.ok(Map.of("items", rbacService.listUsers()));
    }

    /**
     * 业务侧 - 支付订单：需要登录 + {@code order:pay} 权限。
     */
    @POST
    @Path("/orders/{id}/pay")
    @SaCheckLogin
    @SaCheckPermission("order:pay")
    public R<Map<String, Object>> businessPayOrder(@PathParam("id") String id) {
        return R.ok(Map.of("orderId", id, "paid", true));
    }

    /**
     * 业务侧 - 删除用户：需要登录 + {@code admin} 角色 + {@code user:delete} 权限（AND 模式）。
     */
    @DELETE
    @Path("/users/{id}")
    @SaCheckLogin
    @SaCheckRole(value = "admin", mode = SaMode.AND)
    @SaCheckPermission("user:delete")
    public R<Boolean> businessDeleteUser(@PathParam("id") String id) {
        return R.ok("user deleted", rbacService.deleteUser(id));
    }

    /**
     * 登录请求体。
     */
    public record LoginRequest(String username, String password) {
    }

}
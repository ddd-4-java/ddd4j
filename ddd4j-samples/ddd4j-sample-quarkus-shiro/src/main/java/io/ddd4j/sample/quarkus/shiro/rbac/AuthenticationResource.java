package io.ddd4j.sample.quarkus.shiro.rbac;

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
 *   POST   /auth/logout           登出              (login)
 *   GET    /auth/me               当前用户          (login)
 *   GET    /auth/status           登录状态
 *   GET    /auth/check/permission 运行时权限校验    (login)
 *   GET    /auth/check/role       运行时角色校验    (login)
 *
 *   GET    /auth/users            业务侧 - 用户列表  (login + user:list)
 *   POST   /auth/orders/{id}/pay  业务侧 - 订单支付  (login + order:pay)
 *   DELETE /auth/users/{id}       业务侧 - 删除用户  (login + admin role + user:delete)
 * </pre>
 *
 * <p>与 Sa-Token 示例完全对齐：相同 URL、相同请求/响应体、相同鉴权语义。
 * 唯一区别是 Shiro 版本通过 {@link RbacService} 显式调用鉴权方法
 * （Apache Shiro 在 Quarkus 中无开箱即用的方法级注解集成，
 * 需要 AOP / 拦截器 / 自定义 AuthzHandler 才能像 Sa-Token 注解一样工作）。
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
    public R<Map<String, Object>> logout() {
        rbacService.requireLogin();
        rbacService.logout();
        return R.ok(Map.of("success", true));
    }

    /**
     * 当前登录用户信息。
     */
    @GET
    @Path("/me")
    public R<Map<String, Object>> me() {
        rbacService.requireLogin();
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
    public R<Map<String, Object>> checkPermission(@QueryParam("permission") String permission) {
        rbacService.requireLogin();
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
    public R<Map<String, Object>> checkRole(@QueryParam("role") String role) {
        rbacService.requireLogin();
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
    public R<Object> businessListUsers() {
        rbacService.requireLogin();
        rbacService.requirePermission("user:list");
        return R.ok(Map.of("items", rbacService.listUsers()));
    }

    /**
     * 业务侧 - 支付订单：需要登录 + {@code order:pay} 权限。
     */
    @POST
    @Path("/orders/{id}/pay")
    public R<Map<String, Object>> businessPayOrder(@PathParam("id") String id) {
        rbacService.requireLogin();
        rbacService.requirePermission("order:pay");
        return R.ok(Map.of("orderId", id, "paid", true));
    }

    /**
     * 业务侧 - 删除用户：需要登录 + {@code admin} 角色 + {@code user:delete} 权限（AND 模式）。
     */
    @DELETE
    @Path("/users/{id}")
    public R<Boolean> businessDeleteUser(@PathParam("id") String id) {
        rbacService.requireLogin();
        rbacService.requireRoleAndPermission("admin", "user:delete");
        return R.ok("user deleted", rbacService.deleteUser(id));
    }

    /**
     * 登录请求体。
     */
    public record LoginRequest(String username, String password) {
    }

}
package io.ddd4j.sample.javalin.satoken.rbac.web;

import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.javalin.satoken.rbac.application.RbacService;
import io.javalin.apibuilder.EndpointGroup;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.javalin.apibuilder.ApiBuilder.*;

/**
 * 鉴权操作控制器：登录 / 登出 / 鉴权检查（重点演示 3 种鉴权控制 + 组合鉴权）。
 *
 * <p>本控制器演示 ddd4j SubjectKit 编程式鉴权（{@link SubjectKit#hasRole(String)} /
 * {@link SubjectKit#hasPermission(String)} / {@link SubjectKit#isLogin()}），结合
 * {@link RbacService} 实现业务侧鉴权判断，与 Spring Sa-Token 注解示例
 * （{@code @SaCheckLogin} / {@code @SaCheckRole} / {@code @SaCheckPermission}）行为完全一致。
 *
 * <h3>3 种鉴权控制</h3>
 * <ol>
 *   <li><b>登录鉴权</b>：{@code SubjectKit.isLogin()}，仅登录用户可访问</li>
 *   <li><b>角色鉴权</b>：{@code SubjectKit.hasRole("admin")}，指定角色可访问</li>
 *   <li><b>权限鉴权</b>：{@code SubjectKit.hasPermission("user:delete")}，拥有权限可访问</li>
 * </ol>
 *
 * <h3>组合鉴权</h3>
 * <p>{@code DELETE /auth/users/{id}} 需要 admin 角色 + {@code user:delete} 权限（AND 模式）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class AuthenticationController {

    private final RbacService rbacService;

    public AuthenticationController(RbacService rbacService) {
        this.rbacService = Objects.requireNonNull(rbacService, "rbacService must not be null");
    }

    /**
     * 注册 {@code /auth/*} 路由。
     */
    public EndpointGroup routes() {
        return () -> {
            // ============================ 1) 登录鉴权（账号密码登录）============================

            // POST /auth/login —— 登录
            post("/auth/login", ctx -> {
                LoginRequest req = ctx.bodyAsClass(LoginRequest.class);
                String token = rbacService.login(req.username(), req.password());
                if (Objects.isNull(token)) {
                    ctx.status(401).json(R.fail(401, "invalid credentials or user disabled"));
                    return;
                }
                Map<String, Object> data = new HashMap<>();
                data.put("token", token);
                data.put("tokenName", "ddd4j-token");
                ctx.json(R.ok(data));
            });

            // POST /auth/logout —— 登出
            post("/auth/logout", ctx -> {
                if (!SubjectKit.isLogin()) {
                    ctx.status(401).json(R.fail(401, "not login"));
                    return;
                }
                rbacService.logout();
                ctx.json(R.ok());
            });

            // GET /auth/me —— 当前登录用户
            get("/auth/me", ctx -> {
                AuthPrincipal principal = rbacService.me();
                if (Objects.isNull(principal)) {
                    ctx.json(R.ok(Map.of("authenticated", false)));
                    return;
                }
                Map<String, Object> data = new HashMap<>();
                data.put("authenticated", true);
                data.put("loginId", principal.getLoginId());
                data.put("userId", principal.getUserId());
                data.put("userCode", principal.getUserCode());
                data.put("roleCode", principal.getRoleCode());
                data.put("roles", principal.getRoles());
                ctx.json(R.ok(data));
            });

            // GET /auth/status —— 登录状态
            get("/auth/status", ctx -> ctx.json(R.ok(Map.of("login", rbacService.isLogin()))));

            // POST /auth/kickout —— 踢人下线
            post("/auth/kickout", ctx -> {
                KickoutRequest req = ctx.bodyAsClass(KickoutRequest.class);
                rbacService.kickout(req.userId());
                ctx.json(R.ok(Map.of("kicked", req.userId())));
            });

            // ============================ 2) 角色鉴权（SubjectKit.hasRole）============================

            // POST /auth/check/role —— 编程式鉴权：检查是否拥有某个角色（要求已登录）
            post("/auth/check/role", ctx -> {
                if (!SubjectKit.isLogin()) {
                    ctx.status(401).json(R.fail(401, "not login"));
                    return;
                }
                RoleCheckRequest req = ctx.bodyAsClass(RoleCheckRequest.class);
                boolean has = rbacService.hasRole(req.role());
                ctx.json(R.ok(Map.of("role", req.role(), "has", has)));
            });

            // GET /auth/admin —— 仅 admin 角色可访问（演示后端强制角色权限拦截）
            get("/auth/admin", ctx -> {
                if (!SubjectKit.isLogin()) {
                    ctx.status(401).json(R.fail(401, "not login"));
                    return;
                }
                if (!SubjectKit.hasRole("admin")) {
                    ctx.status(403).json(R.fail(403, "no role: admin"));
                    return;
                }
                ctx.json(R.ok(Map.of("message", "admin area accessed", "userId", SubjectKit.getUserId())));
            });

            // GET /auth/manager —— 仅 manager 角色可访问
            get("/auth/manager", ctx -> {
                if (!SubjectKit.isLogin()) {
                    ctx.status(401).json(R.fail(401, "not login"));
                    return;
                }
                if (!SubjectKit.hasRole("manager")) {
                    ctx.status(403).json(R.fail(403, "no role: manager"));
                    return;
                }
                ctx.json(R.ok(Map.of("message", "manager area accessed", "userId", SubjectKit.getUserId())));
            });

            // ============================ 3) 权限鉴权（SubjectKit.hasPermission）============================

            // POST /auth/check/permission —— 编程式鉴权：检查是否拥有某权限（要求已登录）
            post("/auth/check/permission", ctx -> {
                if (!SubjectKit.isLogin()) {
                    ctx.status(401).json(R.fail(401, "not login"));
                    return;
                }
                PermissionCheckRequest req = ctx.bodyAsClass(PermissionCheckRequest.class);
                boolean has = rbacService.hasPermission(req.permission());
                ctx.json(R.ok(Map.of("permission", req.permission(), "has", has)));
            });

            // GET /auth/users —— 需要 user:list 权限
            get("/auth/users", ctx -> {
                if (!SubjectKit.isLogin()) {
                    ctx.status(401).json(R.fail(401, "not login"));
                    return;
                }
                if (!SubjectKit.hasPermission("user:list")) {
                    ctx.status(403).json(R.fail(403, "no permission: user:list"));
                    return;
                }
                ctx.json(R.ok(Map.of("message", "user list accessed with permission", "userId", SubjectKit.getUserId())));
            });

            // POST /auth/orders/{id}/pay —— 业务接口鉴权：订单支付需要 order:pay 权限
            post("/auth/orders/{id}/pay", ctx -> {
                if (!SubjectKit.isLogin()) {
                    ctx.status(401).json(R.fail(401, "not login"));
                    return;
                }
                if (!SubjectKit.hasPermission("order:pay")) {
                    ctx.status(403).json(R.fail(403, "no permission: order:pay"));
                    return;
                }
                String id = ctx.pathParam("id");
                ctx.json(R.ok(Map.of("message", "order paid", "orderId", id, "userId", SubjectKit.getUserId())));
            });

            // ============================ 4) 组合鉴权（角色 + 权限，AND 模式）============================

            // DELETE /auth/users/{id} —— 组合鉴权：admin 角色 + user:delete 权限
            delete("/auth/users/{id}", ctx -> {
                if (!SubjectKit.isLogin()) {
                    ctx.status(401).json(R.fail(401, "not login"));
                    return;
                }
                if (!SubjectKit.hasRole("admin")) {
                    ctx.status(403).json(R.fail(403, "no role: admin"));
                    return;
                }
                if (!SubjectKit.hasPermission("user:delete")) {
                    ctx.status(403).json(R.fail(403, "no permission: user:delete"));
                    return;
                }
                String id = ctx.pathParam("id");
                ctx.json(R.ok(Map.of("success", true, "deleted", id, "userId", SubjectKit.getUserId())));
            });
        };
    }

    // ============================ 请求 DTO ============================

    public record LoginRequest(String username, String password) {
    }

    public record KickoutRequest(String userId) {
    }

    public record RoleCheckRequest(String role) {
    }

    public record PermissionCheckRequest(String permission) {
    }

}
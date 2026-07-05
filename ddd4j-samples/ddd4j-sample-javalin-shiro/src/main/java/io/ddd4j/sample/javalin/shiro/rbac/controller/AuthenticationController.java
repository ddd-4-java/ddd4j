package io.ddd4j.sample.javalin.shiro.rbac.controller;

import com.google.inject.Inject;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.javalin.shiro.rbac.domain.User;
import io.ddd4j.sample.javalin.shiro.rbac.service.RbacService;
import io.javalin.http.Context;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * 认证控制器：登录 / 登出 / 当前用户 / 权限/角色校验。
 *
 * <p>本控制器提供 RBAC 体系的认证入口：用户登录后，通过 {@link AuthPrincipal#getRoles()}
 * 暴露当前账号的角色列表，权限列表则通过 {@link RbacService#computeEffectivePermissions(User)}
 * 派生计算（用户直接权限 ∪ 角色持有权限）。
 *
 * <p>本控制器与 {@code ddd4j-sample-javalin-satoken} 的 AuthenticationController 业务代码<b>完全一致</b>，
 * 仅 HTTP 适配层（Javalin lambda）适配不同框架。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class AuthenticationController {

    private final RbacService rbacService;

    @Inject
    public AuthenticationController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    /**
     * POST /auth/login —— 登录：SubjectKit.login(AuthRequest)
     */
    public void login(Context ctx) {
        LoginRequest req = ctx.bodyAsClass(LoginRequest.class);
        // 1. 通过 RBAC 服务校验账号密码
        User user;
        try {
            user = rbacService.authenticate(req.loginId(), req.password());
        } catch (NoSuchElementException e) {
            ctx.status(401).json(R.fail(401, "user not found"));
            return;
        } catch (IllegalArgumentException e) {
            ctx.status(401).json(R.fail(401, "invalid credentials"));
            return;
        }

        // 2. 构造 AuthPrincipal（携带角色与权限信息）
        Set<String> effectivePerms = rbacService.computeEffectivePermissions(user);
        AuthPrincipal principal = new AuthPrincipal()
                .setLoginId(user.loginId())
                .setUserId(user.loginId())
                .setPerms(new LinkedHashSet<>(effectivePerms));

        // 3. 通过 SubjectKit 登录
        AuthRequest request = AuthRequest.of(user.loginId()).setTimeout(7200);
        request.setPrincipal(principal);
        String token = SubjectKit.login(request);

        Map<String, Object> result = Map.of(
                "token", token,
                "principal", principal,
                "roles", user.roles(),
                "permissions", effectivePerms);
        ctx.json(R.ok(result));
    }

    /**
     * POST /auth/logout —— 登出：SubjectKit.logout()
     */
    public void logout(Context ctx) {
        SubjectKit.logout();
        ctx.json(R.ok(Map.of("success", true)));
    }

    /**
     * GET /auth/me —— 当前用户：SubjectKit.getPrincipal() + 派生角色/权限
     */
    public void me(Context ctx) {
        AuthPrincipal principal = SubjectKit.getPrincipal();
        if (Objects.isNull(principal)) {
            ctx.json(R.ok(Map.of("authenticated", false)));
            return;
        }

        // 读取 RBAC 用户的角色与派生权限
        String loginId = String.valueOf(principal.getLoginId());
        Set<String> roles;
        Set<String> permissions;
        try {
            User user = rbacService.findUser(loginId);
            roles = user.roles();
            permissions = rbacService.computeEffectivePermissions(user);
        } catch (NoSuchElementException e) {
            // 已登录但 RBAC 中不存在（数据漂移场景），使用 Principal 中的信息兜底
            roles = Set.of();
            permissions = principal.getPerms();
        }

        Map<String, Object> result = Map.of(
                "authenticated", true,
                "loginId", principal.getLoginId(),
                "userId", principal.getUserId(),
                "roles", roles,
                "permissions", permissions);
        ctx.json(R.ok(result));
    }

    /**
     * GET /auth/check/permission?permission=xxx —— 权限校验
     */
    public void checkPermission(Context ctx) {
        String permission = ctx.queryParam("permission");
        boolean has = SubjectKit.hasPermission(permission);
        ctx.json(R.ok(Map.of("permission", permission, "has", has)));
    }

    /**
     * GET /auth/check/role?role=xxx —— 角色校验
     */
    public void checkRole(Context ctx) {
        String role = ctx.queryParam("role");
        boolean has = SubjectKit.hasRole(role);
        ctx.json(R.ok(Map.of("role", role, "has", has)));
    }

    /**
     * POST /auth/kickout —— 踢人下线：SubjectKit.kickout()
     */
    public void kickout(Context ctx) {
        String userId = ctx.formParam("userId");
        SubjectKit.kickout(userId);
        ctx.json(R.ok(Map.of("kicked", userId)));
    }

    /**
     * GET /auth/status —— 登录状态：SubjectKit.isLogin()
     */
    public void status(Context ctx) {
        ctx.json(R.ok(Map.of("login", SubjectKit.isLogin())));
    }

    // ============================ DTO ============================

    public record LoginRequest(String loginId, String password) {
    }

}
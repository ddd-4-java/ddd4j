package io.ddd4j.sample.auth.security.controller;

import io.ddd4j.core.subject.AuthPrincipal;
import io.ddd4j.core.subject.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 鉴权示例控制器：演示 SubjectKit 统一鉴权入口（Spring Security 底层）。
 *
 * <p>本控制器的代码与 sa-token / shiro 示例完全一致，
 * 证明切换底层鉴权框架时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    /**
     * 登录：SubjectKit.login(AuthRequest)
     * <p>底层委托 SecurityContextHolder.setContext()
     */
    @PostMapping("/login")
    public Map<String, Object> login(String userId) {
        AuthPrincipal principal = new AuthPrincipal()
                .setLoginId(userId)
                .setUserId(userId)
                .setRoleCode("user");

        AuthRequest request = AuthRequest.of(userId).setTimeout(7200);
        request.setPrincipal(principal);
        String token = SubjectKit.login(request);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("principal", principal);
        return result;
    }

    /**
     * 登出：SubjectKit.logout()
     * <p>底层委托 SecurityContextHolder.clearContext()
     */
    @PostMapping("/logout")
    public Map<String, Object> logout() {
        SubjectKit.logout();
        return Map.of("success", true);
    }

    /**
     * 当前用户：SubjectKit.getPrincipal()
     */
    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthPrincipal principal = SubjectKit.getPrincipal();
        if (java.util.Objects.isNull(principal)) {
            return Map.of("authenticated", false);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("authenticated", true);
        result.put("loginId", principal.getLoginId());
        result.put("userId", principal.getUserId());
        return result;
    }

    /**
     * 权限校验：SubjectKit.hasPermission()
     * <p>底层委托 SubjectDataProvider 数据源（与 sa-token/shiro 行为一致）
     */
    @GetMapping("/check/permission")
    public Map<String, Object> checkPermission(String permission) {
        boolean has = SubjectKit.hasPermission(permission);
        return Map.of("permission", permission, "has", has);
    }

    /**
     * 角色校验：SubjectKit.hasRole()
     */
    @GetMapping("/check/role")
    public Map<String, Object> checkRole(String role) {
        boolean has = SubjectKit.hasRole(role);
        return Map.of("role", role, "has", has);
    }

    /**
     * 登录状态：SubjectKit.isLogin()
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("login", SubjectKit.isLogin());
    }

}

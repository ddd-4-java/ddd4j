package io.ddd4j.sample.auth.satoken.controller;

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
 * 鉴权示例控制器：演示 SubjectKit 统一鉴权入口。
 *
 * <p>所有鉴权操作通过 {@link SubjectKit} 调用，底层切换为 Shiro/Security/sa-token 时业务代码零改动。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    /**
     * 登录：SubjectKit.login(AuthRequest)
     */
    @PostMapping("/login")
    public Map<String, Object> login(String userId) {
        // 构造认证主体
        AuthPrincipal principal = new AuthPrincipal()
                .setLoginId(userId)
                .setUserId(userId)
                .setRoleCode("user");

        // 统一登录入口
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
        if (principal == null) {
            return Map.of("authenticated", false);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("authenticated", true);
        result.put("loginId", principal.getLoginId());
        result.put("userId", principal.getUserId());
        result.put("roleCode", principal.getRoleCode());
        return result;
    }

    /**
     * 权限校验：SubjectKit.hasPermission()
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
     * 踢人下线：SubjectKit.kickout()
     */
    @PostMapping("/kickout")
    public Map<String, Object> kickout(String userId) {
        SubjectKit.kickout(userId);
        return Map.of("kicked", userId);
    }

    /**
     * 登录状态：SubjectKit.isLogin()
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("login", SubjectKit.isLogin());
    }

}

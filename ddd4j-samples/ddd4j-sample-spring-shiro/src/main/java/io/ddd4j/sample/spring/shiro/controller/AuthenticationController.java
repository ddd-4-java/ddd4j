package io.ddd4j.sample.spring.shiro.controller;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.spring.shiro.rbac.application.RbacService;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 鉴权操作控制器：登录 / 登出 / 鉴权检查（重点演示 3 种鉴权控制）。
 *
 * <p>本控制器演示 Apache Shiro 注解式鉴权（{@code @RequiresAuthentication} / {@code @RequiresRoles} /
 * {@code @RequiresPermissions}），结合 {@link RbacService} 通过 ddd4j SubjectKit 实现业务侧鉴权判断。
 *
 * <h3>3 种鉴权控制</h3>
 * <ol>
 *   <li><b>登录鉴权</b>：{@code @RequiresAuthentication}，仅登录用户可访问</li>
 *   <li><b>角色鉴权</b>：{@code @RequiresRoles("admin")}，指定角色可访问</li>
 *   <li><b>权限鉴权</b>：{@code @RequiresPermissions("user:delete")}，拥有权限可访问</li>
 * </ol>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private final RbacService rbacService;

    public AuthenticationController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    // ============================ 1) 登录鉴权（账号密码登录）============================

    /**
     * 登录。
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        String token = rbacService.login(req.username(), req.password());
        Map<String, Object> result = new HashMap<>();
        if (token == null) {
            result.put("success", false);
            result.put("message", "invalid credentials or user disabled");
            return result;
        }
        result.put("success", true);
        result.put("token", token);
        result.put("tokenName", "ddd4j-token");
        return result;
    }

    /**
     * 登出。
     */
    @PostMapping("/logout")
    public Map<String, Object> logout() {
        rbacService.logout();
        return Map.of("success", true);
    }

    /**
     * 当前登录用户。
     */
    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthPrincipal principal = rbacService.me();
        if (Objects.isNull(principal)) {
            return Map.of("authenticated", false);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("authenticated", true);
        result.put("loginId", principal.getLoginId());
        result.put("userId", principal.getUserId());
        result.put("userCode", principal.getUserCode());
        result.put("roleCode", principal.getRoleCode());
        result.put("roles", principal.getRoles());
        return result;
    }

    /**
     * 登录状态。
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("login", rbacService.isLogin());
    }

    /**
     * 踢人下线。
     */
    @PostMapping("/kickout")
    public Map<String, Object> kickout(@RequestBody KickoutRequest req) {
        rbacService.kickout(req.userId());
        return Map.of("kicked", req.userId());
    }

    // ============================ 2) 角色鉴权（@RequiresRoles）============================

    /**
     * 检查是否拥有某个角色（编程式鉴权）。
     */
    @RequiresAuthentication
    @PostMapping("/check/role")
    public Map<String, Object> checkRole(@RequestBody RoleCheckRequest req) {
        boolean has = rbacService.hasRole(req.role());
        return Map.of("role", req.role(), "has", has);
    }

    /**
     * 仅 admin 角色可访问（演示后端强制角色权限拦截）。
     */
    @RequiresRoles("admin")
    @GetMapping("/admin")
    public Map<String, Object> adminOnly() {
        return Map.of("message", "admin area accessed", "userId", SubjectKit.getUserId());
    }

    /**
     * 仅 manager 角色可访问。
     */
    @RequiresRoles("manager")
    @GetMapping("/manager")
    public Map<String, Object> managerOnly() {
        return Map.of("message", "manager area accessed", "userId", SubjectKit.getUserId());
    }

    // ============================ 3) 权限鉴权（@RequiresPermissions）============================

    /**
     * 检查是否拥有某权限（编程式鉴权）。
     */
    @RequiresAuthentication
    @PostMapping("/check/permission")
    public Map<String, Object> checkPermission(@RequestBody PermissionCheckRequest req) {
        boolean has = rbacService.hasPermission(req.permission());
        return Map.of("permission", req.permission(), "has", has);
    }

    /**
     * 需要 {@code user:list} 权限。
     */
    @RequiresPermissions("user:list")
    @GetMapping("/users")
    public Map<String, Object> listUsersByPermission() {
        return Map.of("message", "user list accessed with permission", "userId", SubjectKit.getUserId());
    }

    /**
     * 业务接口权限鉴权：订单支付需要 {@code order:pay} 权限。
     */
    @RequiresAuthentication
    @RequiresPermissions("order:pay")
    @PostMapping("/orders/{id}/pay")
    public Map<String, Object> payOrder(@PathVariable("id") String id) {
        return Map.of("message", "order paid", "orderId", id, "userId", SubjectKit.getUserId());
    }

    // ============================ 4) 组合鉴权（角色 + 权限）============================

    /**
     * 组合鉴权：需要 admin 角色 + {@code user:delete} 权限（AND 模式）。
     */
    @RequiresRoles(value = "admin", logical = Logical.AND)
    @RequiresPermissions("user:delete")
    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUserWithCombinedCheck(@PathVariable("id") String id) {
        return Map.of("success", true, "deleted", id, "userId", SubjectKit.getUserId());
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
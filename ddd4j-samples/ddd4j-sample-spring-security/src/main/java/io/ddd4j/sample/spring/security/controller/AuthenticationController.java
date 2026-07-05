package io.ddd4j.sample.spring.security.controller;

import io.ddd4j.sample.spring.security.rbac.RbacService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 鉴权操作控制器（Spring Security）：登录、登出、当前用户、权限/角色校验、业务鉴权。
 *
 * <p>演示四类鉴权：
 * <ul>
 *   <li>登录鉴权：{@code isAuthenticated()} / 任意访问</li>
 *   <li>角色鉴权：{@code hasRole('admin')}</li>
 *   <li>权限鉴权：{@code hasAuthority('user:list')}</li>
 *   <li>组合鉴权：{@code hasRole('admin') and hasAuthority('user:delete')}</li>
 * </ul>
 *
 * <p>业务接口鉴权示范：
 * <ul>
 *   <li>{@code GET /auth/users}              需要 {@code user:list} 权限</li>
 *   <li>{@code POST /auth/orders/{id}/pay}  需要 {@code order:pay} 权限</li>
 *   <li>{@code DELETE /auth/users/{id}}     需要 {@code admin} 角色 + {@code user:delete} 权限</li>
 * </ul>
 *
 * <p>HTTP 协议适配层：业务逻辑全部委托 {@link RbacService}。
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

    // ============== 登录 / 登出 / 当前用户 ==============

    /**
     * 登录：SubjectKit.login(AuthRequest)
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String username, @RequestParam String password) {
        return rbacService.login(username, password);
    }

    /**
     * 登出：SubjectKit.logout()
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> logout() {
        return rbacService.logout();
    }

    /**
     * 当前用户：SubjectKit.getPrincipal()
     */
    @GetMapping("/me")
    public Map<String, Object> me() {
        return rbacService.me();
    }

    /**
     * 登录状态：SubjectKit.isLogin()
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return rbacService.status();
    }

    // ============== 角色 / 权限校验（直接走 ddd4j SubjectKit） ==============

    /**
     * 权限校验：SubjectKit.hasPermission()
     */
    @GetMapping("/check/permission")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> checkPermission(@RequestParam String permission) {
        return rbacService.checkPermission(permission);
    }

    /**
     * 角色校验：SubjectKit.hasRole()
     */
    @GetMapping("/check/role")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> checkRole(@RequestParam String role) {
        return rbacService.checkRole(role);
    }

    // ============== 业务接口鉴权示范 ==============

    /**
     * 业务鉴权示例 1：权限鉴权。
     * <p>需要 {@code order:pay} 权限——演示业务接口的细粒度权限控制。
     */
    @PostMapping("/orders/{id}/pay")
    @PreAuthorize("hasAuthority('order:pay')")
    public Map<String, Object> payOrder(@PathVariable String id) {
        // 实际项目：rbacService.payOrder(id)
        return Map.of("orderId", id, "status", "paid");
    }

}
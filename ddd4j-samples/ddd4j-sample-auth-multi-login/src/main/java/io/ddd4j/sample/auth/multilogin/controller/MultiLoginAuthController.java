package io.ddd4j.sample.auth.multilogin.controller;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.auth.multilogin.audit.LoginAuditListener;
import io.ddd4j.sample.auth.multilogin.controller.dto.PhoneLoginRequest;
import io.ddd4j.sample.auth.multilogin.controller.dto.ThirdPartyLoginRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 多登录认证控制器。
 *
 * <p>提供手机号登录、第三方登录、查询当前用户、登出等 RESTful 接口。
 * 认证逻辑统一通过 {@link io.ddd4j.core.util.SubjectKit} 委托给底层鉴权 SPI。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
@RequestMapping("/auth")
public class MultiLoginAuthController {

    private final LoginAuditListener auditListener;

    public MultiLoginAuthController(LoginAuditListener auditListener) {
        this.auditListener = auditListener;
    }

    /**
     * 手机号验证码登录。
     *
     * @param command 手机号登录请求参数
     * @return 登录结果（Token 与认证主体信息）
     */
    @PostMapping("/login/phone")
    public Map<String, Object> phoneLogin(@RequestBody PhoneLoginRequest command) {
        AuthPrincipal principal = new AuthPrincipal()
                .setLoginId(command.phone())
                .setUserId(command.phone())
                .setUserType("mobile")
                .setDeviceType(command.deviceType());
        principal.getPerms().add("profile:read");

        AuthRequest request = AuthRequest.of(command.phone())
                .setPrincipal(principal)
                .setRealm("mobile")
                .setDeviceType(command.deviceType())
                .setTimeout(7200)
                .extra("loginScene", "phone")
                .extra("verificationCode", command.code());
        return loginResponse(SubjectKit.login(request), principal);
    }

    /**
     * 第三方平台登录（微信、QQ 等）。
     *
     * @param command 第三方登录请求参数
     * @return 登录结果（Token 与认证主体信息）
     */
    @PostMapping("/login/third-party")
    public Map<String, Object> thirdPartyLogin(@RequestBody ThirdPartyLoginRequest command) {
        String loginId = command.provider() + ":" + command.openId();
        AuthPrincipal principal = new AuthPrincipal()
                .setLoginId(loginId)
                .setUserId(Objects.isNull(command.unionId()) || !org.springframework.util.StringUtils.hasText(command.unionId()) ? loginId : command.unionId())
                .setOpenid(command.openId())
                .setUnionId(command.unionId())
                .setUserType("third-party");
        principal.getPerms().add("profile:read");

        AuthRequest request = AuthRequest.of(loginId)
                .setPrincipal(principal)
                .setRealm("third-party")
                .setTimeout(7200)
                .extra("loginScene", "third-party")
                .extra("provider", command.provider())
                .extra("openId", command.openId());
        return loginResponse(SubjectKit.login(request), principal);
    }

    /**
     * 获取当前登录用户信息。
     *
     * @return 当前用户信息（未登录时返回 authenticated=false）
     */
    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthPrincipal principal = SubjectKit.getPrincipal();
        if (Objects.isNull(principal)) {
            return Map.of("authenticated", false);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", true);
        result.put("loginId", principal.getLoginId());
        result.put("userId", principal.getUserId());
        result.put("userType", principal.getUserType());
        result.put("profile", principal.getProfile());
        return result;
    }

    /**
     * 登出当前用户。
     *
     * @return 操作结果
     */
    @PostMapping("/logout")
    public Map<String, Object> logout() {
        SubjectKit.logout();
        return Map.of("success", true);
    }

    /**
     * 获取最近的登录审计事件列表。
     *
     * @return 审计事件列表
     */
    @GetMapping("/events")
    public Map<String, Object> events() {
        return Map.of("events", auditListener.recentEvents());
    }

    /**
     * 处理登录失败的异常。
     *
     * @param exception 参数校验异常
     * @return 失败响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleLoginFailure(IllegalArgumentException exception) {
        return Map.of("success", false, "message", exception.getMessage());
    }

    /**
     * 构建登录成功响应。
     *
     * @param token     Bearer Token
     * @param principal 认证主体
     * @return 响应 Map
     */
    private Map<String, Object> loginResponse(String token, AuthPrincipal principal) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tokenType", "Bearer");
        result.put("token", token);
        result.put("principal", principal);
        return result;
    }
}

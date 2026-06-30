package io.ddd4j.sample.auth.multilogin.controller;

import io.ddd4j.core.subject.AuthPrincipal;
import io.ddd4j.core.subject.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.sample.auth.multilogin.audit.LoginAuditListener;
import io.ddd4j.sample.auth.multilogin.controller.dto.PhoneLoginRequest;
import io.ddd4j.sample.auth.multilogin.controller.dto.ThirdPartyLoginRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class MultiLoginAuthController {

    private final LoginAuditListener auditListener;

    public MultiLoginAuthController(LoginAuditListener auditListener) {
        this.auditListener = auditListener;
    }

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

    @PostMapping("/login/third-party")
    public Map<String, Object> thirdPartyLogin(@RequestBody ThirdPartyLoginRequest command) {
        String loginId = command.provider() + ":" + command.openId();
        AuthPrincipal principal = new AuthPrincipal()
                .setLoginId(loginId)
                .setUserId(command.unionId() == null || command.unionId().isBlank() ? loginId : command.unionId())
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

    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthPrincipal principal = SubjectKit.getPrincipal();
        if (principal == null) {
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

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        SubjectKit.logout();
        return Map.of("success", true);
    }

    @GetMapping("/events")
    public Map<String, Object> events() {
        return Map.of("events", auditListener.recentEvents());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleLoginFailure(IllegalArgumentException exception) {
        return Map.of("success", false, "message", exception.getMessage());
    }

    private Map<String, Object> loginResponse(String token, AuthPrincipal principal) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tokenType", "Bearer");
        result.put("token", token);
        result.put("principal", principal);
        return result;
    }
}

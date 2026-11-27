package io.ddd4j.sample.micronaut.shiro;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import org.apache.shiro.SecurityUtils;

import java.util.Map;

/**
 * 原生登录、主体查询与退出入口；固定账号只用于样例。
 */
@Controller("/auth")
public final class AuthenticationController {
    /**
     * 处理当前示例应用登录状态的原生认证组件。
     */
    private final NativeAuth auth;

    /**
     * @param auth 原生会话装配
     */
    public AuthenticationController(NativeAuth auth) {
        this.auth = auth;
    }

    /**
     * @param request 演示凭证
     * @return 会话令牌
     */
    @Post("/login")
    public Map<String, String> login(@Body Map<String, String> request) {
        return Map.of("token", auth.login(request.get("username"), request.get("password")));
    }

    /**
     * @return 已认证账号
     */
    @Get("/me")
    public Map<String, String> me() {
        return Map.of("principal", SecurityUtils.getSubject().getPrincipal().toString());
    }

    /**
     * @return 退出结果
     */
    @Post("/logout")
    public Map<String, Boolean> logout() {
        SecurityUtils.getSubject().logout();
        return Map.of("loggedOut", true);
    }
}

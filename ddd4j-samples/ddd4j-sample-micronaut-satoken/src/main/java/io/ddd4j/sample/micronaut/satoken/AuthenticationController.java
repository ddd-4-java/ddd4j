package io.ddd4j.sample.micronaut.satoken;

import cn.dev33.satoken.stp.StpUtil;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * 原生登录、主体查询与退出入口；固定账号只用于样例。
 */
@Controller("/auth")
public final class AuthenticationController {
    /** \u5904\u7406\u5f53\u524d\u793a\u4f8b\u5e94\u7528\u767b\u5f55\u72b6\u6001\u7684\u539f\u751f\u8ba4\u8bc1\u7ec4\u4ef6\u3002 */
    private final NativeAuth auth;

    /**
     * 初始化 <code>AuthenticationController</code> 使用的 <code>auth</code>。
     *
     * @param auth \u539f\u751f\u4f1a\u8bdd\u88c5\u914d
     */
    public AuthenticationController(NativeAuth auth) {
        this.auth = auth;
    }

    /**
     * 执行 <code>login</code>，调用 <code>Map.of</code>、<code>auth.login</code>、<code>request.get</code>。
     *
     * @param request \u6f14\u793a\u51ed\u8bc1
     * @return \u4f1a\u8bdd\u4ee4\u724c
     */
    @Post("/login")
    public Map<String, String> login(@Body Map<String, String> request) {
        return Map.of("token", auth.login(request.get("username"), request.get("password")));
    }

    /**
     * 执行 <code>me</code>，调用 <code>Map.of</code>、<code>StpUtil.getLoginIdAsString</code>。
     *
     * @return \u5df2\u8ba4\u8bc1\u8d26\u53f7
     */
    @Get("/me")
    public Map<String, String> me() {
        return Map.of("principal", StpUtil.getLoginIdAsString());
    }

    /**
     * 执行 <code>logout</code>，调用 <code>StpUtil.logout</code>、<code>Map.of</code>。
     *
     * @return \u9000\u51fa\u7ed3\u679c
     */
    @Post("/logout")
    public Map<String, Boolean> logout() {
        StpUtil.logout();
        return Map.of("loggedOut", true);
    }
}

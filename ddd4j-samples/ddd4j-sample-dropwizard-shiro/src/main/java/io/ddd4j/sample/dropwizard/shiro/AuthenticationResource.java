package io.ddd4j.sample.dropwizard.shiro;

import io.ddd4j.core.util.SubjectKit;
import jakarta.ws.rs.*;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 专项示例的原生会话入口。
 */
@Path("/")
@Produces("application/json")
@Consumes("application/json")
@RequiredArgsConstructor
public class AuthenticationResource {
    /**
     * 处理当前示例应用登录状态的原生认证组件。
     */
    private final Supplier<NativeAuth> auth;

    /**
     * 报告鉴权示例的 HTTP 服务可访问。
     *
     * @return 带有 UP 状态的健康检查响应
     */
    @GET
    @Path("health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    /**
     * 校验账号凭据并签发当前认证引擎的会话令牌。
     *
     * @param credentials 包含用户名和密码的请求数据
     * @return 包含会话令牌的响应
     */
    @POST
    @Path("auth/login")
    public Map<String, String> login(Map<String, String> credentials) {
        return Map.of("token", auth.get().login(credentials.get("username"), credentials.get("password")));
    }

    /**
     * 注销当前请求所绑定的认证主体。
     *
     * @return 带有成功标记的注销响应
     */
    @POST
    @Path("auth/logout")
    public Map<String, Boolean> logout() {
        SubjectKit.getSubject().logout();
        return Map.of("success", true);
    }
}

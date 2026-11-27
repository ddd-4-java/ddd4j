package io.ddd4j.sample.helidon.satoken;

import io.ddd4j.core.api.R;
import io.ddd4j.core.util.SubjectKit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.Data;

import java.util.Map;

/**
 * 原生会话、主体和权限入口；演示凭证不是生产账号体系。
 */
@Path("/auth")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class AuthenticationResource {
    /**
     * 处理当前示例应用登录状态的原生认证组件。
     */
    @Inject
    NativeAuth auth;

    /**
     * @param login 演示账号凭证
     * @return 原生会话令牌
     */
    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public R<Map<String, String>> login(Login login) {
        return R.ok(Map.of("token", auth.login(login.getUsername(), login.getPassword())));
    }

    /**
     * @return 已认证主体
     */
    @GET
    @Path("/me")
    public R<String> me() {
        return R.ok(String.valueOf(SubjectKit.getSubject().getPrincipal().getLoginId()));
    }

    /**
     * @param permission 所需权限
     * @return 授权资源
     */
    @GET
    @Path("/resource")
    public R<String> resource(@QueryParam("permission") String permission) {
        if (!SubjectKit.getSubject().isPermitted(permission)) {
            throw new ForbiddenException("permission denied");
        }
        return R.ok("orders");
    }

    /**
     * @return 退出确认
     */
    @POST
    @Path("/logout")
    public R<String> logout() {
        SubjectKit.getSubject().logout();
        return R.ok("logged-out");
    }

    /**
     * 登录输入。
     */
    @Data
    public static class Login {
        /**
         * 当前身份凭据使用的用户名。
         */
        private String username;
        /**
         * 当前登录或账号创建请求提供的口令。
         */
        private String password;
    }
}

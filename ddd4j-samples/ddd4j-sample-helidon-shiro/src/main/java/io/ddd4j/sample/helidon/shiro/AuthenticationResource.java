package io.ddd4j.sample.helidon.shiro;

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
    /** \u5904\u7406\u5f53\u524d\u793a\u4f8b\u5e94\u7528\u767b\u5f55\u72b6\u6001\u7684\u539f\u751f\u8ba4\u8bc1\u7ec4\u4ef6\u3002 */
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
        /** \u5f53\u524d\u8eab\u4efd\u51ed\u636e\u4f7f\u7528\u7684\u7528\u6237\u540d\u3002 */
        private String username;
        /** \u5f53\u524d\u767b\u5f55\u6216\u8d26\u53f7\u521b\u5efa\u8bf7\u6c42\u63d0\u4f9b\u7684\u53e3\u4ee4\u3002 */
        private String password;
    }
}

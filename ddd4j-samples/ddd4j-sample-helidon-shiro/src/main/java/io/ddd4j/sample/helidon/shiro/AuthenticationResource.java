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
     * 执行 <code>login</code>，调用 <code>R.ok</code>、<code>Map.of</code>、<code>auth.login</code>。
     *
     * @param login \u6f14\u793a\u8d26\u53f7\u51ed\u8bc1
     * @return \u539f\u751f\u4f1a\u8bdd\u4ee4\u724c
     */
    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public R<Map<String, String>> login(Login login) {
        return R.ok(Map.of("token", auth.login(login.getUsername(), login.getPassword())));
    }

    /**
     * 执行 <code>me</code>，调用 <code>R.ok</code>、<code>SubjectKit.getSubject</code>。
     *
     * @return \u5df2\u8ba4\u8bc1\u4e3b\u4f53
     */
    @GET
    @Path("/me")
    public R<String> me() {
        return R.ok(String.valueOf(SubjectKit.getSubject().getPrincipal().getLoginId()));
    }

    /**
     * 执行 <code>resource</code>，调用 <code>SubjectKit.getSubject</code>、<code>R.ok</code>。
     *
     * @param permission \u6240\u9700\u6743\u9650
     * @return \u6388\u6743\u8d44\u6e90
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
     * 执行 <code>logout</code>，调用 <code>SubjectKit.getSubject</code>、<code>R.ok</code>。
     *
     * @return \u9000\u51fa\u786e\u8ba4
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

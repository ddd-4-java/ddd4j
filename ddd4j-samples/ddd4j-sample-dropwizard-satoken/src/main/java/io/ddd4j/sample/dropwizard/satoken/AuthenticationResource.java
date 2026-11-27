package io.ddd4j.sample.dropwizard.satoken;

import com.google.common.collect.ImmutableMap;
import io.ddd4j.core.util.SubjectKit;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

/** 专项示例的原生会话入口。 */
@Path("/")
@Produces("application/json")
@Consumes("application/json")
@RequiredArgsConstructor
public class AuthenticationResource {
    /** \u5904\u7406\u5f53\u524d\u793a\u4f8b\u5e94\u7528\u767b\u5f55\u72b6\u6001\u7684\u539f\u751f\u8ba4\u8bc1\u7ec4\u4ef6\u3002 */
    private final Supplier<NativeAuth> auth;

    /**
     * \u62a5\u544a\u9274\u6743\u793a\u4f8b\u7684 HTTP \u670d\u52a1\u53ef\u8bbf\u95ee\u3002
     * @return \u5e26\u6709 UP \u72b6\u6001\u7684\u5065\u5eb7\u68c0\u67e5\u54cd\u5e94
     */
    @GET
    @Path("health")
    public Map<String, String> health() {
        return ImmutableMap.of("status", "UP");
    }

    /**
     * \u6821\u9a8c\u8d26\u53f7\u51ed\u636e\u5e76\u7b7e\u53d1\u5f53\u524d\u8ba4\u8bc1\u5f15\u64ce\u7684\u4f1a\u8bdd\u4ee4\u724c\u3002
     * @param credentials \u5305\u542b\u7528\u6237\u540d\u548c\u5bc6\u7801\u7684\u8bf7\u6c42\u6570\u636e
     * @return \u5305\u542b\u4f1a\u8bdd\u4ee4\u724c\u7684\u54cd\u5e94
     */
    @POST
    @Path("auth/login")
    public Map<String, String> login(Map<String, String> credentials) {
        return ImmutableMap.of("token", auth.get().login(credentials.get("username"), credentials.get("password")));
    }

    /**
     * \u6ce8\u9500\u5f53\u524d\u8bf7\u6c42\u6240\u7ed1\u5b9a\u7684\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @return \u5e26\u6709\u6210\u529f\u6807\u8bb0\u7684\u6ce8\u9500\u54cd\u5e94
     */
    @POST
    @Path("auth/logout")
    public Map<String, Boolean> logout() {
        SubjectKit.getSubject().logout();
        return ImmutableMap.of("success", true);
    }
}

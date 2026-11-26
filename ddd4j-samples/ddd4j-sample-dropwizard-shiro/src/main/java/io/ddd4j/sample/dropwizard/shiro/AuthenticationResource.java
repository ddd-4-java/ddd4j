package io.ddd4j.sample.dropwizard.shiro;

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
    private final Supplier<NativeAuth> auth;

    @GET
    @Path("health")
    public Map<String, String> health() {
        return ImmutableMap.of("status", "UP");
    }

    @POST
    @Path("auth/login")
    public Map<String, String> login(Map<String, String> credentials) {
        return ImmutableMap.of("token", auth.get().login(credentials.get("username"), credentials.get("password")));
    }

    @POST
    @Path("auth/logout")
    public Map<String, Boolean> logout() {
        SubjectKit.getSubject().logout();
        return ImmutableMap.of("success", true);
    }
}

package io.ddd4j.sample.dropwizard.satoken;

import io.ddd4j.core.util.SubjectKit;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
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
        return Map.of("status", "UP");
    }

    @POST
    @Path("auth/login")
    public Map<String, String> login(Map<String, String> credentials) {
        return Map.of("token", auth.get().login(credentials.get("username"), credentials.get("password")));
    }

    @POST
    @Path("auth/logout")
    public Map<String, Boolean> logout() {
        SubjectKit.getSubject().logout();
        return Map.of("success", true);
    }
}

package io.ddd4j.sample.dropwizard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.Data;

/**
 * 原生 Dropwizard Bearer 示例令牌入口。
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
public final class AuthenticationResource {
    @POST
    @Path("/tokens/{userId}")
    public R<TokenResponse> issueToken(@PathParam("userId") String userId) {
        return R.ok(new TokenResponse(SubjectKit.getSubject().login(AuthRequest.of(userId))));
    }

    @Data
    public static class TokenResponse {

        private static final long serialVersionUID = 0L;

        private String token;

        /**
         * @param token 登录令牌
         */
        @JsonCreator()
        public TokenResponse(@JsonProperty("token") String token) {
            this.token = token;
        }

        /**
         * @return 登录令牌
         */
        @JsonProperty("token")
        public String token() {
            return token;
        }

    }
}

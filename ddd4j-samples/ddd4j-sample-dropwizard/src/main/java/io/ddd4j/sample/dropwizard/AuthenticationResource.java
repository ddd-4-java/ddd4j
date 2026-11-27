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
    /**
     * \u4e3a\u6307\u5b9a\u7528\u6237\u7b7e\u53d1\u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u7684\u4f1a\u8bdd\u4ee4\u724c\u3002
     * @param userId \u767b\u5f55\u7528\u6237\u6807\u8bc6
     * @return \u5305\u542b\u4f1a\u8bdd\u4ee4\u724c\u7684\u54cd\u5e94
     */
    @POST
    @Path("/tokens/{userId}")
    public R<TokenResponse> issueToken(@PathParam("userId") String userId) {
        return R.ok(new TokenResponse(SubjectKit.getSubject().login(AuthRequest.of(userId))));
    }

    /**
     * \u5c01\u88c5\u7b7e\u53d1\u7ed9\u793a\u4f8b\u5ba2\u6237\u7aef\u7684\u4f1a\u8bdd\u4ee4\u724c\u3002
     */
    @Data
    public static class TokenResponse {

        /**
         * Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002
         */
        private static final long serialVersionUID = 0L;

        /**
         * \u5f53\u524d\u793a\u4f8b\u7b7e\u53d1\u6216\u8fd4\u56de\u5ba2\u6237\u7aef\u7684\u4f1a\u8bdd\u4ee4\u724c\u3002
         */
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

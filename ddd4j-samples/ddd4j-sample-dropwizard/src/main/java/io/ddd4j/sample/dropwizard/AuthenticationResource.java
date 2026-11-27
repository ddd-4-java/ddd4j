package io.ddd4j.sample.dropwizard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.Data;

/**
 * 原生 Dropwizard Bearer 示例令牌入口。
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
public final class AuthenticationResource {
    /**
     * 为指定用户签发商品交易示例的会话令牌。
     *
     * @param userId 登录用户标识
     * @return 包含会话令牌的响应
     */
    @POST
    @Path("/tokens/{userId}")
    public R<TokenResponse> issueToken(@PathParam("userId") String userId) {
        return R.ok(new TokenResponse(SubjectKit.getSubject().login(AuthRequest.of(userId))));
    }

    /**
     * 封装签发给示例客户端的会话令牌。
     */
    @Data
    public static class TokenResponse {

        /**
         * Java 序列化版本标识。
         */
        private static final long serialVersionUID = 0L;

        /**
         * 当前示例签发或返回客户端的会话令牌。
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

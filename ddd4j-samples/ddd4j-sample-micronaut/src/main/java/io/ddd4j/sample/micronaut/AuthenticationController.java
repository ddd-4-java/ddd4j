package io.ddd4j.sample.micronaut;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.core.api.R;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.subject.SubjectProvider;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.Objects;

/**
 * 本地 Bearer 获取入口，演示 HTTP Bearer 到 Subject SPI 的桥接。
 */
@Controller("/api/auth")
public class AuthenticationController {

    private final SubjectProvider subjectProvider;

    public AuthenticationController(SubjectProvider subjectProvider) {
        this.subjectProvider = Objects.requireNonNull(subjectProvider, "subjectProvider must not be null");
    }

    @Post("/tokens/{userId}")
    public R<TokenResponse> issueToken(String userId) {
        String token = subjectProvider.getSubject().login(AuthRequest.of(userId));
        return R.ok(new TokenResponse(token));
    }

    public final static class TokenResponse {

        private static final long serialVersionUID = 0L;

        private final String token;

        @JsonCreator()
        public TokenResponse(@JsonProperty("token") String token) {
            this.token = token;
        }

        @JsonProperty("token")
        public String token() {
            return token;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            TokenResponse other = (TokenResponse) obj;
            return Objects.equals(this.token, other.token);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(token);
            return result;
        }

        @Override
        public String toString() {
            return "TokenResponse[token=" + token + "]";
        }
    }
}

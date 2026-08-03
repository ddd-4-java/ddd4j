package io.ddd4j.sample.micronaut;

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

    public record TokenResponse(String token) {
    }
}

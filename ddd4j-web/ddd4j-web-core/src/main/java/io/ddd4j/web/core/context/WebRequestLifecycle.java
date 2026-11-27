package io.ddd4j.web.core.context;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator.Authentication;

import java.util.Objects;
import java.util.Optional;

import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;

/**
 * 在具体 Web 框架之外统一执行访问策略与 Subject 认证。
 */
public final class WebRequestLifecycle {

    /** \u89e3\u6790\u5e76\u9a8c\u8bc1\u5f53\u524d\u8bf7\u6c42\u4e3b\u4f53\u7684\u8ba4\u8bc1\u7ec4\u4ef6\u3002 */
    private final BearerSubjectAuthenticator authenticator;
    /** \u9009\u62e9\u4e0d\u540c\u8bf7\u6c42\u8def\u5f84\u9274\u6743\u6a21\u5f0f\u7684\u8bbf\u95ee\u7b56\u7565\u3002 */
    private final WebAccessPolicy accessPolicy;

    /**
     * \u521b\u5efa\u8bf7\u6c42\u8ba4\u8bc1\u751f\u547d\u5468\u671f\u3002
     * @param authenticator Bearer \u4e3b\u4f53\u8ba4\u8bc1\u5668
     * @param accessPolicy \u8bbf\u95ee\u7b56\u7565
     */
    public WebRequestLifecycle(BearerSubjectAuthenticator authenticator, WebAccessPolicy accessPolicy) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator must not be null");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy must not be null");
    }

    /**
     * \u6309\u8bbf\u95ee\u7b56\u7565\u8ba4\u8bc1\u8bf7\u6c42\u3002
     * @param context \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @return \u8ba4\u8bc1\u7ed3\u679c\uff1b\u514d\u8ba4\u8bc1\u65f6\u4e3a\u7a7a
     */
    public Optional<Authentication> authenticate(WebRequestContext context) {
        WebRequestContext requestContext = Objects.requireNonNull(context, "context must not be null");
        AuthenticationMode mode = Objects.requireNonNull(accessPolicy.authenticationMode(requestContext),
                "access policy must return an authentication mode");
        return switch (mode) {
            case DISABLED -> Optional.empty();
            case OPTIONAL -> authenticator.authenticateOptional(requestContext.authorization());
            case REQUIRED -> Optional.of(authenticator.authenticateSubject(requestContext.authorization()));
        };
    }
}

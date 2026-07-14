package io.ddd4j.web.core;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 决定一次 HTTP 请求采用何种认证模式。
 */
@FunctionalInterface
public interface WebAccessPolicy {

    AuthenticationMode authenticationMode(WebRequestContext context);

    static WebAccessPolicy disabled() {
        return context -> AuthenticationMode.DISABLED;
    }

    static WebAccessPolicy optional() {
        return context -> AuthenticationMode.OPTIONAL;
    }

    static WebAccessPolicy required() {
        return context -> AuthenticationMode.REQUIRED;
    }

    static WebAccessPolicy requiredExcept(Predicate<String> publicPath) {
        Predicate<String> pathPredicate = Objects.requireNonNull(publicPath, "publicPath must not be null");
        return context -> pathPredicate.test(context.path())
                ? AuthenticationMode.DISABLED : AuthenticationMode.REQUIRED;
    }
}

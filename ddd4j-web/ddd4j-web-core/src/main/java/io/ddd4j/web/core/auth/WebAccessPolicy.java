package io.ddd4j.web.core.auth;

import java.util.Objects;
import java.util.function.Predicate;

import io.ddd4j.web.core.context.WebRequestContext;

/**
 * 决定一次 HTTP 请求采用何种认证模式。
 */
@FunctionalInterface
public interface WebAccessPolicy {

    /**
     * \u51b3\u5b9a\u5f53\u524d\u8bf7\u6c42\u7684\u8ba4\u8bc1\u6a21\u5f0f\u3002
     * @param context \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @return \u8ba4\u8bc1\u6a21\u5f0f
     */
    AuthenticationMode authenticationMode(WebRequestContext context);

    /**
     * \u8fd4\u56de\u4e0d\u8981\u6c42\u8ba4\u8bc1\u7684\u8bbf\u95ee\u7b56\u7565\u3002
     * @return \u514d\u8ba4\u8bc1\u7b56\u7565
     */
    static WebAccessPolicy disabled() {
        return context -> AuthenticationMode.DISABLED;
    }

    /**
     * \u8fd4\u56de\u53ef\u9009\u8ba4\u8bc1\u7684\u8bbf\u95ee\u7b56\u7565\u3002
     * @return \u53ef\u9009\u8ba4\u8bc1\u7b56\u7565
     */
    static WebAccessPolicy optional() {
        return context -> AuthenticationMode.OPTIONAL;
    }

    /**
     * \u8fd4\u56de\u5fc5\u987b\u8ba4\u8bc1\u7684\u8bbf\u95ee\u7b56\u7565\u3002
     * @return \u5fc5\u987b\u8ba4\u8bc1\u7b56\u7565
     */
    static WebAccessPolicy required() {
        return context -> AuthenticationMode.REQUIRED;
    }

    /**
     * \u9664\u516c\u5f00\u8def\u5f84\u5916\u90fd\u8981\u6c42\u8ba4\u8bc1\u3002
     * @param publicPath \u516c\u5f00\u8def\u5f84\u5224\u65ad\u5668
     * @return \u8def\u5f84\u8bbf\u95ee\u7b56\u7565
     */
    static WebAccessPolicy requiredExcept(Predicate<String> publicPath) {
        Predicate<String> pathPredicate = Objects.requireNonNull(publicPath, "publicPath must not be null");
        return context -> pathPredicate.test(context.path())
                ? AuthenticationMode.DISABLED : AuthenticationMode.REQUIRED;
    }
}

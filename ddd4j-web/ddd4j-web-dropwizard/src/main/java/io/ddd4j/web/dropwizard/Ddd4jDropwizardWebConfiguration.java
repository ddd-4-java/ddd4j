package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.auth.AuthenticationMode;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 可作为业务 Dropwizard {@code Configuration} 嵌套属性的 ddd4j Web 配置。
 */
@Getter
@Setter
public class Ddd4jDropwizardWebConfiguration {

    /** \u8df3\u8fc7\u9ed8\u8ba4\u8ba4\u8bc1\u62e6\u622a\u7684\u516c\u5f00\u8def\u5f84\u914d\u7f6e\u3002 */
    private List<String> publicPaths = new ArrayList<>(List.of("/health", "/healthcheck/**"));
    /** \u672a\u5355\u72ec\u914d\u7f6e\u8def\u5f84\u65f6\u91c7\u7528\u7684\u9ed8\u8ba4\u9274\u6743\u6a21\u5f0f\u3002 */
    private AuthenticationMode defaultAuthenticationMode = AuthenticationMode.REQUIRED;
    /** \u662f\u5426\u5141\u8bb8\u7528\u53cd\u5411\u4ee3\u7406\u8f6c\u53d1\u5934\u89e3\u6790\u5ba2\u6237\u7aef\u5730\u5740\u3002 */
    private boolean trustForwardedHeaders;
    /** \u662f\u5426\u7ed9\u5339\u914d\u8def\u7531\u542f\u7528\u5e42\u7b49\u9632\u62a4\u3002 */
    private boolean idempotencyEnabled = true;
    /** \u5e42\u7b49\u68c0\u67e5\u67e5\u627e\u7684\u672c\u5730\u7f13\u5b58\u6ce8\u518c\u540d\u3002 */
    private String idempotencyCacheName = "ddd4j-web-idempotency";
    /** \u5e42\u7b49\u6761\u76ee\u5728\u7f13\u5b58\u4e2d\u7684\u5b58\u6d3b\u65f6\u95f4\u914d\u7f6e\u3002 */
    private Duration idempotencyTtl = Duration.ofMinutes(5);
}

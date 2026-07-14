package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.AuthenticationMode;
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

    private List<String> publicPaths = new ArrayList<>(List.of("/health", "/healthcheck/**"));
    private AuthenticationMode defaultAuthenticationMode = AuthenticationMode.REQUIRED;
    private boolean trustForwardedHeaders;
    private boolean idempotencyEnabled = true;
    private String idempotencyCacheName = "ddd4j-web-idempotency";
    private Duration idempotencyTtl = Duration.ofMinutes(5);
}

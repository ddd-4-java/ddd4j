package io.ddd4j.web.micronaut;

import io.ddd4j.web.core.AuthenticationMode;
import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Micronaut Web 适配配置。
 */
@Getter
@ConfigurationProperties("ddd4j.web")
public class Ddd4jMicronautWebConfiguration {

    private List<String> publicPaths = new ArrayList<>(List.of(
            "/health", "/health/readiness", "/health/liveness"));
    private AuthenticationMode defaultAuthenticationMode = AuthenticationMode.REQUIRED;
    private boolean trustForwardedHeaders;
    private boolean idempotencyEnabled = true;
    private String idempotencyCacheName = "ddd4j-web-idempotency";
    private Duration idempotencyTtl = Duration.ofMinutes(5);

    // Micronaut 在 Lombok 之前分析配置元数据，因此 setter 必须显式存在于源码 AST 中。
    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = new ArrayList<>(Objects.requireNonNull(publicPaths, "publicPaths must not be null"));
    }

    public void setDefaultAuthenticationMode(AuthenticationMode defaultAuthenticationMode) {
        this.defaultAuthenticationMode = Objects.requireNonNull(defaultAuthenticationMode,
                "defaultAuthenticationMode must not be null");
    }

    public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public void setIdempotencyEnabled(boolean idempotencyEnabled) {
        this.idempotencyEnabled = idempotencyEnabled;
    }

    public void setIdempotencyCacheName(String idempotencyCacheName) {
        this.idempotencyCacheName = Objects.requireNonNull(idempotencyCacheName,
                "idempotencyCacheName must not be null");
    }

    public void setIdempotencyTtl(Duration idempotencyTtl) {
        this.idempotencyTtl = Objects.requireNonNull(idempotencyTtl, "idempotencyTtl must not be null");
    }
}

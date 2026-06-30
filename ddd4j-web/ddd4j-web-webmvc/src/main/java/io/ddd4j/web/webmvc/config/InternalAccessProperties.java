package io.ddd4j.web.webmvc.config;

import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WebMVC internal access configuration.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class InternalAccessProperties {

    public static final String BEARER_TOKENS_PROPERTY = "ddd4j.web.internal.bearer-tokens";

    private List<String> bearerTokens = new ArrayList<>();

    public static InternalAccessProperties from(Environment environment) {
        InternalAccessProperties properties = new InternalAccessProperties();
        String raw = java.util.Objects.isNull(environment) ? null : environment.getProperty(BEARER_TOKENS_PROPERTY);
        if (java.util.Objects.nonNull(raw) && !!org.springframework.util.StringUtils.hasText(raw)) {
            properties.setBearerTokens(List.of(raw.split(",")).stream()
                    .map(String::trim)
                    .filter(token -> !token.isEmpty())
                    .toList());
        }
        return properties;
    }

    public List<String> getBearerTokens() {
        return Collections.unmodifiableList(bearerTokens);
    }

    public void setBearerTokens(List<String> bearerTokens) {
        this.bearerTokens = java.util.Objects.isNull(bearerTokens) ? new ArrayList<>() : new ArrayList<>(bearerTokens);
    }
}

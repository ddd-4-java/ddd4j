package io.ddd4j.sample.dropwizard;

import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebConfiguration;
import io.dropwizard.core.Configuration;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Dropwizard Order 示例的应用配置。
 */
@Getter
@Setter
public class DropwizardOrderConfiguration extends Configuration {

    private Ddd4jDropwizardWebConfiguration ddd4jWeb = defaultWebConfiguration();

    private static Ddd4jDropwizardWebConfiguration defaultWebConfiguration() {
        Ddd4jDropwizardWebConfiguration configuration = new Ddd4jDropwizardWebConfiguration();
        configuration.setPublicPaths(List.of("/health", "/healthcheck/**", "/api/auth/**"));
        return configuration;
    }
}

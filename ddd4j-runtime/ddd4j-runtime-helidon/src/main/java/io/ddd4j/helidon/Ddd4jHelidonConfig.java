package io.ddd4j.helidon;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Objects;
import java.util.Optional;

/**
 * Helidon MP 配置访问边界，避免核心代码依赖 MicroProfile Config。
 */
public final class Ddd4jHelidonConfig {

    private final Config config;

    public Ddd4jHelidonConfig() {
        this(ConfigProvider.getConfig());
    }

    public Ddd4jHelidonConfig(Config config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public <T> Optional<T> value(String name, Class<T> type) {
        return config.getOptionalValue(name, type);
    }
}

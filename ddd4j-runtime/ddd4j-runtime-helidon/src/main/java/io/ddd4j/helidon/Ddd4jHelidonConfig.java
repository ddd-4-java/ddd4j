package io.ddd4j.helidon;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Objects;
import java.util.Optional;

/**
 * Helidon MP 配置访问边界，避免核心代码依赖 MicroProfile Config。
 */
public final class Ddd4jHelidonConfig {

    /** Helidon \u5bb9\u5668\u63d0\u4f9b\u7684\u914d\u7f6e\u67e5\u8be2\u5165\u53e3\u3002 */
    private final Config config;

    /** \u4f7f\u7528\u5f53\u524d MicroProfile \u914d\u7f6e\u521b\u5efa\u8bbf\u95ee\u5668\u3002 */
    public Ddd4jHelidonConfig() {
        this(ConfigProvider.getConfig());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u914d\u7f6e\u6e90\u521b\u5efa\u8bbf\u95ee\u5668\u3002
     * @param config MicroProfile \u914d\u7f6e\u6e90
     */
    public Ddd4jHelidonConfig(Config config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * \u8bfb\u53d6\u53ef\u9009\u914d\u7f6e\u503c\u3002
     * @param <T> \u914d\u7f6e\u503c\u7c7b\u578b
     * @param name \u914d\u7f6e\u540d\u79f0
     * @param type \u914d\u7f6e\u503c\u7c7b\u578b
     * @return \u53ef\u9009\u914d\u7f6e\u503c
     */
    public <T> Optional<T> value(String name, Class<T> type) {
        return config.getOptionalValue(name, type);
    }
}

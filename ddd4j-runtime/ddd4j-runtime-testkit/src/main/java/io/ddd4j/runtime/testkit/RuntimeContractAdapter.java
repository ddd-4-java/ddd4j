package io.ddd4j.runtime.testkit;

import java.util.Map;
import java.util.Objects;

/**
 * 将框架运行时的启动和关闭动作适配为共享契约。
 */
public final class RuntimeContractAdapter implements RuntimeContract {

    private final Runnable starter;
    private final Runnable closer;
    private final Map<String, Class<?>> services;

    public RuntimeContractAdapter(Runnable starter, Runnable closer, Map<String, Class<?>> services) {
        this.starter = Objects.requireNonNull(starter, "starter must not be null");
        this.closer = Objects.requireNonNull(closer, "closer must not be null");
        this.services = Map.copyOf(Objects.requireNonNull(services, "services must not be null"));
    }

    @Override
    public void start() {
        starter.run();
    }

    @Override
    public Map<String, Class<?>> services() {
        return services;
    }

    @Override
    public void close() {
        closer.run();
    }
}

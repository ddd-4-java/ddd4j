package io.ddd4j.runtime.testkit;

import io.ddd4j.core.health.ReadinessReport;

import java.util.Map;

/**
 * 各运行时适配器用于共享生命周期测试的最小控制面。
 */
public interface RuntimeContract extends AutoCloseable {

    void start();

    Map<String, Class<?>> services();

    ReadinessReport readiness();

    @Override
    void close();
}

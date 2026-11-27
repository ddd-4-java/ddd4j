package io.ddd4j.runtime.testkit;

import io.ddd4j.core.health.ReadinessReport;

import java.util.Map;

/**
 * 各运行时适配器用于共享生命周期测试的最小控制面。
 */
public interface RuntimeContract extends AutoCloseable {

    /** \u542f\u52a8\u8fd0\u884c\u65f6\u3002 */
    void start();

    /** @return \u8fd0\u884c\u65f6\u6ce8\u518c\u7684\u670d\u52a1\u952e\u4e0e\u63a5\u53e3\u7c7b\u578b */
    Map<String, Class<?>> services();

    /** @return \u5f53\u524d\u5c31\u7eea\u72b6\u6001 */
    ReadinessReport readiness();

    /** 结束当前组件持有的资源。 */
    @Override
    void close();
}

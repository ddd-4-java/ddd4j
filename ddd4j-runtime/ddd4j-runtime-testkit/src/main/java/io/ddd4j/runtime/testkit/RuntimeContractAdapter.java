package io.ddd4j.runtime.testkit;

import io.ddd4j.core.health.ReadinessReport;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 将框架运行时的启动和关闭动作适配为共享契约。
 */
public final class RuntimeContractAdapter implements RuntimeContract {

    /** \u542f\u52a8\u53d7\u6d4b\u8fd0\u884c\u65f6\u7684\u56de\u8c03\u3002 */
    private final Runnable starter;
    /** \u505c\u6b62\u5e76\u6e05\u7406\u53d7\u6d4b\u8fd0\u884c\u65f6\u7684\u56de\u8c03\u3002 */
    private final Runnable closer;
    /** \u53d7\u6d4b\u8fd0\u884c\u65f6\u5e94\u6ce8\u518c\u7684\u670d\u52a1\u952e\u4e0e\u63a5\u53e3\u7c7b\u578b\u3002 */
    private final Map<String, Class<?>> services;
    /** \u83b7\u53d6\u53d7\u6d4b\u8fd0\u884c\u65f6\u5f53\u524d\u5c31\u7eea\u62a5\u544a\u7684\u56de\u8c03\u3002 */
    private final Supplier<ReadinessReport> readinessSupplier;

    /**
     * \u521b\u5efa\u8fd0\u884c\u65f6\u5951\u7ea6\u9002\u914d\u5668\u3002
     * @param starter \u542f\u52a8\u52a8\u4f5c
     * @param closer \u5173\u95ed\u52a8\u4f5c
     * @param services \u6ce8\u518c\u7684\u670d\u52a1\u952e\u4e0e\u63a5\u53e3\u7c7b\u578b
     * @param readinessSupplier \u5c31\u7eea\u72b6\u6001\u63d0\u4f9b\u8005
     */
    public RuntimeContractAdapter(Runnable starter, Runnable closer, Map<String, Class<?>> services,
                                  Supplier<ReadinessReport> readinessSupplier) {
        this.starter = Objects.requireNonNull(starter, "starter must not be null");
        this.closer = Objects.requireNonNull(closer, "closer must not be null");
        this.services = Map.copyOf(Objects.requireNonNull(services, "services must not be null"));
        this.readinessSupplier = Objects.requireNonNull(readinessSupplier, "readinessSupplier must not be null");
    }

    /**
     * 启动当前组件并准备其运行资源。
     *
     * <p>此实现调用 <code>starter.run()</code>。</p>
     */
    @Override
    public void start() {
        starter.run();
    }

    /**
     * 读取<code>services</code>提供给调用方。
     *
     * @return 实际返回<code>services</code>，供调用方取得当前值
     */
    @Override
    public Map<String, Class<?>> services() {
        return services;
    }

    /**
     * 通过<code>readinessSupplier.get()</code>处理当前调用。
     *
     * @return 实际返回<code>readinessSupplier.get()</code>
     */
    @Override
    public ReadinessReport readiness() {
        return readinessSupplier.get();
    }

    /** \u6267\u884c\u5f53\u524d\u88ab\u6d4b\u8fd0\u884c\u65f6\u7684\u6e05\u7406\u56de\u8c03\u3002 */
    @Override
    public void close() {
        closer.run();
    }
}

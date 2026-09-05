package io.ddd4j.core.cqrs.readmodel;

/**
 * 空实现的投影指标（no-op 单例）。
 *
 * <p>所有回调方法均为空操作，适用于不需要指标采集的场景。
 * 通过 {@link #INSTANCE} 获取全局单例。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public final class NoopProjectionMetrics implements ProjectionMetrics {
    public static final NoopProjectionMetrics INSTANCE = new NoopProjectionMetrics();
    private NoopProjectionMetrics() { }
}

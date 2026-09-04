package io.ddd4j.core.cqrs.readmodel;

/** 空投影指标实现。 */
public final class NoopProjectionMetrics implements ProjectionMetrics {
    public static final NoopProjectionMetrics INSTANCE = new NoopProjectionMetrics();
    private NoopProjectionMetrics() { }
}

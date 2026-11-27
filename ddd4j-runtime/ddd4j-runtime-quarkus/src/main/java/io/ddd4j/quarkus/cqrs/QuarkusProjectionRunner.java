package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.*;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

/**
 * durable 管线的 CDI/JTA 边界；事件执行及进度规则完全复用 core。
 */
@Singleton
@ActivateRequestContext
@IfBuildProperty(name = "ddd4j.quarkus.cqrs.projection.mode", stringValue = "durable")
public class QuarkusProjectionRunner extends ProjectionRunner<Object> {
    /**
     * 注入明确的事件源、位置服务和可选指标。
     *
     * @param service 位置服务
     * @param reader  事件来源
     * @param metrics 可选运行指标
     */
    @Inject
    public QuarkusProjectionRunner(ProjectionService service, EventChunkReader<Object> reader,
                                   Instance<ProjectionMetrics> metrics) {
        super(service, reader, metrics.isResolvable() ? metrics.get() : null);
        if (metrics.isAmbiguous()) {
            throw new IllegalStateException("Multiple ProjectionMetrics beans are available");
        }
    }

    /**
     * \u540c\u4e00\u4e8b\u52a1\u8986\u76d6\u4e1a\u52a1\u89c6\u56fe\u4e0e\u4f4d\u7f6e\u66f4\u65b0\uff1b\u5f02\u5e38\u4ea4\u7ed9 JTA \u56de\u6eda\u3002
     *
     * @param view \u672c\u6b21\u6267\u884c\u7684\u6295\u5f71\u89c6\u56fe
     * @return \u672c\u6b21\u6295\u5f71\u6267\u884c\u7684\u5904\u7406\u7ed3\u679c
     */
    @Override
    @Transactional
    public EventChunk<Object> runOnce(ProjectionView<Object> view) {
        return super.runOnce(view);
    }
}

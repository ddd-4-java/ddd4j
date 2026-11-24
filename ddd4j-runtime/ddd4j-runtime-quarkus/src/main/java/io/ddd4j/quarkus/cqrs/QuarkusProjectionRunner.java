package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.core.cqrs.readmodel.ProjectionMetrics;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionService;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Singleton;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** durable 管线的 CDI/JTA 边界；事件执行及进度规则完全复用 core。 */
@Singleton
@ActivateRequestContext
@IfBuildProperty(name = "ddd4j.quarkus.cqrs.projection.mode", stringValue = "durable")
public class QuarkusProjectionRunner extends ProjectionRunner<Object> {
    /** 注入明确的事件源、位置服务和可选指标。
     * @param service 位置服务
     * @param reader 事件来源
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

    /** 同一事务覆盖业务视图与位置更新；异常交给 JTA 回滚。 */
    @Override
    @Transactional
    public EventChunk<Object> runOnce(ProjectionView<Object> view) {
        return super.runOnce(view);
    }
}

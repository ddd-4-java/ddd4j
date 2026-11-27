package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Quarkus CQRS 投影拉取服务。
 * <p>
 * 基于 {@code @Scheduled} 定时拉取投影事件，驱动 CQRS 读侧视图增量更新。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@ApplicationScoped
public class QuarkusJpaProjectionService {

    /**
     * 投影位置仓储实例
     */
    @Inject
    Instance<ProjectionPositionRepository> projectionPositionRepository;

    /** \u6267\u884c\u517c\u5bb9\u8c03\u5ea6\u5165\u53e3\u89e6\u53d1\u7684\u6295\u5f71\u5904\u7406\u3002 */
    @Scheduled(cron = "${ddd4j.quarkus.cqrs.projection.cron:*/30 * * * * ?}")
    void scheduled() {
        runOnce();
    }

    /** \u5728\u4e8b\u52a1\u4e2d\u6267\u884c\u4e00\u6b21\u5f85\u5904\u7406\u6295\u5f71\u62c9\u53d6\u3002 */
    @Transactional
    public void runOnce() {
        if (projectionPositionRepository.isUnsatisfied()) {
            log.debug("ProjectionPositionRepository not provided, skip projection");
            return;
        }
        var positions = projectionPositionRepository.get().findAll();
        if (positions.isEmpty()) {
            log.debug("No projection positions registered, skip");
            return;
        }
        for (ProjectionPosition position : positions) {
            try {
                pullAndApply(position.getStreamId());
            } catch (Exception ex) {
                log.warn("Failed to pull and apply projection for stream '{}'", position.getStreamId(), ex);
            }
        }
    }

    /**
     * \u62c9\u53d6\u5e76\u5e94\u7528\u6307\u5b9a\u6d41\u7684\u4e8b\u4ef6\u3002
     * @param streamId \u6295\u5f71\u6d41\u6807\u8bc6
     */
    protected void pullAndApply(String streamId) {
        log.debug("pullAndApply for stream '{}' (override me to implement actual projection)", streamId);
    }
}

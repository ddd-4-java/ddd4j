package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.domain.query.projection.ProjectionPosition;
import io.ddd4j.core.domain.query.projection.ProjectionPositionRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Quarkus CQRS 投影拉取服务。
 */
@Slf4j
@ApplicationScoped
public class QuarkusJpaProjectionService {

    @Inject
    Instance<ProjectionPositionRepository> projectionPositionRepository;

    @Scheduled(cron = "${ddd4j.quarkus.cqrs.projection.cron:*/30 * * * * ?}")
    void scheduled() {
        runOnce();
    }

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

    protected void pullAndApply(String streamId) {
        log.debug("pullAndApply for stream '{}' (override me to implement actual projection)", streamId);
    }
}

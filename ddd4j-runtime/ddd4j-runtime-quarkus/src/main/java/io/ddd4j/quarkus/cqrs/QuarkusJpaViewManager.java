package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Shutdown;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Quarkus CQRS 读侧视图管理器（基于 {@code @Scheduled}）。
 *
 * <p>实现 ddd4j-core 的 {@link ViewManager} SPI。Quarkus 通过 CDI 事件
 * （{@link Startup} / {@link Shutdown}）自动触发 start/stop。
 *
 * <p>实际视图拉取由 {@code QuarkusJpaProjectionService}（{@code @Scheduled}）
 * 负责，本类仅负责生命周期管理。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-QUARKUS : ViewManager ###")
@ApplicationScoped
public class QuarkusJpaViewManager implements ViewManager {

    /**
     * 运行状态标志
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    void onStart(@Observes Startup event) {
        start();
    }

    void onStop(@Observes Shutdown event) {
        stop();
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("QuarkusJpaViewManager started");
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("QuarkusJpaViewManager stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void triggerOnce() {
        log.info("triggerOnce() - 业务方应在 QuarkusJpaProjectionService override");
    }
}

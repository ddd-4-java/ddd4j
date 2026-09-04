package io.ddd4j.web.webflux;

import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import io.ddd4j.web.core.health.ReadinessEndpoint;
import io.ddd4j.web.core.health.ReadinessResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Spring WebFlux 的显式 readiness HTTP 端点。
 */
@RestController
public final class Ddd4jWebFluxReadinessController {

    private final ReadinessEndpoint readinessEndpoint;

    public Ddd4jWebFluxReadinessController(RuntimeReadinessRegistry readinessRegistry) {
        RuntimeReadinessRegistry registry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
        readinessEndpoint = new ReadinessEndpoint(() -> registry.readiness().ready());
    }

    /**
     * 返回整体运行时就绪状态，不包含下游依赖详情。
     *
     * @return 就绪时 200，未就绪时 503
     */
    @GetMapping(ReadinessEndpoint.PATH)
    public Mono<ResponseEntity<ReadinessResponse>> readiness() {
        return Mono.fromSupplier(readinessEndpoint::readiness)
                .map(response -> ResponseEntity.status(response.httpStatus()).body(response));
    }
}

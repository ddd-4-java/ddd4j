package io.ddd4j.web.webmvc;

import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import io.ddd4j.web.core.ReadinessEndpoint;
import io.ddd4j.web.core.ReadinessResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Spring MVC 的显式 readiness HTTP 端点。
 */
@RestController
public final class Ddd4jWebMvcReadinessController {

    private final ReadinessEndpoint readinessEndpoint;

    public Ddd4jWebMvcReadinessController(RuntimeReadinessRegistry readinessRegistry) {
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
    public ResponseEntity<ReadinessResponse> readiness() {
        ReadinessResponse response = readinessEndpoint.readiness();
        return ResponseEntity.status(response.httpStatus()).body(response);
    }
}

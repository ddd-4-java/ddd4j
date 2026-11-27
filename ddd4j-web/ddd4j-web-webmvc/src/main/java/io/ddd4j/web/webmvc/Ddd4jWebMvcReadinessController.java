package io.ddd4j.web.webmvc;

import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import io.ddd4j.web.core.health.ReadinessEndpoint;
import io.ddd4j.web.core.health.ReadinessResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Spring MVC 的显式 readiness HTTP 端点。
 */
@RestController
public final class Ddd4jWebMvcReadinessController {

    /** \u63d0\u4f9b\u4f9d\u8d56\u5c31\u7eea\u72b6\u6001\u7684 Web \u7aef\u70b9\u3002 */
    private final ReadinessEndpoint readinessEndpoint;

    /**
     * \u521b\u5efa Ddd4jWebMvcReadinessController \u5b9e\u4f8b\u3002
     * @param readinessRegistry \u5c31\u7eea\u72b6\u6001\u6ce8\u518c\u8868
     */
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

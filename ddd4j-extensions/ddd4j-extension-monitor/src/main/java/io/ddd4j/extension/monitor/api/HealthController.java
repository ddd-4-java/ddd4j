package io.ddd4j.extension.monitor.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查 Controller
 *
 * <p>提供应用健康检查的 REST 接口，用于 Kubernetes 探针或负载均衡健康检测。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }
}

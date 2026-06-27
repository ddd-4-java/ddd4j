package io.ddd4j.extension.monitor.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class HealthController {

    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }
}

/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

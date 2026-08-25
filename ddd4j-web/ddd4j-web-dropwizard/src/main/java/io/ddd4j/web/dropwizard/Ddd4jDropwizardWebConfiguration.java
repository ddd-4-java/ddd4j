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
package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.auth.AuthenticationMode;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 可作为业务 Dropwizard {@code Configuration} 嵌套属性的 ddd4j Web 配置。
 */
@Getter
@Setter
public class Ddd4jDropwizardWebConfiguration {

    private List<String> publicPaths = new ArrayList<>(List.of("/health", "/healthcheck/**"));
    private AuthenticationMode defaultAuthenticationMode = AuthenticationMode.REQUIRED;
    private boolean trustForwardedHeaders;
    private boolean idempotencyEnabled = true;
    private String idempotencyCacheName = "ddd4j-web-idempotency";
    private Duration idempotencyTtl = Duration.ofMinutes(5);
}

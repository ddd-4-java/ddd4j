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
package io.ddd4j.sample.dropwizard.cqrs;

import io.dropwizard.core.Configuration;

/**
 * Dropwizard CQRS 配置。
 */
@lombok.Getter @lombok.Setter
public class DropwizardCqrsConfiguration extends Configuration {
    // 没有 JDBC 配置时保留显式标记的旧 demo-memory。
    private String jdbcUrl = System.getProperty("sample.jdbc.url");
    private String jdbcUser = System.getProperty("sample.jdbc.user", "postgres");
    private String jdbcPassword = System.getProperty("sample.jdbc.password", "postgres");
}

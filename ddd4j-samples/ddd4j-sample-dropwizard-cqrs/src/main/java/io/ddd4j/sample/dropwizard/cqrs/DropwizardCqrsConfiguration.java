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
@lombok.Getter
@lombok.Setter
public class DropwizardCqrsConfiguration extends Configuration {
    /** \u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u7684 JDBC \u8fde\u63a5\u5730\u5740\u3002 */
    private String catalogJdbcUrl;
    /** \u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u6570\u636e\u5e93\u8d26\u53f7\u3002 */
    private String catalogDbUser;
    /** \u8fde\u63a5\u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u6570\u636e\u5e93\u6240\u7528\u7684\u53e3\u4ee4\u3002 */
    private String catalogDbPassword;
}

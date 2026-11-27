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
package io.ddd4j.sample.dropwizard;

import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.dropwizard.Ddd4jDropwizardWebConfiguration;
import io.dropwizard.Configuration;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

/**
 * Dropwizard Order 示例的应用配置。
 */
@Getter
@Setter
public class DropwizardOrderConfiguration extends Configuration {

    /**
     * \u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u7684 JDBC \u8fde\u63a5\u5730\u5740\u3002
     */
    private String catalogJdbcUrl;
    /**
     * \u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u6570\u636e\u5e93\u8d26\u53f7\u3002
     */
    private String catalogDbUser;
    /**
     * \u8fde\u63a5\u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u6570\u636e\u5e93\u6240\u7528\u7684\u53e3\u4ee4\u3002
     */
    private String catalogDbPassword;
    /**
     * \u5f53\u524d\u793a\u4f8b\u4f7f\u7528\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587\u548c\u8bbf\u95ee\u7b56\u7565\u914d\u7f6e\u3002
     */
    private Ddd4jDropwizardWebConfiguration ddd4jWeb = defaultWebConfiguration();

    /**
     * \u4e3a\u666e\u901a\u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u51c6\u5907\u7f3a\u7701 Web \u914d\u7f6e\u3002
     * @return \u542b\u793a\u4f8b\u8bbf\u95ee\u7b56\u7565\u7684 Web \u914d\u7f6e
     */
    private static Ddd4jDropwizardWebConfiguration defaultWebConfiguration() {
        Ddd4jDropwizardWebConfiguration configuration = new Ddd4jDropwizardWebConfiguration();
        configuration.setDefaultAuthenticationMode(AuthenticationMode.DISABLED);
        configuration.setPublicPaths(Arrays.asList("/health", "/healthcheck/**", "/api/auth/**"));
        return configuration;
    }
}

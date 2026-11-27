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
import io.dropwizard.core.Configuration;
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
     * 商品交易示例的 JDBC 连接地址。
     */
    private String catalogJdbcUrl;
    /**
     * 商品交易示例数据库账号。
     */
    private String catalogDbUser;
    /**
     * 连接商品交易示例数据库所用的口令。
     */
    private String catalogDbPassword;
    /**
     * 当前示例使用的请求上下文和访问策略配置。
     */
    private Ddd4jDropwizardWebConfiguration ddd4jWeb = defaultWebConfiguration();

    /**
     * 为普通商品交易示例准备缺省 Web 配置。
     *
     * @return 含示例访问策略的 Web 配置
     */
    private static Ddd4jDropwizardWebConfiguration defaultWebConfiguration() {
        Ddd4jDropwizardWebConfiguration configuration = new Ddd4jDropwizardWebConfiguration();
        configuration.setDefaultAuthenticationMode(AuthenticationMode.DISABLED);
        configuration.setPublicPaths(Arrays.asList("/health", "/healthcheck/**", "/api/auth/**"));
        return configuration;
    }
}

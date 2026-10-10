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
package io.ddd4j.auth.license;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 许可证（License）默认自动配置。
 *
 * <p>注册 {@link LicenseVerify} Bean：容器启动时安装许可证，容器关闭时卸载许可证。
 *
 * @since 2.0.x
 */
@Configuration(proxyBeanMethods = false)
// @EnableConfigurationProperties({LicenseProperties.class})
public class DefaultLicenseAutoConfiguration {

    /**
     * 创建许可证校验 Bean。
     *
     * @param properties 许可证配置属性（subject、公钥别称、密码、签名算法等）
     * @return 按配置装配完成的 {@link LicenseVerify} 实例
     */
    @Bean(initMethod = "installLicense", destroyMethod = "unInstallLicense")
    public LicenseVerify licenseVerify(LicenseProperties properties) {
        return new LicenseVerify(properties.getSubject(), properties.getPublicAlias(), properties.getStorePass(),
                properties.getLicensePath(), properties.getPublicKeysStorePath(), properties.getSignatureAlgorithm());
    }

}

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
 * \u8bb8\u53ef\u8bc1\uff08License\uff09\u9ed8\u8ba4\u81ea\u52a8\u914d\u7f6e\u3002
 *
 *  <p>\u6ce8\u518c {@link LicenseVerify} Bean\uff1a\u5bb9\u5668\u542f\u52a8\u65f6\u5b89\u88c5\u8bb8\u53ef\u8bc1\uff0c\u5bb9\u5668\u5173\u95ed\u65f6\u5378\u8f7d\u8bb8\u53ef\u8bc1\u3002
 * @since 2.0.x
 */
@Configuration(proxyBeanMethods = false)
// @EnableConfigurationProperties({LicenseProperties.class})
public class DefaultLicenseAutoConfiguration {

    /**
     * \u521b\u5efa\u8bb8\u53ef\u8bc1\u6821\u9a8c Bean\u3002
     * @param properties \u8bb8\u53ef\u8bc1\u914d\u7f6e\u5c5e\u6027\uff08subject\u3001\u516c\u94a5\u522b\u79f0\u3001\u5bc6\u7801\u3001\u7b7e\u540d\u7b97\u6cd5\u7b49\uff09
     * @return \u6309\u914d\u7f6e\u88c5\u914d\u5b8c\u6210\u7684 {@link LicenseVerify} \u5b9e\u4f8b
     */
    @Bean(initMethod = "installLicense", destroyMethod = "unInstallLicense")
    public LicenseVerify licenseVerify(LicenseProperties properties) {
        return new LicenseVerify(properties.getSubject(), properties.getPublicAlias(), properties.getStorePass(),
                properties.getLicensePath(), properties.getPublicKeysStorePath(), properties.getSignatureAlgorithm());
    }

}

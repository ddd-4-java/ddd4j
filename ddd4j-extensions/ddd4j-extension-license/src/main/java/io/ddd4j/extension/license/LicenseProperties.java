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
package io.ddd4j.extension.license;

import lombok.Data;

/**
 * License 配置属性类。
 *
 * <p>映射配置文件中以 "license" 为前缀的配置项，包括证书主题、公钥别称、证书路径、
 * 验证结果缓存 TTL 等。作为纯 POJO 使用；在 Spring 环境下可启用
 * {@code @ConfigurationProperties(prefix = LicenseProperties.PREFIX)} 完成自动绑定。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
// @ConfigurationProperties(prefix = LicenseProperties.PREFIX)
@Data
public class LicenseProperties {

    public static final String PREFIX = "license";

    /**
     * 是否启用 License 校验（关闭时 {@code verify} 恒返回 true）
     */
    private boolean enabled = true;

    /**
     * 证书subject
     */
    private String subject;

    /**
     * 公钥别称
     */
    private String publicAlias;

    /**
     * 访问公钥库的密码
     */
    private String storePass;

    /**
     * 证书生成路径
     */
    private String licensePath;

    /**
     * 密钥库存储路径
     */
    private String publicKeysStorePath;

    /**
     * 验证结果缓存 TTL（秒），默认 300s。
     *
     * <p>缓存命中时可跳过昂贵的 TrueLicense 验签 + XML 解码。
     */
    private long cacheTtlSeconds = 300L;

}

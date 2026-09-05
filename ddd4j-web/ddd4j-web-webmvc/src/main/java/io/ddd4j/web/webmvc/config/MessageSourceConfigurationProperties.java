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
package io.ddd4j.web.webmvc.config;

import lombok.Data;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 国际化 MessageSource 配置（对应 Boot {@code spring.messages.*}，Framework 层本地绑定）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class MessageSourceConfigurationProperties {

    /**
     * 资源 bundle 基名列表，默认 {@code messages}
     */
    private List<String> basename = new ArrayList<>(List.of("messages"));

    /**
     * 默认编码
     */
    private Charset encoding = StandardCharsets.UTF_8;

    /**
     * 找不到 locale 时是否回退系统 locale
     */
    private boolean fallbackToSystemLocale = true;

    /**
     * 缓存时长
     */
    private Duration cacheDuration;

    /**
     * 是否始终使用 MessageFormat
     */
    private boolean alwaysUseMessageFormat = false;

    /**
     * 找不到 key 时是否直接使用 key 作为默认消息
     */
    private boolean useCodeAsDefaultMessage = false;
}

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
package io.ddd4j.web.webflux;

import io.ddd4j.spring.properties.SpringPropertySourcePostProcessor;
import io.ddd4j.web.webflux.config.MessageSourceConfigurationProperties;
import io.ddd4j.web.webflux.error.I18nResourceBasenameHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extension.context.NestedMessageSource;
import org.springframework.extension.context.support.MultiResourceBundleMessageSource;
import org.springframework.extension.context.support.ResourceBasenameHandler;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.Objects;

/**
 * MessageSource 与属性源默认配置（Boot 条件装配已迁出至 ddd4j-boot 轨）。
 */
@Configuration(proxyBeanMethods = false)
/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class DefaultMessageSourceConfiguration {

    /**
     * 属性源后置处理器。
     */
    @Bean
    public SpringPropertySourcePostProcessor bladePropertySourcePostProcessor() {
        return new SpringPropertySourcePostProcessor();
    }

    /**
     * 国际化配置属性 Bean。
     */
    @Bean
    @Primary
    public MessageSourceConfigurationProperties myMessageSourceProperties() {
        return new MessageSourceConfigurationProperties();
    }

    /**
     * 资源 bundle 基名解析器。
     */
    @Bean
    public ResourceBasenameHandler resourceBasenameHandler() {
        return new I18nResourceBasenameHandler();
    }

    /**
     * 主 MessageSource：支持多 bundle 与 basename 动态解析。
     */
    @Bean
    @Primary
    public MessageSource messageSource(
            @Qualifier("myMessageSourceProperties") MessageSourceConfigurationProperties properties,
            ResourceBasenameHandler resourceBasenameHandler) {
        MultiResourceBundleMessageSource messageSource = new MultiResourceBundleMessageSource();
        messageSource.setBasenameHandler(resourceBasenameHandler);
        if (!CollectionUtils.isEmpty(properties.getBasename())) {
            messageSource.setBasenames(properties.getBasename().toArray(new String[0]));
        }
        if (Objects.nonNull(properties.getEncoding())) {
            messageSource.setDefaultEncoding(properties.getEncoding().name());
        }
        messageSource.setFallbackToSystemLocale(properties.isFallbackToSystemLocale());
        Duration cacheDuration = properties.getCacheDuration();
        if (Objects.nonNull(cacheDuration)) {
            messageSource.setCacheMillis(cacheDuration.toMillis());
        }
        messageSource.setAlwaysUseMessageFormat(properties.isAlwaysUseMessageFormat());
        messageSource.setUseCodeAsDefaultMessage(properties.isUseCodeAsDefaultMessage());
        return messageSource;
    }

    /**
     * 嵌套 MessageSource，聚合容器内全部 MessageSource 实现。
     */
    @Bean
    public NestedMessageSource nestedMessageSource(ObjectProvider<MessageSource> messageSourceProvider) {
        MessageSource[] messageSources = messageSourceProvider.orderedStream().toArray(MessageSource[]::new);
        return new NestedMessageSource(messageSources);
    }
}

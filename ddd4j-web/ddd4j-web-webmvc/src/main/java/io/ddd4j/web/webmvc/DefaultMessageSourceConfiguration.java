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
package io.ddd4j.web.webmvc;

import io.ddd4j.spring.properties.BasePropertySourcePostProcessor;
import io.ddd4j.web.webmvc.config.MessageSourceConfigurationProperties;
import io.ddd4j.web.webmvc.error.I18nResourceBasenameHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.extension.context.NestedMessageSource;
import org.springframework.extension.context.support.MultiResourceBundleMessageSource;
import org.springframework.extension.context.support.ResourceBasenameHandler;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.Objects;

/**
 * MessageSource 与属性源默认配置（Boot 条件装配已迁出至 ddd4j-boot 轨）。
 */
@Configuration(proxyBeanMethods = false)
public class DefaultMessageSourceConfiguration {

    /**
     * \u5c5e\u6027\u6e90\u540e\u7f6e\u5904\u7406\u5668\u3002
     *
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    public BasePropertySourcePostProcessor bladePropertySourcePostProcessor() {
        return new BasePropertySourcePostProcessor();
    }

    /**
     * \u56fd\u9645\u5316\u914d\u7f6e\u5c5e\u6027 Bean\u3002
     *
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    @Primary
    public MessageSourceConfigurationProperties myMessageSourceProperties() {
        return new MessageSourceConfigurationProperties();
    }

    /**
     * \u8d44\u6e90 bundle \u57fa\u540d\u89e3\u6790\u5668\u3002
     *
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     */
    @Bean
    public ResourceBasenameHandler resourceBasenameHandler() {
        return new I18nResourceBasenameHandler();
    }

    /**
     * \u4e3b MessageSource\uff1a\u652f\u6301\u591a bundle \u4e0e basename \u52a8\u6001\u89e3\u6790\u3002
     *
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     * @param resourceBasenameHandler \u8d44\u6e90\u6587\u4ef6\u540d\u5904\u7406\u5668
     * @param properties \u914d\u7f6e\u5c5e\u6027
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
     * \u5d4c\u5957 MessageSource\uff0c\u805a\u5408\u5bb9\u5668\u5185\u5168\u90e8 MessageSource \u5b9e\u73b0\u3002
     *
     * @return \u914d\u7f6e\u6216\u5904\u7406\u540e\u7684\u7ec4\u4ef6
     * @param messageSourceProvider \u6d88\u606f\u6e90\u63d0\u4f9b\u8005
     */
    @Bean
    public NestedMessageSource nestedMessageSource(ObjectProvider<MessageSource> messageSourceProvider) {
        MessageSource[] messageSources = messageSourceProvider.orderedStream().toArray(MessageSource[]::new);
        return new NestedMessageSource(messageSources);
    }
}

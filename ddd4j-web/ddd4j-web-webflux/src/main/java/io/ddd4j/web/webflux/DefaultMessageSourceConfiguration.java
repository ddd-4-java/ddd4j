/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
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
public class DefaultMessageSourceConfiguration {

    /**
     * \u5c5e\u6027\u6e90\u540e\u7f6e\u5904\u7406\u5668\u3002
     *
     * @return \u5c5e\u6027\u6e90\u540e\u7f6e\u5904\u7406\u5668
     */
    @Bean
    public SpringPropertySourcePostProcessor bladePropertySourcePostProcessor() {
        return new SpringPropertySourcePostProcessor();
    }

    /**
     * \u56fd\u9645\u5316\u914d\u7f6e\u5c5e\u6027 Bean\u3002
     *
     * @return \u56fd\u9645\u5316\u914d\u7f6e\u5c5e\u6027
     */
    @Bean
    @Primary
    public MessageSourceConfigurationProperties myMessageSourceProperties() {
        return new MessageSourceConfigurationProperties();
    }

    /**
     * \u8d44\u6e90 bundle \u57fa\u540d\u89e3\u6790\u5668\u3002
     *
     * @return \u8d44\u6e90\u57fa\u540d\u89e3\u6790\u5668
     */
    @Bean
    public ResourceBasenameHandler resourceBasenameHandler() {
        return new I18nResourceBasenameHandler();
    }

    /**
     * \u4e3b MessageSource\uff1a\u652f\u6301\u591a bundle \u4e0e basename \u52a8\u6001\u89e3\u6790\u3002
     *
     * @param properties \u56fd\u9645\u5316\u914d\u7f6e\u5c5e\u6027
     * @param resourceBasenameHandler \u8d44\u6e90\u57fa\u540d\u89e3\u6790\u5668
     * @return \u4e3b\u6d88\u606f\u6e90
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
     * @param messageSourceProvider \u5bb9\u5668\u5185\u7684\u6d88\u606f\u6e90\u63d0\u4f9b\u5668
     * @return \u5d4c\u5957\u6d88\u606f\u6e90
     */
    @Bean
    public NestedMessageSource nestedMessageSource(ObjectProvider<MessageSource> messageSourceProvider) {
        MessageSource[] messageSources = messageSourceProvider.orderedStream().toArray(MessageSource[]::new);
        return new NestedMessageSource(messageSources);
    }
}

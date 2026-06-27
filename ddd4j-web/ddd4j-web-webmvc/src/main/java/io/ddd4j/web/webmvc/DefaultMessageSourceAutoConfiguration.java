/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webmvc.webmvc;

import io.ddd4j.spring.properties.BasePropertySourcePostProcessor;
import io.ddd4j.web.webmvc.config.MessageSourceConfigurationProperties;
import io.ddd4j.web.webmvc.error.I18nResourceBasenameHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.biz.context.NestedMessageSource;
import org.springframework.biz.context.support.MultiResourceBundleMessageSource;
import org.springframework.biz.context.support.ResourceBasenameHandler;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.CollectionUtils;

import java.time.Duration;

/**
 * MessageSource 与属性源默认配置（Boot 条件装配已迁出至 ddd4j-boot 轨）。
 */
@Configuration(proxyBeanMethods = false)
public class DefaultMessageSourceAutoConfiguration {

    /**
     * 属性源后置处理器。
     */
    @Bean
    public BasePropertySourcePostProcessor bladePropertySourcePostProcessor() {
        return new BasePropertySourcePostProcessor();
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
        if (properties.getEncoding() != null) {
            messageSource.setDefaultEncoding(properties.getEncoding().name());
        }
        messageSource.setFallbackToSystemLocale(properties.isFallbackToSystemLocale());
        Duration cacheDuration = properties.getCacheDuration();
        if (cacheDuration != null) {
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

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
package io.ddd4j.spring.i18n;

import io.ddd4j.core.i18n.I18nProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.biz.context.NestedMessageSource;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * Spring 实现的国际化提供者
 * <p>
 * 基于 Spring MessageSource 实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Component
public class SpringI18nProvider implements I18nProvider {

    /**
     * Spring 嵌套消息源
     */
    private final MessageSource messageSource;

    /** 保留旧 spring-biz 构造入口，供已有调用方继续使用。 */
    public SpringI18nProvider(NestedMessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** 使用当前 WebMVC 配置提供的 Easy4J 嵌套消息源。 */
    public SpringI18nProvider(org.springframework.extension.context.NestedMessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** Spring 容器优先接入当前扩展消息源，同时兼容旧 spring-biz Bean。 */
    @Autowired
    public SpringI18nProvider(
            ObjectProvider<org.springframework.extension.context.NestedMessageSource> extensionSources,
            ObjectProvider<NestedMessageSource> legacySources) {
        MessageSource selected = extensionSources.getIfAvailable();
        if (Objects.isNull(selected)) {
            selected = legacySources.getIfAvailable();
        }
        if (Objects.isNull(selected)) {
            throw new IllegalStateException("No NestedMessageSource bean available");
        }
        this.messageSource = selected;
    }

    @Override
    public String getMessage(String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, locale);
    }
}

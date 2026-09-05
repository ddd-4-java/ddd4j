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
package io.ddd4j.sample.quarkus.spi;

import io.ddd4j.core.i18n.I18nProvider;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * 国际化提供者：直接复用 {@link I18nProvider#DEFAULT}（基于 ResourceBundle）。
 *
 * <p>作为 CDI {@link ApplicationScoped} Bean，由 ddd4j-runtime-quarkus 启动期自动注入。
 *
 * <p>真实项目可重写为基于数据库 / 配置中心的实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class DefaultI18nProvider implements I18nProvider {

    @Override
    public String getMessage(String key, Object... args) {
        // 委托给 ddd4j-core 自带的 DEFAULT 实现（基于 i18n/messages ResourceBundle）
        return I18nProvider.DEFAULT.getMessage(key, args);
    }
}
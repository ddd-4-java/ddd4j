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
package io.ddd4j.quarkus;

import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.I18nKit;
import io.ddd4j.core.util.SubjectKit;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Quarkus 启动初始化器：在应用启动时注册 I18nProvider 和 SubjectProvider。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@ApplicationScoped
public class DddInitializer {

    /**
     * 国际化提供者
     */
    @Inject
    I18nProvider i18nProvider;

    /**
     * Subject 提供者
     */
    @Inject
    SubjectProvider subjectProvider;
    /**
     * 框架扩展是否已完成运行时初始化。
     */
    private boolean started;
    /**
     * 安装框架主体提供器前已存在的全局提供器。
     */
    private SubjectProvider previousSubjectProvider;
    /**
     * 安装框架国际化组件前已存在的消息提供器。
     */
    private I18nProvider previousI18nProvider;

    /**
     * 把 CDI 应用的主体及国际化组件安装到共享入口。
     *
     * @param event 当前框架生命周期或容器启动事件
     */
    synchronized void onStart(@Observes StartupEvent event) {
        if (started) {
            return;
        }
        previousI18nProvider = I18nKit.getProvider();
        I18nKit.register(i18nProvider);
        log.info("Registered I18nProvider for Quarkus CDI");

        previousSubjectProvider = SubjectKit.subjectProvider;
        SubjectKit.register(subjectProvider);
        log.info("Registered SubjectProvider for Quarkus CDI");
        started = true;
    }

    /**
     * 在 Quarkus 停止时撤销当前初始化器拥有的注册。
     *
     * @param event 当前框架生命周期或容器启动事件
     */
    synchronized void onStop(@Observes ShutdownEvent event) {
        restoreProviders();
    }

    /**
     * 恢复当前初始化器替换前的主体和国际化提供器。
     */
    @PreDestroy
    synchronized void restoreProviders() {
        if (!started) {
            return;
        }
        started = false;
        if (Objects.equals(SubjectKit.subjectProvider, subjectProvider)) {
            SubjectKit.register(previousSubjectProvider);
        }
        if (Objects.equals(I18nKit.getProvider(), i18nProvider)) {
            I18nKit.register(previousI18nProvider);
        }
        previousSubjectProvider = null;
        previousI18nProvider = null;
    }
}

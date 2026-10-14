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
package io.ddd4j.runtime.testkit;

import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.ReadinessResult;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;

import java.util.List;
import java.util.Map;

/**
 * Runtime 契约测试使用的框架无关服务夹具。
 */
public final class RuntimeFixtures {

    private final DomainEventPublisher publisher = new DomainEventPublisher() {
        @Override
        public <ID extends EntityId> void publish(DomainEvent<ID> event) {
            // Contract tests only verify registration and lifecycle.
        }
    };
    private final SubjectProvider subjectProvider = new InMemorySubjectProvider(new InMemorySubject(event -> {
    }));
    private final I18nProvider i18nProvider = I18nProvider.DEFAULT;
    private final CommandBus commandBus = new DefaultCommandBus(List.of());

    /** @return 用于契约测试的领域事件发布器 */
    public DomainEventPublisher publisher() {
        return publisher;
    }

    /** @return 用于契约测试的主体提供者 */
    public SubjectProvider subjectProvider() {
        return subjectProvider;
    }

    /** @return 用于契约测试的国际化提供者 */
    public I18nProvider i18nProvider() {
        return i18nProvider;
    }

    /** @return 用于契约测试的命令总线 */
    public CommandBus commandBus() {
        return commandBus;
    }

    /** @return 用于契约测试的就绪检查项 */
    public List<ReadinessContributor> readinessContributors() {
        return List.of(() -> ReadinessResult.ready("runtime-fixture"));
    }

    /** @return 注册的服务键与接口类型 */
    public Map<String, Class<?>> services() {
        return Map.of(
                SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class,
                SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class,
                SpiKeys.I18N_PROVIDER, I18nProvider.class,
                SpiKeys.COMMAND_BUS, CommandBus.class);
    }
}

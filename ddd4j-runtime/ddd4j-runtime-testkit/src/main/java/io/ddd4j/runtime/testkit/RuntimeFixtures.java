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
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.ReadinessResult;
import io.ddd4j.core.subject.SubjectProvider;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    private final CommandBus commandBus = new DefaultCommandBus(Collections.emptyList());

    public DomainEventPublisher publisher() {
        return publisher;
    }

    public SubjectProvider subjectProvider() {
        return subjectProvider;
    }

    public I18nProvider i18nProvider() {
        return i18nProvider;
    }

    public CommandBus commandBus() {
        return commandBus;
    }

    public List<ReadinessContributor> readinessContributors() {
        return Collections.singletonList(() -> ReadinessResult.ready("runtime-fixture"));
    }

    public Map<String, Class<?>> services() {
        Map<String, Class<?>> map = new LinkedHashMap<>();
        map.put(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);
        map.put(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class);
        map.put(SpiKeys.I18N_PROVIDER, I18nProvider.class);
        map.put(SpiKeys.COMMAND_BUS, CommandBus.class);
        return Collections.unmodifiableMap(map);
    }
}

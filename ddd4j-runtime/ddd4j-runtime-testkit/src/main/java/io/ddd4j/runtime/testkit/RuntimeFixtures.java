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

    /** \u5c06\u9886\u57df\u4e8b\u4ef6\u4ea4\u7ed9\u8fd0\u884c\u65f6\u5206\u53d1\u7684\u53d1\u5e03\u5668\u3002 */
    private final DomainEventPublisher publisher = new DomainEventPublisher() {
        @Override
        public <ID extends EntityId> void publish(DomainEvent<ID> event) {
            // Contract tests only verify registration and lifecycle.
        }
    };
    /** \u6839\u636e\u5f53\u524d\u8c03\u7528\u8303\u56f4\u63d0\u4f9b\u8ba4\u8bc1\u4e3b\u4f53\u7684\u7ec4\u4ef6\u3002 */
    private final SubjectProvider subjectProvider = new InMemorySubjectProvider(new InMemorySubject(event -> {
    }));
    /** \u5f53\u524d\u8fd0\u884c\u6846\u67b6\u6ce8\u518c\u7684\u56fd\u9645\u5316\u6d88\u606f\u63d0\u4f9b\u5668\u3002 */
    private final I18nProvider i18nProvider = I18nProvider.DEFAULT;
    /** \u6839\u636e\u547d\u4ee4\u7c7b\u578b\u5206\u53d1\u5199\u64cd\u4f5c\u7684\u547d\u4ee4\u603b\u7ebf\u3002 */
    private final CommandBus commandBus = new DefaultCommandBus(List.of());

    /** @return \u7528\u4e8e\u5951\u7ea6\u6d4b\u8bd5\u7684\u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668 */
    public DomainEventPublisher publisher() {
        return publisher;
    }

    /** @return \u7528\u4e8e\u5951\u7ea6\u6d4b\u8bd5\u7684\u4e3b\u4f53\u63d0\u4f9b\u8005 */
    public SubjectProvider subjectProvider() {
        return subjectProvider;
    }

    /** @return \u7528\u4e8e\u5951\u7ea6\u6d4b\u8bd5\u7684\u56fd\u9645\u5316\u63d0\u4f9b\u8005 */
    public I18nProvider i18nProvider() {
        return i18nProvider;
    }

    /** @return \u7528\u4e8e\u5951\u7ea6\u6d4b\u8bd5\u7684\u547d\u4ee4\u603b\u7ebf */
    public CommandBus commandBus() {
        return commandBus;
    }

    /** @return \u7528\u4e8e\u5951\u7ea6\u6d4b\u8bd5\u7684\u5c31\u7eea\u68c0\u67e5\u9879 */
    public List<ReadinessContributor> readinessContributors() {
        return List.of(() -> ReadinessResult.ready("runtime-fixture"));
    }

    /** @return \u6ce8\u518c\u7684\u670d\u52a1\u952e\u4e0e\u63a5\u53e3\u7c7b\u578b */
    public Map<String, Class<?>> services() {
        return Map.of(
                SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class,
                SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class,
                SpiKeys.I18N_PROVIDER, I18nProvider.class,
                SpiKeys.COMMAND_BUS, CommandBus.class);
    }
}

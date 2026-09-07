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
package io.ddd4j.spring.context;

import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.runtime.testkit.AbstractRuntimeContractTest;
import io.ddd4j.runtime.testkit.RuntimeContract;
import io.ddd4j.runtime.testkit.RuntimeContractAdapter;
import io.ddd4j.runtime.testkit.RuntimeFixtures;
import io.ddd4j.spring.config.SpringCoreConfig;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SpringRuntimeContractTest extends AbstractRuntimeContractTest {

    @Override
    protected RuntimeContract createRuntime() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(SpringCoreConfig.class);
        context.registerBean(SpringContextBridge.class);
        context.registerBean(DomainEventPublisher.class, fixtures::publisher);
        context.registerBean(SubjectProvider.class, fixtures::subjectProvider);
        context.registerBean(I18nProvider.class, fixtures::i18nProvider);
        context.registerBean(CommandBus.class, fixtures::commandBus);
        context.registerBean(ReadinessContributor.class, () -> fixtures.readinessContributors().get(0));
        return new RuntimeContractAdapter(() -> {
            if (!context.isActive()) {
                context.refresh();
            }
        }, context::close, fixtures.services(),
                () -> context.getBean(RuntimeReadinessRegistry.class).readiness());
    }
}

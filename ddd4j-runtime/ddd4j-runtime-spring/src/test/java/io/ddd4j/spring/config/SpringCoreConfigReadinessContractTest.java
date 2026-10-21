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
package io.ddd4j.spring.config;

import io.ddd4j.core.health.ReadinessContributor;
import io.ddd4j.core.health.ReadinessResult;
import io.ddd4j.core.health.RuntimeReadinessRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.Order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三线共同的 Spring 就绪检查项装配契约。 */
class SpringCoreConfigReadinessContractTest {

    @Test
    void registersContributorsInSpringOrder() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(SpringCoreConfig.class, LateContributor.class, EarlyContributor.class);
            context.refresh();

            RuntimeReadinessRegistry registry = context.getBean(RuntimeReadinessRegistry.class);
            assertEquals(2, registry.contributors().size());
            assertSame(context.getBean(EarlyContributor.class), registry.contributors().get(0));
            assertSame(context.getBean(LateContributor.class), registry.contributors().get(1));
            assertTrue(registry.readiness().ready());
        }
    }

    @Order(2)
    static final class LateContributor implements ReadinessContributor {
        @Override
        public ReadinessResult check() {
            return ReadinessResult.ready("late");
        }
    }

    @Order(1)
    static final class EarlyContributor implements ReadinessContributor {
        @Override
        public ReadinessResult check() {
            return ReadinessResult.ready("early");
        }
    }
}

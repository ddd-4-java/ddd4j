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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三线共同的 Spring 就绪检查项装配契约。
 */
class SpringCoreConfigReadinessContractTest {

    /** \u9a8c\u8bc1<code>registry.contributors().size()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>registry.contributors().get(0)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>context.getBean(EarlyContributor.class)</code>\uff0c<code>registry.readiness().ready()</code>\u4e3a\u771f\u3002 */
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

    /** \u5411\u8fd0\u884c\u65f6\u6d4b\u8bd5\u63d0\u4f9b\u53ef\u63a7\u7684\u5c31\u7eea\u72b6\u6001\u3002 */
    @Order(2)
    static final class LateContributor implements ReadinessContributor {
        /** {@inheritDoc} */
        @Override
        public ReadinessResult check() {
            return ReadinessResult.ready("late");
        }
    }

    /** \u5411\u8fd0\u884c\u65f6\u6d4b\u8bd5\u63d0\u4f9b\u53ef\u63a7\u7684\u5c31\u7eea\u72b6\u6001\u3002 */
    @Order(1)
    static final class EarlyContributor implements ReadinessContributor {
        /** {@inheritDoc} */
        @Override
        public ReadinessResult check() {
            return ReadinessResult.ready("early");
        }
    }
}

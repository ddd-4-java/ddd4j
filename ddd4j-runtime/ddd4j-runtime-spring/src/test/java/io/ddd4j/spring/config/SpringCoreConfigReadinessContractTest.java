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

    /**
     * 验证<code>registry.contributors().size()</code> 等于 <code>2</code>，<code>registry.contributors().get(0)</code> 与…为同一实例 <code>context.getBean(EarlyContributor.class)</code>，<code>registry.readiness().ready()</code>为真。
     */
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

    /**
     * 向运行时测试提供可控的就绪状态。
     */
    @Order(2)
    static final class LateContributor implements ReadinessContributor {
        /**
         * 执行当前实现对应的检查。
         *
         * <p>测试夹具会调用 <code>ReadinessResult.ready("late")</code>。</p>
         *
         * @return 实际返回<code>ReadinessResult.ready("late")</code>
         */
        @Override
        public ReadinessResult check() {
            return ReadinessResult.ready("late");
        }
    }

    /**
     * 向运行时测试提供可控的就绪状态。
     */
    @Order(1)
    static final class EarlyContributor implements ReadinessContributor {
        /**
         * 执行当前实现对应的检查。
         *
         * <p>测试夹具会调用 <code>ReadinessResult.ready("early")</code>。</p>
         *
         * @return 实际返回<code>ReadinessResult.ready("early")</code>
         */
        @Override
        public ReadinessResult check() {
            return ReadinessResult.ready("early");
        }
    }
}

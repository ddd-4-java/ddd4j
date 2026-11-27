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
package io.ddd4j.dropwizard;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.readmodel.DefaultProjectionService;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.NoopEventChunkReader;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.dropwizard.cqrs.DropwizardProjectionViewManager;
import io.ddd4j.runtime.testkit.AbstractRuntimeContractTest;
import io.ddd4j.runtime.testkit.RuntimeContract;
import io.ddd4j.runtime.testkit.RuntimeContractAdapter;
import io.ddd4j.runtime.testkit.RuntimeFixtures;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dropwizard 生命周期和就绪状态的运行时契约。
 */
class Ddd4jDropwizardRuntimeTest extends AbstractRuntimeContractTest {

    /** \u9a8c\u8bc1<code>runtime.readiness().contributors().isEmpty()</code>\u4e3a\u771f\u3002 */
    @Test
    void defaultConstructorStartsWithNoReadinessContributors() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Ddd4jDropwizardRuntime runtime = new Ddd4jDropwizardRuntime(fixtures.publisher(),
                fixtures.subjectProvider(), fixtures.i18nProvider(), fixtures.commandBus());

        assertTrue(runtime.readiness().contributors().isEmpty());
        runtime.close();
    }


    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LinkageError.class</code>\uff0c<code>BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previous</code>\uff0c<code>cleanup</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>failure</code>\u3002 */
    @Test
    void linkageFailureRestoresSpiAndKeepsOriginalEvenIfCleanupRepeatsIt() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        LinkageError failure = new LinkageError("projection linkage failure");
        AtomicInteger stopCalls = new AtomicInteger();
        ProjectionRunner<Object> runner = new ProjectionRunner<>(
                new DefaultProjectionService(new InMemoryProjectionPositionRepository()), new NoopEventChunkReader<>());
        DropwizardProjectionViewManager manager = new DropwizardProjectionViewManager(
                (name, cron, task) -> {
                    throw new AssertionError("no task expected");
                }, runner, Collections.emptyList()) {
            @Override
            public void start() {
                throw failure;
            }

            @Override
            public void stop() {
                if (stopCalls.incrementAndGet() == 1) {
                    throw failure;
                }
            }
        };
        Object previous = BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class);
        Ddd4jDropwizardRuntime runtime = new Ddd4jDropwizardRuntime(fixtures.publisher(),
                fixtures.subjectProvider(), fixtures.i18nProvider(), fixtures.commandBus(),
                fixtures.readinessContributors(), manager);
        try {
            assertSame(failure, assertThrows(LinkageError.class, runtime::start));
            assertSame(previous, BaseContext.get(SpiKeys.COMMAND_BUS, CommandBus.class));
            assertEquals(1, stopCalls.get());
            assertEquals(0, failure.getSuppressed().length);
        } finally {
            try {
                runtime.close();
            } catch (LinkageError cleanup) {
                assertSame(failure, cleanup);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected RuntimeContract createRuntime() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Ddd4jDropwizardRuntime runtime = new Ddd4jDropwizardRuntime(fixtures.publisher(), fixtures.subjectProvider(),
                fixtures.i18nProvider(), fixtures.commandBus(), fixtures.readinessContributors());
        return new RuntimeContractAdapter(runtime::start, runtime::close, fixtures.services(),
                () -> runtime.readiness().readiness());
    }
}

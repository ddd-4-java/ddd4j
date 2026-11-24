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
package io.ddd4j.micronaut;

import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.runtime.testkit.AbstractRuntimeContractTest;
import io.ddd4j.runtime.testkit.RuntimeContract;
import io.ddd4j.runtime.testkit.RuntimeContractAdapter;
import io.ddd4j.runtime.testkit.RuntimeFixtures;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Micronaut 生命周期和就绪状态的运行时契约。 */
class Ddd4jMicronautRuntimeTest extends AbstractRuntimeContractTest {

    @Test
    void defaultConstructorStartsWithNoReadinessContributors() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Ddd4jMicronautRuntime runtime = new Ddd4jMicronautRuntime(fixtures.publisher(),
                fixtures.subjectProvider(), fixtures.i18nProvider(), fixtures.commandBus());

        assertTrue(runtime.readiness().contributors().isEmpty());
        runtime.close();
    }

    @Test
    void startupErrorStopsPartiallyStartedViewAndPreservesCleanupFailure() {
        LinkageError startup = new LinkageError("projection start failed");
        IllegalStateException cleanup = new IllegalStateException("projection stop failed");
        FailingViewManager manager = new FailingViewManager(() -> { throw startup; }, () -> { throw cleanup; });

        LinkageError failure = assertThrows(LinkageError.class, runtimeWithView(manager)::start);

        assertSame(startup, failure);
        assertFalse(manager.isRunning());
        assertEquals(1, manager.stopCalls);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanup, failure.getSuppressed()[0]);
    }

    @Test
    void startupAndCleanupThrowingSameExceptionPreservesItsIdentity() {
        IllegalStateException shared = new IllegalStateException("projection failed");
        FailingViewManager manager = new FailingViewManager(() -> { throw shared; }, () -> { throw shared; });

        IllegalStateException failure = assertThrows(IllegalStateException.class, runtimeWithView(manager)::start);

        assertSame(shared, failure);
        assertFalse(manager.isRunning());
        assertEquals(1, manager.stopCalls);
        assertEquals(0, failure.getSuppressed().length);
    }

    private Ddd4jMicronautRuntime runtimeWithView(ViewManager manager) {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        return new Ddd4jMicronautRuntime(fixtures.publisher(), fixtures.subjectProvider(), fixtures.i18nProvider(),
                fixtures.commandBus(), Collections.emptyList(), manager);
    }

    private static final class FailingViewManager implements ViewManager {
        private final Runnable startFailure;
        private final Runnable stopFailure;
        private boolean running;
        private int stopCalls;

        private FailingViewManager(Runnable startFailure, Runnable stopFailure) {
            this.startFailure = startFailure;
            this.stopFailure = stopFailure;
        }

        @Override
        public void start() {
            running = true;
            startFailure.run();
        }

        @Override
        public void stop() {
            running = false;
            stopCalls++;
            stopFailure.run();
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public void triggerOnce() {
        }
    }

    @Override
    protected RuntimeContract createRuntime() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Ddd4jMicronautRuntime runtime = new Ddd4jMicronautRuntime(fixtures.publisher(), fixtures.subjectProvider(),
                fixtures.i18nProvider(), fixtures.commandBus(), fixtures.readinessContributors());
        return new RuntimeContractAdapter(runtime::start, runtime::close, fixtures.services(),
                () -> runtime.readiness().readiness());
    }
}

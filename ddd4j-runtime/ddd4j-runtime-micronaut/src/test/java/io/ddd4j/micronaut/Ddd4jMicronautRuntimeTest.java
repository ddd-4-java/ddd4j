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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Micronaut 生命周期和就绪状态的运行时契约。
 */
class Ddd4jMicronautRuntimeTest extends AbstractRuntimeContractTest {

    /** \u9a8c\u8bc1<code>runtime.readiness().contributors().isEmpty()</code>\u4e3a\u771f\u3002 */
    @Test
    void defaultConstructorStartsWithNoReadinessContributors() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Ddd4jMicronautRuntime runtime = new Ddd4jMicronautRuntime(fixtures.publisher(),
                fixtures.subjectProvider(), fixtures.i18nProvider(), fixtures.commandBus());

        assertTrue(runtime.readiness().contributors().isEmpty());
        runtime.close();
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>LinkageError.class</code>\uff0c<code>failure</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>startup</code>\uff0c<code>failure.getSuppressed()[0]</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>cleanup</code>\u3002 */
    @Test
    void startupErrorStopsPartiallyStartedViewAndPreservesCleanupFailure() {
        LinkageError startup = new LinkageError("projection start failed");
        IllegalStateException cleanup = new IllegalStateException("projection stop failed");
        FailingViewManager manager = new FailingViewManager(() -> {
            throw startup;
        }, () -> {
            throw cleanup;
        });

        LinkageError failure = assertThrows(LinkageError.class, runtimeWithView(manager)::start);

        assertSame(startup, failure);
        assertFalse(manager.isRunning());
        assertEquals(1, manager.stopCalls);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanup, failure.getSuppressed()[0]);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>shared</code>\uff0c<code>failure.getSuppressed().length</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void startupAndCleanupThrowingSameExceptionPreservesItsIdentity() {
        IllegalStateException shared = new IllegalStateException("projection failed");
        FailingViewManager manager = new FailingViewManager(() -> {
            throw shared;
        }, () -> {
            throw shared;
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class, runtimeWithView(manager)::start);

        assertSame(shared, failure);
        assertFalse(manager.isRunning());
        assertEquals(1, manager.stopCalls);
        assertEquals(0, failure.getSuppressed().length);
    }

    /**
     * \u8c03\u7528 <code>fixtures.publisher()</code>\u3001<code>fixtures.subjectProvider()</code>\u3001<code>fixtures.i18nProvider()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param manager \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>ViewManager</code> \u8f93\u5165
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>Ddd4jMicronautRuntime</code> \u7ed3\u679c
     */
    private Ddd4jMicronautRuntime runtimeWithView(ViewManager manager) {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        return new Ddd4jMicronautRuntime(fixtures.publisher(), fixtures.subjectProvider(), fixtures.i18nProvider(),
                fixtures.commandBus(), Collections.emptyList(), manager);
    }

    /** {@inheritDoc} */
    @Override
    protected RuntimeContract createRuntime() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Ddd4jMicronautRuntime runtime = new Ddd4jMicronautRuntime(fixtures.publisher(), fixtures.subjectProvider(),
                fixtures.i18nProvider(), fixtures.commandBus(), fixtures.readinessContributors());
        return new RuntimeContractAdapter(runtime::start, runtime::close, fixtures.services(),
                () -> runtime.readiness().readiness());
    }

    /** \u5b9e\u73b0 <code>ViewManager</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>start()</code>\u3001<code>stop()</code>\u3001<code>isRunning()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    private static final class FailingViewManager implements ViewManager {
        /** \u6a21\u62df\u6846\u67b6\u6216\u9002\u914d\u5668\u542f\u52a8\u5931\u8d25\u65f6\u4f7f\u7528\u7684\u5f02\u5e38\u3002 */
        private final Runnable startFailure;
        /** \u6a21\u62df\u6846\u67b6\u6216\u9002\u914d\u5668\u505c\u6b62\u5931\u8d25\u65f6\u4f7f\u7528\u7684\u5f02\u5e38\u3002 */
        private final Runnable stopFailure;
        /** \u4fdd\u5b58\u6216\u6a21\u62df\u88ab\u6d4b\u670d\u52a1\u7684\u5f53\u524d\u8fd0\u884c\u72b6\u6001\u3002 */
        private boolean running;
        /** \u6d4b\u8bd5\u89c2\u5bdf\u5230\u7684\u6846\u67b6\u505c\u6b62\u8c03\u7528\u6b21\u6570\u3002 */
        private int stopCalls;

        /**
         * \u8bbe\u7f6e <code>startFailure</code>\u3001<code>stopFailure</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>FailingViewManager</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param startFailure \u7528\u4e8e\u521d\u59cb\u5316 <code>FailingViewManager</code> \u7684 <code>Runnable</code> \u503c
         * @param stopFailure \u7528\u4e8e\u521d\u59cb\u5316 <code>FailingViewManager</code> \u7684 <code>Runnable</code> \u503c
         */
        private FailingViewManager(Runnable startFailure, Runnable stopFailure) {
            this.startFailure = startFailure;
            this.stopFailure = stopFailure;
        }

        /** {@inheritDoc} */
        @Override
        public void start() {
            running = true;
            startFailure.run();
        }

        /** {@inheritDoc} */
        @Override
        public void stop() {
            running = false;
            stopCalls++;
            stopFailure.run();
        }

        /** {@inheritDoc} */
        @Override
        public boolean isRunning() {
            return running;
        }

        /** {@inheritDoc} */
        @Override
        public void triggerOnce() {
        }
    }
}

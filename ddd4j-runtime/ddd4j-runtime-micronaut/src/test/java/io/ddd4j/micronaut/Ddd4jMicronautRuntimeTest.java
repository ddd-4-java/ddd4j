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

    /**
     * 验证<code>runtime.readiness().contributors().isEmpty()</code>为真。
     */
    @Test
    void defaultConstructorStartsWithNoReadinessContributors() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Ddd4jMicronautRuntime runtime = new Ddd4jMicronautRuntime(fixtures.publisher(),
                fixtures.subjectProvider(), fixtures.i18nProvider(), fixtures.commandBus());

        assertTrue(runtime.readiness().contributors().isEmpty());
        runtime.close();
    }

    /**
     * 验证被测调用 抛出 <code>LinkageError.class</code>，<code>failure</code> 与…为同一实例 <code>startup</code>，<code>failure.getSuppressed()[0]</code> 与…为同一实例 <code>cleanup</code>。
     */
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

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>failure</code> 与…为同一实例 <code>shared</code>，<code>failure.getSuppressed().length</code> 等于 <code>0</code>。
     */
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
     * 调用 <code>fixtures.publisher()</code>、<code>fixtures.subjectProvider()</code>、<code>fixtures.i18nProvider()</code> 执行当前夹具操作。
     *
     * @param manager 用于构造本次返回结果的 <code>ViewManager</code> 输入
     * @return 提供给后续契约断言的 <code>Ddd4jMicronautRuntime</code> 结果
     */
    private Ddd4jMicronautRuntime runtimeWithView(ViewManager manager) {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        return new Ddd4jMicronautRuntime(fixtures.publisher(), fixtures.subjectProvider(), fixtures.i18nProvider(),
                fixtures.commandBus(), Collections.emptyList(), manager);
    }

    /**
     * 为契约测试构造当前框架的运行实例。
     *
     * <p>测试夹具会调用 <code>fixtures.publisher()</code>、<code>fixtures.subjectProvider()</code>。</p>
     *
     * @return 待验证的运行时适配器
     */
    @Override
    protected RuntimeContract createRuntime() {
        RuntimeFixtures fixtures = new RuntimeFixtures();
        Ddd4jMicronautRuntime runtime = new Ddd4jMicronautRuntime(fixtures.publisher(), fixtures.subjectProvider(),
                fixtures.i18nProvider(), fixtures.commandBus(), fixtures.readinessContributors());
        return new RuntimeContractAdapter(runtime::start, runtime::close, fixtures.services(),
                () -> runtime.readiness().readiness());
    }

    /**
     * 实现 <code>ViewManager</code>，供测试调用 <code>start()</code>、<code>stop()</code>、<code>isRunning()</code> 并观察结果。
     */
    private static final class FailingViewManager implements ViewManager {
        /**
         * 模拟框架或适配器启动失败时使用的异常。
         */
        private final Runnable startFailure;
        /**
         * 模拟框架或适配器停止失败时使用的异常。
         */
        private final Runnable stopFailure;
        /**
         * 保存或模拟被测服务的当前运行状态。
         */
        private boolean running;
        /**
         * 测试观察到的框架停止调用次数。
         */
        private int stopCalls;

        /**
         * 设置 <code>startFailure</code>、<code>stopFailure</code>，用于构造 <code>FailingViewManager</code> 测试实例。
         *
         * @param startFailure 用于初始化 <code>FailingViewManager</code> 的 <code>Runnable</code> 值
         * @param stopFailure  用于初始化 <code>FailingViewManager</code> 的 <code>Runnable</code> 值
         */
        private FailingViewManager(Runnable startFailure, Runnable stopFailure) {
            this.startFailure = startFailure;
            this.stopFailure = stopFailure;
        }

        /**
         * 启动当前组件并准备其运行资源。
         *
         * <p>测试夹具会调用 <code>startFailure.run()</code>。</p>
         */
        @Override
        public void start() {
            running = true;
            startFailure.run();
        }

        /**
         * 停止当前组件并清理已登记的运行资源。
         *
         * <p>测试夹具会调用 <code>stopFailure.run()</code>。</p>
         */
        @Override
        public void stop() {
            running = false;
            stopCalls++;
            stopFailure.run();
        }

        /**
         * 检查当前组件是否仍处于运行状态。
         *
         * @return 实际返回<code>running</code>，作为本次判断结果
         */
        @Override
        public boolean isRunning() {
            return running;
        }

        /**
         * 提供测试替身回调，调用时保持夹具状态。
         */
        @Override
        public void triggerOnce() {
        }
    }
}

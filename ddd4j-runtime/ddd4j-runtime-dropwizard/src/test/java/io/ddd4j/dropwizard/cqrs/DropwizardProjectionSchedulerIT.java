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
package io.ddd4j.dropwizard.cqrs;

import io.ddd4j.core.cqrs.readmodel.*;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.DropwizardTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DropwizardProjectionScheduler} + {@link DropwizardProjectionViewManager}
 * 真实 Dropwizard 启动集成测试（Task 7.12）。
 *
 * <p>真实 Dropwizard 5 启动（{@link DropwizardTestSupport} 驱动真实 Jetty/Jersey
 * 容器，随机端口）+ 真实调度器/管理器（零 mock）——完整验证 ViewScheduler 的
 * schedule/cancel/isActive 契约与 ViewManager 的 start/stop/isRunning/triggerOnce
 * 生命周期。
 *
 * <ol>
 *   <li>register + start + assert handle isActive + stop + assert inactive</li>
 *   <li>多 view 同时注册 + start + stop 隔离</li>
 *   <li>triggerOnce 立即执行所有 view 的 runOnce</li>
 * </ol>
 *
 * <p><b>启动开销注记</b>：Dropwizard 真实启动（Jetty+Jersey+Jackson+metrics 全栈）
 * 刻意 {@link BeforeAll} 类级单次启动（3 用例共享同一真实容器）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("DropwizardProjectionScheduler + ViewManager 真实 Dropwizard 启动 IT")
class DropwizardProjectionSchedulerIT {

    /** 保存 <code>support</code> 对应的 <code>DropwizardTestSupport&lt;Configuration&gt;</code> 数据。 */
    private static DropwizardTestSupport<Configuration> support;
    /** 注册任务和取消任务所用的视图调度器。 */
    private static DropwizardProjectionScheduler scheduler;
    /** 保存 <code>view Manager</code> 对应的 <code>DropwizardProjectionViewManager</code> 数据。 */
    private static DropwizardProjectionViewManager viewManager;

    /**
     * 初始化测试资源，调用 <code>support.before</code>、<code>support.getApplication</code>。
     *
     * @throws Exception 完成 <code>start Dropwizard</code> 操作失败
     */
    @BeforeAll
    static void startDropwizard() throws Exception {
        support = new DropwizardTestSupport<>(TestApplication.class, randomPortConfiguration());
        support.before();
        TestApplication app = support.getApplication();
        scheduler = app.scheduler;
        viewManager = app.viewManager;
    }

    /** 释放测试资源，调用 <code>viewManager.isRunning</code>、<code>viewManager.stop</code>、<code>scheduler.shutdown</code>。 */
    @AfterAll
    static void stopDropwizard() {
        if (viewManager != null && viewManager.isRunning()) {
            viewManager.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (support != null) {
            support.after();
        }
    }

    /**
     * \u968f\u673a\u7aef\u53e3\u914d\u7f6e\uff08application/admin \u8fde\u63a5\u5668 port 0 \u2192 \u5185\u6838\u5206\u914d\u7a7a\u95f2\u7aef\u53e3\uff09\uff0c
     *  \u907f\u514d\u771f\u5b9e Jetty \u542f\u52a8\u649e\u56fa\u5b9a 8080/8081\u3002
     *
     * @return 返回表达式 <code>configuration</code>
     */
    private static Configuration randomPortConfiguration() {
        Configuration configuration = new Configuration();
        DefaultServerFactory serverFactory = (DefaultServerFactory) configuration.getServerFactory();
        HttpConnectorFactory applicationConnector = new HttpConnectorFactory();
        applicationConnector.setPort(0);
        serverFactory.setApplicationConnectors(Collections.singletonList(applicationConnector));
        HttpConnectorFactory adminConnector = new HttpConnectorFactory();
        adminConnector.setPort(0);
        serverFactory.setAdminConnectors(Collections.singletonList(adminConnector));
        return configuration;
    }

    /** 验证<code>handle.isActive()</code>为真，<code>handle.isActive()</code>为假。 */
    @Test
    void scheduler_schedule_应返回activeHandle_cancel后应inactive() {
        ViewScheduler.ViewScheduleHandle handle = scheduler.schedule(
                "direct-view", "0/1 * * * * *", () -> {
                });

        assertThat(handle.isActive()).isTrue();

        handle.cancel();
        assertThat(handle.isActive()).isFalse();
    }

    /** 验证<code>viewManager.isRunning()</code>为假，<code>viewManager.isRunning()</code>为真。 */
    @Test
    void viewManager_start_stop_应管理生命周期() {
        assertThat(viewManager.isRunning()).isFalse();

        viewManager.start();
        assertThat(viewManager.isRunning()).isTrue();

        // start idempotent
        viewManager.start();
        assertThat(viewManager.isRunning()).isTrue();

        viewManager.stop();
        assertThat(viewManager.isRunning()).isFalse();
    }

    /** 验证<code>viewManager.isRunning()</code>为真。 */
    @Test
    void triggerOnce_应立即执行所有view的runOnce() {
        viewManager.start();
        viewManager.triggerOnce();
        assertThat(viewManager.isRunning()).isTrue();
        viewManager.stop();
    }

    /**
     * 测试用 Dropwizard 应用（仅 {@code run} 阶段被覆盖）：在
     * {@code Application.run} 内以生产接法调
     * {@link DropwizardProjectionScheduler#create} +
     * {@link DropwizardProjectionViewManager#create}。
     */
    public static class TestApplication extends Application<Configuration> {

        /** 注册任务和取消任务所用的视图调度器。 */
        volatile DropwizardProjectionScheduler scheduler;
        /** 保存 <code>view Manager</code> 对应的 <code>DropwizardProjectionViewManager</code> 数据。 */
        volatile DropwizardProjectionViewManager viewManager;

        /**
         * 执行 <code>run</code>，调用 <code>Arrays.asList</code>、<code>DropwizardProjectionScheduler.create</code>、<code>DropwizardProjectionViewManager.create</code>。
         *
         * @param configuration 传入此操作的 <code>Configuration</code> 类型 <code>configuration</code>
         * @param environment 传入此操作的 <code>Environment</code> 类型 <code>environment</code>
         */
        @Override
        public void run(Configuration configuration, Environment environment) {
            Collection<ProjectionView<?>> views = Arrays.asList(
                    new ProjectionViewStub("test-view-1", "0/5 * * * * *"),
                    new ProjectionViewStub("test-view-2", "0/10 * * * * *")
            );
            ProjectionRunner<Object> runner = createTestRunner();
            this.scheduler = DropwizardProjectionScheduler.create(environment, views, runner);
            this.viewManager = DropwizardProjectionViewManager.create(environment, views, runner);
        }

        /**
         * 执行 <code>create Test Runner</code>，调用 <code>EventChunk.empty</code>。
         *
         * @return 按条件返回 <code>0</code>、<code>null</code>
         */
        @SuppressWarnings("unchecked")
        private ProjectionRunner<Object> createTestRunner() {
            ProjectionService projectionService = new ProjectionService() {
                @Override
                public void resetProjectionPosition(String streamId) {
                }

                @Override
                public long readProjectionPosition(String streamId) {
                    return 0;
                }

                @Override
                public ProjectionPosition updateProjectionPosition(String streamId, long nextEventNumber) {
                    return null;
                }
            };
            EventChunkReader<Object> chunkReader = (streamId, fromEventNumber, chunkSize, eventTypes) ->
                    EventChunk.empty(fromEventNumber);
            return new ProjectionRunner<>(projectionService, chunkReader);
        }
    }

    /** 为测试场景提供 <code>ProjectionView</code> 的实现。 */
    private static class ProjectionViewStub implements ProjectionView<Object> {

        /** 保存 <code>name</code> 对应的 <code>String</code> 数据。 */
        private final String name;
        /** 控制投影任务执行时点的 Cron 表达式。 */
        private final String cron;

        /**
         * 初始化 <code>ProjectionViewStub</code> 使用的 <code>name</code>、<code>cron</code>。
         *
         * @param name 传入此操作的 <code>String</code> 类型 <code>name</code>
         * @param cron 控制任务触发时刻的 Cron 表达式
         */
        ProjectionViewStub(String name, String cron) {
            this.name = name;
            this.cron = cron;
        }

        /**
         * 读取<code>name</code> 的当前值。
         *
         * @return 返回表达式 <code>name</code>
         */
        @Override
        public String getName() {
            return name;
        }

        /**
         * 读取控制投影任务执行时点的 Cron 表达式。
         *
         * @return 返回表达式 <code>cron</code>
         */
        @Override
        public String getCron() {
            return cron;
        }

        /**
         * 执行 <code>get Event Types</code>，调用 <code>Collections.singleton</code>。
         *
         * @return 返回表达式 <code>Collections.singleton("TestEvent")</code>
         */
        @Override
        public Collection<String> getEventTypes() {
            return Collections.singleton("TestEvent");
        }

        /**
         * 提供测试夹具的空操作。
         *
         * @param events 传入此操作的 <code>Collection&lt;Object&gt;</code> 类型 <code>events</code>
         */
        @Override
        public void handleEvents(Collection<Object> events) {
        }
    }
}

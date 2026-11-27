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
package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 在真实 ArC 容器中验证应用显式组装的数值调度器及其生命周期。 */
@DisplayName("显式数值投影调度")
@QuarkusTest
@io.quarkus.test.junit.TestProfile(QuarkusNumericProjectionSchedulerIT.Profile.class)
class QuarkusNumericProjectionSchedulerIT {

    /** 只在当前测试应用注册视图及其事件读取器。 */
    public static class Profile implements io.quarkus.test.junit.QuarkusTestProfile {
        /** @return 激活当前投影夹具的构建配置 */
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Collections.singletonMap("ddd4j.test.numeric-projection", "true");
        }
    }

    /** 容器选定的投影执行器。 */
    @Inject
    ProjectionRunner<Object> runner;

    /** 应用注册的视图。 */
    @Inject
    jakarta.enterprise.inject.Instance<ProjectionView<?>> views;

    /** 当前测试拥有的调度器。 */
    private final QuarkusNumericProjectionScheduler scheduler = new QuarkusNumericProjectionScheduler();

    /** 应用手动管理其生命周期的视图管理器。 */
    private QuarkusProjectionViewManager viewManager;

    /** 用真实容器提供的视图和执行器组装调度。 */
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        viewManager = new QuarkusProjectionViewManager(scheduler, runner, views);
    }

    /** 停止视图并释放测试拥有的调度线程。 */
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        viewManager.stop();
        scheduler.shutdown();
    }

    /** 即使容器已启动，手动管理的视图仍等待应用显式启动。 */
    @Test
    void explicitManagerWaitsForStart() {
        assertThat(views.isUnsatisfied()).isFalse();
        assertThat(viewManager.isRunning()).isFalse();
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
        viewManager.stop();
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

    // ======== CDI 测试替身（@Produces 注册为 ArC Bean） ========

    /** 集中保存 <code>TestProducer</code> 的声明，供当前模块使用。 */
    @io.quarkus.arc.properties.IfBuildProperty(name = "ddd4j.test.numeric-projection", stringValue = "true")
    @ApplicationScoped
    static class TestProducer {


        /**
         * 执行 <code>chunk Reader</code>，调用 <code>EventChunk.empty</code>。
         *
         * @return 当前操作得到的 <code>EventChunkReader&lt;Object&gt;</code> 结果
         */
        @Produces
        @ApplicationScoped
        @SuppressWarnings("unchecked")
        EventChunkReader<Object> chunkReader() {
            return (streamId, fromEventNumber, chunkSize, eventTypes) ->
                    EventChunk.empty(fromEventNumber);
        }



        /**
         * 按 <code>test View</code> 的契约计算并返回结果。
         *
         * @return 当前操作得到的 <code>ProjectionView&lt;Object&gt;</code> 结果
         */
        @Produces
        @ApplicationScoped
        ProjectionView<Object> testView() {
            return new ProjectionViewStub("test-view", "0/5 * * * * *");
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
         * 执行 <code>get Event Types</code>，调用 <code>Set.of</code>。
         *
         * @return 返回表达式 <code>Set.of("TestEvent")</code>
         */
        @Override
        public Collection<String> getEventTypes() {
            return Set.of("TestEvent");
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

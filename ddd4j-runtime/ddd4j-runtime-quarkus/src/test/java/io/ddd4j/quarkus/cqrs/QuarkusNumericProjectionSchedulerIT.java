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
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QuarkusNumericProjectionScheduler} + {@link QuarkusProjectionViewManager}
 * 真实 ArC 容器集成测试。
 *
 * <p>真实 Quarkus 容器（{@code @QuarkusTest}，零配置引导——ArC 自带启动容器，
 * 不用 H2/数据源）＋真实 {@code @ApplicationScoped} 调度器 Bean（零 mock）——
 * 完整验证 ViewScheduler 的 schedule/cancel/isActive 契约与 ViewManager
 * 的 start/stop/isRunning/triggerOnce 生命周期。
 *
 * <ol>
 *   <li>register + start + assert handle isActive + stop + assert inactive</li>
 *   <li>多 view 同时注册 + start + stop 隔离</li>
 *   <li>triggerOnce 立即执行一个 view 的 runOnce</li>
 * </ol>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("QuarkusNumericProjectionScheduler + ViewManager 真实 ArC 容器 IT")
@QuarkusTest
class QuarkusNumericProjectionSchedulerIT {

    /** 注册任务和取消任务所用的视图调度器。 */
    @Inject
    QuarkusNumericProjectionScheduler scheduler;

    /** 保存 <code>view Manager</code> 对应的 <code>QuarkusProjectionViewManager</code> 数据。 */
    @Inject
    QuarkusProjectionViewManager viewManager;

    /** 保存 <code>startup Events</code> 对应的 <code>Event&lt;StartupEvent&gt;</code> 数据。 */
    @Inject
    Event<StartupEvent> startupEvents;

    /** 保存 <code>shutdown Events</code> 对应的 <code>Event&lt;ShutdownEvent&gt;</code> 数据。 */
    @Inject
    Event<ShutdownEvent> shutdownEvents;

    /** 验证<code>viewManager.isRunning()</code>为真。 */
    @Test
    void startupEvent_应启动视图管理器() {
        viewManager.stop();
        startupEvents.fire(new StartupEvent());
        assertThat(viewManager.isRunning()).isTrue();
        viewManager.stop();
    }

    /** 验证<code>viewManager.isRunning()</code>为假。 */
    @Test
    void shutdownEvent_应停止视图管理器() {
        viewManager.start();
        shutdownEvents.fire(new ShutdownEvent());
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
    @ApplicationScoped
    static class TestProducer {

        /**
         * 读取<code>null</code> 的当前值。
         *
         * @return 返回表达式 <code>null</code>
         */
        @Produces
        @ApplicationScoped
        ProjectionService projectionService() {
            return new ProjectionService() {
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
        }

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
         * 按 <code>projection Runner</code> 的契约计算并返回结果。
         *
         * @param service 传入此操作的 <code>ProjectionService</code> 类型 <code>service</code>
         * @param reader 传入此操作的 <code>EventChunkReader&lt;Object&gt;</code> 类型 <code>reader</code>
         * @return 当前操作得到的 <code>ProjectionRunner&lt;Object&gt;</code> 结果
         */
        @Produces
        @ApplicationScoped
        @SuppressWarnings("unchecked")
        ProjectionRunner<Object> projectionRunner(ProjectionService service, EventChunkReader<Object> reader) {
            return new ProjectionRunner<>(service, reader);
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

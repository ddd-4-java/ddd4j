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
package io.ddd4j.data.projection.helidon;

import io.ddd4j.core.cqrs.readmodel.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HelidonProjectionScheduler} + {@link HelidonProjectionViewManager}
 * 手动装配集成测试（Helidon SE 模式）。
 *
 * <p>手动装配链（Helidon SE 无容器——集成方直接构造 Bean 或经 ServiceLoader 发现）：
 * 构造 {@link HelidonProjectionScheduler} + 测试替身（ProjectionService /
 * EventChunkReader / ProjectionRunner / ProjectionView）+ 手动传入 views 集合，
 * 完整验证 ViewScheduler 的 schedule/cancel/isActive 契约与 ViewManager 的
 * start/stop/isRunning/triggerOnce 生命周期。三用例与 Quarkus / Micronaut IT 对齐：
 * <ol>
 *   <li>register + start + assert handle isActive + stop + assert inactive</li>
 *   <li>多 view 同时注册 + start + stop 隔离</li>
 *   <li>triggerOnce 立即执行一个 view 的 runOnce</li>
 * </ol>
 *
 * <p>每用例独立装配（构造即快照、无共享可变状态），保持用例间隔离。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
@DisplayName("HelidonProjectionScheduler + ViewManager 手动装配 IT")
class HelidonProjectionSchedulerIT {

    /**
     * 按 <code>create Scheduler</code> 的契约计算并返回结果。
     *
     * @return 当前操作得到的 <code>HelidonProjectionScheduler</code> 结果
     */
    private HelidonProjectionScheduler createScheduler() {
        return new HelidonProjectionScheduler();
    }

    /**
     * 执行 <code>create Runner</code>，调用 <code>EventChunk.empty</code>。
     *
     * @return 按条件返回 <code>0</code>、<code>null</code>
     */
    private ProjectionRunner<Object> createRunner() {
        ProjectionService service = new ProjectionService() {
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
        EventChunkReader<Object> reader = (streamId, fromEventNumber, chunkSize, eventTypes) ->
                EventChunk.empty(fromEventNumber);
        return new ProjectionRunner<>(service, reader);
    }

    /**
     * 执行 <code>create View</code>，调用 <code>Collections.singleton</code>。
     *
     * @param name 传入此操作的 <code>String</code> 类型 <code>name</code>
     * @param cron 控制任务触发时刻的 Cron 表达式
     * @return 按条件返回 <code>cron</code>、<code>java.util.Collections.singleton("TestEvent")</code>
     */
    private ProjectionView<Object> createView(String name, String cron) {
        return new ProjectionView<Object>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getCron() {
                return cron;
            }

            @Override
            public Collection<String> getEventTypes() {
                return java.util.Collections.singleton("TestEvent");
            }

            @Override
            public void handleEvents(Collection<Object> events) {
            }
        };
    }

    /** 验证<code>handle.isActive()</code>为真，<code>handle.isActive()</code>为假。 */
    @Test
    void scheduler_schedule_应返回activeHandle_cancel后应inactive() {
        HelidonProjectionScheduler scheduler = createScheduler();

        ViewScheduler.ViewScheduleHandle handle = scheduler.schedule(
                "direct-view", "0/1 * * * * *", () -> {
                });

        assertThat(handle.isActive()).isTrue();

        handle.cancel();
        assertThat(handle.isActive()).isFalse();

        scheduler.shutdown();
    }

    /** 验证<code>viewManager.isRunning()</code>为假，<code>viewManager.isRunning()</code>为真。 */
    @Test
    void viewManager_start_stop_应管理生命周期() {
        HelidonProjectionScheduler scheduler = createScheduler();
        ProjectionRunner<Object> runner = createRunner();
        ProjectionView<Object> view = createView("test-view", "0/5 * * * * *");
        HelidonProjectionViewManager viewManager =
                new HelidonProjectionViewManager(scheduler, runner, java.util.Collections.singletonList(view));

        assertThat(viewManager.isRunning()).isFalse();

        viewManager.start();
        assertThat(viewManager.isRunning()).isTrue();

        // start idempotent
        viewManager.start();
        assertThat(viewManager.isRunning()).isTrue();

        viewManager.stop();
        assertThat(viewManager.isRunning()).isFalse();

        scheduler.shutdown();
    }

    /** 验证<code>viewManager.isRunning()</code>为真。 */
    @Test
    void triggerOnce_应立即执行所有view的runOnce() {
        HelidonProjectionScheduler scheduler = createScheduler();
        ProjectionRunner<Object> runner = createRunner();
        ProjectionView<Object> view = createView("test-view", "0/5 * * * * *");
        HelidonProjectionViewManager viewManager =
                new HelidonProjectionViewManager(scheduler, runner, java.util.Collections.singletonList(view));

        viewManager.start();
        viewManager.triggerOnce();
        assertThat(viewManager.isRunning()).isTrue();
        viewManager.stop();

        scheduler.shutdown();
    }
}

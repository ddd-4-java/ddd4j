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
package io.ddd4j.data.projection.micronaut;

import io.ddd4j.core.cqrs.readmodel.*;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Collections;

/**
 * 集成测试专用装配工厂：{@code @MicronautTest} 引导的真实 BeanContext 需要
 * {@link ProjectionRunner}、{@link ProjectionView} 等 Bean 定义。
 * 本工厂以 {@code @Factory} 工厂方法注册测试替身（等价于 Quarkus 的
 * {@code @Produces @ApplicationScoped}）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Factory
class TestFactory {

    /**
     * 读取<code>null</code> 的当前值。
     *
     * @return 返回表达式 <code>null</code>
     */
    @Singleton
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
    @Singleton
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
    @Singleton
    @SuppressWarnings("unchecked")
    ProjectionRunner<Object> projectionRunner(ProjectionService service, EventChunkReader<Object> reader) {
        return new ProjectionRunner<>(service, reader);
    }

    /**
     * 按 <code>test View</code> 的契约计算并返回结果。
     *
     * @return 当前操作得到的 <code>ProjectionView&lt;Object&gt;</code> 结果
     */
    @Singleton
    ProjectionView<Object> testView() {
        return new ProjectionViewStub("test-view", "0/5 * * * * *");
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

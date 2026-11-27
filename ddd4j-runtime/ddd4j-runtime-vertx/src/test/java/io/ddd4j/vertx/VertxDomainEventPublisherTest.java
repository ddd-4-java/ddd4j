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
package io.ddd4j.vertx;


import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 <code>VertxDomainEventPublisher</code> 在<code>shouldPublishLocalEvent</code>等操作中的行为契约。
 */
class VertxDomainEventPublisherTest {

    /**
     * 验证<code>received.await(5, TimeUnit.SECONDS)</code>为真，<code>body.get()</code> 指向同一实例 <code>event</code>。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void shouldPublishLocalEventWithoutRequiringWireCodec() throws Exception {
        Vertx vertx = Vertx.vertx();
        try {
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<Object> body = new AtomicReference<>();
            vertx.eventBus().consumer(VertxDomainEventPublisher.ADDRESS, message -> {
                body.set(message.body());
                received.countDown();
            });

            Object event = new LocalEvent("order-created");
            new VertxDomainEventPublisher(vertx).publish(event);

            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(body.get()).isSameAs(event);
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    /**
     * 将 <code>name</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    private static final class LocalEvent {
        /**
         * 当前测试模型用于展示和断言的名称。
         */
        private final String name;

        /**
         * 设置 <code>name</code>，用于构造 <code>LocalEvent</code> 测试实例。
         *
         * @param name 当前测试指定的对象或属性名称
         */
        LocalEvent(String name) {
            this.name = name;
        }

        /**
         * 读取<code>name</code> 字段。
         *
         * @return <code>name</code> 字段
         */
        String name() {
            return name;
        }
    }
}

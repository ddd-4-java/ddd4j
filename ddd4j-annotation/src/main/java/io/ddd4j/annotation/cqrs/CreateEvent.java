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
package io.ddd4j.annotation.cqrs;

import io.ddd4j.annotation.Contract;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CQRS 读侧"创建"事件处理器。
 *
 * <p>标注于 CQRS 读侧投影的方法，标识该方法处理"实体被创建"类型的事件。
 *
 * <p>典型用法：
 * <pre>{@code
 * public class OrderListView extends DddJpaView {
 *     @CreateEvent(OrderCreatedEvent.class)
 *     public void onOrderCreated(EntityManager em, OrderCreatedEvent event) {
 *         em.persist(OrderListEntry.from(event));
 *     }
 * }
 * }</pre>
 *
 * <p>由各框架适配层（{@code ddd4j-runtime-spring} / {@code ddd4j-runtime-quarkus}）的
 * {@code SpringEventHandlerRegistry} / {@code QuarkusEventHandlerRegistry} 在运行时反射装配事件路由表。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Contract
public @interface CreateEvent {

    /**
     * 要处理的事件类型。
     */
    Class<?> value();
}

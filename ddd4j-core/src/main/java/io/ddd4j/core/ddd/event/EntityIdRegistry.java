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
package io.ddd4j.core.ddd.event;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * {@link EntityId} 类型注册表。
 *
 * <p>用于按 {@link EntityType} 字符串还原自定义 {@link EntityId} 实现类：业务方在
 * 启动期通过 {@link #register(String, Function)} 注册工厂函数；未注册的
 * {@code EntityType} 通过 {@link #valueOf(String, String)} 返回 {@code null}，
 * 由调用方按业务语义兜底（r2dbc 模块对未注册类型显式报错并附注册指引）。
 *
 * <h3>注册时机</h3>
 * <p>建议在框架适配层（如 Spring {@code @PostConstruct}、Quarkus {@code @Startup}）
 * 注册；并发安全由 {@link ConcurrentHashMap} 保证。
 *
 * <h3>内置类型</h3>
 * <p>{@link StringEntityId}（type 名称 {@code "String"}）启动期自动注册，
 * 无需业务方重复注册。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class EntityIdRegistry {

    private static final Map<String, Function<String, EntityId>> FACTORIES = new ConcurrentHashMap<>();

    /** 内置 StringEntityId 类型名（与 {@link StringEntityId} 内部 TYPE 常量一致）。 */
    private static final String STRING_ENTITY_TYPE = "String";

    static {
        // StringEntityId 是 ddd4j 内置兜底类型，启动期自动注册
        FACTORIES.put(STRING_ENTITY_TYPE, StringEntityId::new);
    }

    private EntityIdRegistry() {
    }

    /**
     * 注册自定义 {@link EntityId} 工厂。
     *
     * <p>同类型重复注册以最后一次为准；并发注册安全。
     *
     * @param typeName {@link EntityType#asString()} 返回的类型名（如 {@code "OrderId"}）
     * @param factory  从 value 字符串构造 {@link EntityId} 的工厂；{@code null} 清除注册
     * @throws NullPointerException {@code typeName} 为 {@code null} 时抛出
     */
    public static void register(String typeName, Function<String, EntityId> factory) {
        Objects.requireNonNull(typeName, "typeName must not be null");
        if (factory == null) {
            FACTORIES.remove(typeName);
        } else {
            FACTORIES.put(typeName, factory);
        }
    }

    /**
     * 移除已注册的 {@link EntityId} 工厂（主要用于测试清理）。
     *
     * @param typeName 已注册的类型名
     */
    public static void unregister(String typeName) {
        if (typeName != null && !STRING_ENTITY_TYPE.equals(typeName)) {
            // 保护内置 StringEntityId 不被清除
            FACTORIES.remove(typeName);
        }
    }

    /**
     * 按类型名还原 {@link EntityId}（未注册类型返回 {@code null}，调用方负责兜底）。
     *
     * @param typeName {@link EntityType#asString()} 返回的类型名
     * @param value    实体的原始 value 字符串
     * @return 注册工厂构造的 {@link EntityId}；未注册时返回 {@code null}
     */
    public static EntityId valueOf(String typeName, String value) {
        Function<String, EntityId> factory = FACTORIES.get(typeName);
        return factory == null ? null : factory.apply(value);
    }

    /**
     * 检查某类型是否已注册。
     *
     * @param typeName 类型名
     * @return 已注册时 {@code true}
     */
    public static boolean isRegistered(String typeName) {
        return typeName != null && FACTORIES.containsKey(typeName);
    }
}
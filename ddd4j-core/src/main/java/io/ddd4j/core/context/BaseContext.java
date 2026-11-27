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
package io.ddd4j.core.context;

import io.ddd4j.core.constant.SpiKeys;
import lombok.experimental.UtilityClass;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 全局基础上下文容器（纯 Java，零框架依赖）。
 * <p>
 * 提供一个 JVM 级别的静态键值存储，主要用于：
 * <ul>
 *   <li><b>SPI 服务实例注册</b>（推荐用法）：框架适配层在启动期通过 {@link #inject(String, Class, Object)}
 *       将 SPI 实例注入到约定 key 下（参见 {@link SpiKeys}）；业务代码通过
 *       {@link Contexts#get(String, Class)} 按 key + 类型安全查找。
 *       替代旧的 {@code DomainEvent.registerPublisher} 静态注册模式。</li>
 *   <li><b>应用启动元数据</b>（兼容旧用法）：如 {@code PROJECT_PACKAGE}、
 *       {@code APPLICATION_NAME} 等不随请求变化的配置项。
 *       使用无类型校验的 {@link #inject}/{@link #get}/{@link #contains} 通用 KV API。</li>
 * </ul>
 *
 * <p><b>推荐用法（SPI 服务，类型安全）</b></p>
 * <pre>{@code
 * // 1. 框架适配层启动期注入（类型校验版本）
 * BaseContext.inject(SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class, kafkaPublisher);
 *
 * // 2. 业务方查找（推荐走 Contexts 门面，自动支持线程级覆盖）
 * MQEventPublisher publisher = Contexts.getOrThrow(SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class);
 * }</pre>
 *
 * <p><b>查找优先级</b></p>
 * <ol>
 *   <li>{@link ThreadContext}（线程级，请求级 SPI 可覆盖全局默认）</li>
 *   <li>{@link BaseContext}（JVM 级，全局默认 SPI）</li>
 * </ol>
 *
 * <p><b>API 索引</b></p>
 * <ul>
 *   <li><b>SPI 类型安全 API</b>（3.0.0+ 推荐）：
 *       {@link #inject(String, Class, Object)} · {@link #get(String, Class)} ·
 *       {@link #remove(String)}</li>
 *   <li><b>通用 KV API</b>（兼容旧用法，无类型校验）：
 *       {@link #inject(Object, Object)} · {@link #get(Object)} · {@link #get(Object, Object)} ·
 *       {@link #contains(Object)}</li>
 *   <li><b>测试/调试 API</b>（慎用）：{@link #clear()}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ThreadContext 线程级上下文
 * @see Contexts SPI 查找门面
 * @see SpiKeys SPI 约定 key 常量
 * @since 3.0.0
 */
@UtilityClass
@SuppressWarnings("unchecked")
public class BaseContext {

    /**
     * 全局键值存储（线程安全）。
     * <p>
     * 所有键（SPI key 与通用 KV）共存于同一 {@link ConcurrentHashMap}，由调用方通过 key 前缀
     * 区分用途（SPI key 以 {@code ddd4j.spi.} 前缀，参见 {@link SpiKeys}）。
     */
    private static final ConcurrentMap<String, ContextEntry> GLOBAL = new ConcurrentHashMap<>();

    // ============================================================
    // 推荐 API：SPI 服务（类型安全，3.0.0+）
    // ============================================================

    /**
     * 按 SPI 约定 key 注册类型安全的服务实例。
     * <p>
     * 框架适配层应在启动期调用此方法注入 SPI 实现（如 KafkaMQEventPublisher）。
     * 与 {@link #inject(Object, Object)} 无类型校验版本相比：
     * <ul>
     *   <li><b>类型安全</b>：注册时校验 value 是 type 的实例，杜绝错误注入</li>
     *   <li><b>拒绝 null</b>：value 为 null 抛 {@link IllegalArgumentException}，避免后续 NPE</li>
     *   <li><b>显式语义</b>：明确表达"这是一个类型化的 SPI 服务"</li>
     * </ul>
     *
     * @param key   SPI 约定 key（参见 {@link SpiKeys}，不能为 {@code null}）
     * @param type  期望的服务类型（不能为 {@code null}）
     * @param value 服务实例（不能为 {@code null}）
     * @param <T>   服务类型
     * @throws IllegalArgumentException key/value/type 为 null，或 value 不是 type 的实例
     */
    public <T> void inject(String key, Class<T> type, T value) {
        if (Objects.isNull(key)) {
            throw new IllegalArgumentException("SPI key cannot be null");
        }
        if (Objects.isNull(type)) {
            throw new IllegalArgumentException("SPI type cannot be null");
        }
        if (Objects.isNull(value)) {
            throw new IllegalArgumentException("SPI service cannot be null");
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                    "SPI service must be instance of " + type.getName()
                            + ", but was " + value.getClass().getName());
        }
        GLOBAL.put(key, new ContextEntry(value));
    }

    /**
     * 按 SPI 约定 key 查找类型安全的服务实例。
     * <p>
     * 推荐通过 {@link Contexts#get(String, Class)} 门面调用（自动支持线程级覆盖）。
     * <p>
     * 方法名选择 {@code inject} 而非 {@code lookup/get/find} 的原因：与 {@link #inject(String, Class, Object)}
     * 配对使用，读写语义统一（注入 / 注入查找）。这是从 SPI 注入角度的设计：
     * <ul>
     *   <li>{@code inject(key, type, value)} ← 注入实例</li>
     *   <li>{@code get(key, type)} ← 注入上下文中的实例（查）</li>
     * </ul>
     *
     * @param key  SPI 约定 key（参见 {@link SpiKeys}）
     * @param type 期望的服务类型
     * @param <T>  服务类型
     * @return 包装的服务实例 Optional，未找到或类型不匹配返回 {@link Optional#empty()}
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        if (Objects.isNull(key) || Objects.isNull(type)) {
            return Optional.empty();
        }
        Object value = contextValue(GLOBAL.get(key));
        if (Objects.isNull(value) || !type.isInstance(value)) {
            return Optional.empty();
        }
        T casted = (T) value;
        return Optional.of(casted);
    }

    /**
     * 按 SPI 约定 key 移除已注册的服务实例。
     * <p>
     * 主要用于单测清理或框架适配层卸载场景。
     *
     * @param key SPI 约定 key
     * @return 被移除的服务实例 Optional，未找到返回 {@link Optional#empty()}
     */
    public Optional<Object> remove(String key) {
        if (Objects.isNull(key)) {
            return Optional.empty();
        }
        return Optional.ofNullable(contextValue(GLOBAL.remove(key)));
    }

    /**
     * 当前值与预期实例一致时，原子更新全局注册。
     *
     * @param key         全局注册键，不能为空
     * @param expected    预期实例，{@code null} 表示键尚未注册
     * @param replacement 替换实例，{@code null} 表示删除注册
     * @return 是否匹配并执行更新
     */
    public boolean replaceIfCurrent(String key, Object expected, Object replacement) {
        AtomicBoolean matched = new AtomicBoolean();
        GLOBAL.compute(key, (ignored, current) -> {
            if (contextValue(current) != expected) {
                return current;
            }
            matched.set(true);
            return Objects.isNull(replacement) ? null : new ContextEntry(replacement);
        });
        return matched.get();
    }

    /**
     * 注册启动期间使用的值，并返回可提交或回滚的本次注册。
     *
     * @param key   全局注册键，不能为空
     * @param value 注册值，不能为空
     * @return 仅控制本次写入的注册句柄
     */
    public Registration registerForRollback(String key, Object value) {
        ContextEntry entry = new ContextEntry(value);
        GLOBAL.compute(key, (ignored, current) -> {
            entry.previous = current;
            return entry;
        });
        return new Registration(key, entry);
    }

    /**
     * \u53d6\u51fa\u5f53\u524d\u4e0a\u4e0b\u6587\u8282\u70b9\u4e2d\u7684\u4e1a\u52a1\u503c\u3002
     * @param entry \u8981\u8bfb\u53d6\u7684\u4e0a\u4e0b\u6587\u7ed1\u5b9a\u8282\u70b9
     * @return \u7ed1\u5b9a\u7684\u503c\uff0c\u8282\u70b9\u4e0d\u5b58\u5728\u65f6\u8fd4\u56de\u7a7a
     */
    private static Object contextValue(ContextEntry entry) {
        return Objects.isNull(entry) ? null : entry.value;
    }

    /**
     * 向全局上下文注入一个无类型校验的键值对。
     * <p>
     * 适用场景：应用启动元数据（如 {@code PROJECT_PACKAGE}、{@code APPLICATION_NAME}），
     * 这些值类型不固定且无需类型安全的场景。
     * <p>
     * 如需注入类型化的 SPI 服务，请使用 {@link #inject(String, Class, Object)}。
     *
     * @param key   键（不能为 {@code null}）
     * @param value 值
     * @param <K>   键类型
     * @param <V>   值类型
     */
    public <K, V> void inject(K key, V value) {
        if (Objects.isNull(key)) {
            throw new IllegalArgumentException("key cannot be null");
        }
        GLOBAL.put(String.valueOf(key), new ContextEntry(value));
    }

    /**
     * 从全局上下文获取指定 key 对应的值（不安全转型）。
     * <p>
     * 返回 {@link Object} 需要调用方自行强转，存在 {@link ClassCastException} 风险。
     * SPI 服务查找请改用 {@link #get(String, Class)}。
     *
     * @param key 键
     * @param <K> 键类型
     * @param <V> 期望的值类型
     * @return 对应的值，不存在时返回 {@code null}
     */
    public <K, V> V get(K key) {
        if (Objects.isNull(key)) {
            return null;
        }
        return (V) contextValue(GLOBAL.get(String.valueOf(key)));
    }

    // ============================================================
    // 通用 KV API（无类型校验，兼容启动元数据等场景）
    // ============================================================

    /**
     * 从全局上下文获取指定 key 对应的值，未找到返回默认值。
     *
     * @param key          键
     * @param defaultValue 默认值
     * @param <K>          键类型
     * @param <V>          期望的值类型
     * @return 对应的值，不存在时返回 defaultValue
     */
    public <K, V> V get(K key, V defaultValue) {
        V value = get(key);
        return Objects.nonNull(value) ? value : defaultValue;
    }

    /**
     * 判断全局上下文中是否包含指定 key。
     *
     * @param key 键
     * @param <K> 键类型
     * @return {@code true} 表示存在，{@code false} 表示不存在
     */
    public <K> boolean contains(K key) {
        if (Objects.isNull(key)) {
            return false;
        }
        return GLOBAL.containsKey(String.valueOf(key));
    }

    /**
     * 清空全局上下文（仅用于测试或重启场景）。
     * <p>
     * <b>警告</b>：会清空所有 SPI 注册与应用启动元数据。生产代码严禁调用。
     * 单元测试可在 {@code @AfterEach} 中调用以隔离用例。
     */
    public void clear() {
        GLOBAL.clear();
    }

    /** \u4fdd\u5b58\u4e00\u6b21\u4e0a\u4e0b\u6587\u7ed1\u5b9a\u7684\u503c\u53ca\u56de\u6eda\u94fe\u4e2d\u7684\u524d\u4e00\u4e2a\u8282\u70b9\u3002 */
    private static final class ContextEntry {
        /** \u672c\u8282\u70b9\u7ed1\u5b9a\u7684\u4e0a\u4e0b\u6587\u503c\u3002 */
        private final Object value;
        // 同一键的 compute 回调负责访问和修改回滚链。
        /** \u540c\u4e00\u952e\u4e0b\u6700\u8fd1\u7684\u4e00\u6b21\u65e7\u7ed1\u5b9a\uff0c\u7528\u4e8e\u56de\u6eda\u5f53\u524d\u8282\u70b9\u3002 */
        private ContextEntry previous;
        /** \u5f53\u524d\u8282\u70b9\u662f\u5426\u5df2\u88ab\u56de\u6eda\u3002 */
        private boolean rolledBack;

        /**
         * \u4e3a\u5171\u4eab\u4e0a\u4e0b\u6587\u521b\u5efa\u4e00\u4e2a\u6301\u6709\u975e\u7a7a\u503c\u7684\u7ed1\u5b9a\u8282\u70b9\u3002
         * @param value \u5f53\u524d\u952e\u8981\u7ed1\u5b9a\u7684\u503c
         */
        private ContextEntry(Object value) {
            this.value = Objects.requireNonNull(value);
        }
    }

    // ============================================================
    // 测试/调试 API：慎用
    // ============================================================

    /**
     * 一次待提交的全局注册。查找方法继续返回原始注册值。
     */
    public static final class Registration {
        /** \u53c2\u4e0e SPI \u6ce8\u518c\u53ca\u67e5\u627e\u7684\u952e\u3002 */
        private final String key;
        /** \u5f52\u5f53\u524d\u4e0a\u4e0b\u6587\u6ce8\u518c\u6301\u6709\u7684\u7ed1\u5b9a\u8282\u70b9\u3002 */
        private final ContextEntry entry;
        /** \u8bb0\u5f55\u5f53\u524d\u5c1a\u672a\u63d0\u4ea4\u6216\u5c1a\u672a\u7ed3\u675f\u7684\u6ce8\u518c\u72b6\u6001\u3002 */
        private boolean pending = true;

        /**
         * \u5c06\u5f53\u524d\u6ce8\u518c\u4e0e\u4e0a\u4e0b\u6587\u69fd\u4f4d\u53ca\u5177\u4f53\u7ed1\u5b9a\u8282\u70b9\u5173\u8054\u3002
         * @param key \u5171\u4eab\u4e0a\u4e0b\u6587\u952e
         * @param entry \u5f53\u524d\u6ce8\u518c\u7684\u503c\u8282\u70b9
         */
        private Registration(String key, ContextEntry entry) {
            this.key = key;
            this.entry = entry;
        }

        /**
         * 确认本次写入并释放之前的注册。重复调用无效。
         */
        public void commit() {
            GLOBAL.compute(key, (ignored, current) -> {
                if (pending) {
                    pending = false;
                    entry.previous = null;
                }
                return current;
            });
        }

        /**
         * 撤销本次写入。存在较新的写入时，仅标记本次注册为已回滚。
         */
        public void rollback() {
            GLOBAL.compute(key, (ignored, current) -> {
                if (!pending) {
                    return current;
                }
                pending = false;
                entry.rolledBack = true;
                if (current != entry) {
                    return current;
                }
                ContextEntry previous = entry.previous;
                while (Objects.nonNull(previous) && previous.rolledBack) {
                    previous = previous.previous;
                }
                return previous;
            });
        }
    }
}

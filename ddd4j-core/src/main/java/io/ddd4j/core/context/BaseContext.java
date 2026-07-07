package io.ddd4j.core.context;

import io.ddd4j.core.constant.SpiKeys;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
 * <h3>推荐用法（SPI 服务，类型安全）</h3>
 * <pre>{@code
 * // 1. 框架适配层启动期注入（类型校验版本）
 * BaseContext.inject(SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class, kafkaPublisher);
 *
 * // 2. 业务方查找（推荐走 Contexts 门面，自动支持线程级覆盖）
 * MQEventPublisher publisher = Contexts.getOrThrow(SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class);
 * }</pre>
 *
 * <h3>查找优先级</h3>
 * <ol>
 *   <li>{@link ThreadContext}（线程级，请求级 SPI 可覆盖全局默认）</li>
 *   <li>{@link BaseContext}（JVM 级，全局默认 SPI）</li>
 * </ol>
 *
 * <h3>API 索引</h3>
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
    private static final Map<String, Object> GLOBAL = new ConcurrentHashMap<>();

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
        GLOBAL.put(key, value);
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
        Object value = GLOBAL.get(key);
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
        return Optional.ofNullable(GLOBAL.remove(key));
    }

    // ============================================================
    // 通用 KV API（无类型校验，兼容启动元数据等场景）
    // ============================================================

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
        GLOBAL.put(String.valueOf(key), value);
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
        return (V) GLOBAL.get(String.valueOf(key));
    }

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

    // ============================================================
    // 测试/调试 API：慎用
    // ============================================================

    /**
     * 清空全局上下文（仅用于测试或重启场景）。
     * <p>
     * <b>警告</b>：会清空所有 SPI 注册与应用启动元数据。生产代码严禁调用。
     * 单元测试可在 {@code @AfterEach} 中调用以隔离用例。
     */
    public void clear() {
        GLOBAL.clear();
    }
}

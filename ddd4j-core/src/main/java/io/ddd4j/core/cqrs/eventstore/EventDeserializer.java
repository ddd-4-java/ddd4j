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
package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.kit.lang.JsonKit;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 事件载荷反序列化器：校验 eventType 合法性后按 Class.forName 还原，
 * 失败回退为 Map。
 *
 * <p>从存储层读取的 {@code eventType} 是不可信输入（可能被恶意写入）。
 * 本类在 {@code Class.forName} 之前校验类名格式，防止加载任意类。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public final class EventDeserializer {

    /**
     * 合法的 Java 全限定类名：至少一个包段，每段以字母/下划线/$开头。
     */
    private static final Pattern VALID_CLASS_NAME =
            Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+$");

    /**
     * 进程级类名过滤器（默认 {@link #defaultFilter()}，业务方可注册更严格的实现）。
     */
    private static volatile ClassNameFilter filter = defaultFilter();

    private EventDeserializer() {
    }

    /**
     * 默认类名过滤器：仅允许加载（实际可在调用方配置）。
     *
     * <p>该默认实现是"空操作"——任何合法格式类名都允许加载。业务方应在启动时
     * 通过 {@link #setFilter(ClassNameFilter)} 注册白名单实现（如
     * {@code className.startsWith("io.ddd4j.")} 或
     * {@code className.startsWith("com.example.")}），从而限制恶意 eventType 的攻击面。
     *
     * @return 默认过滤器（恒返回 {@code true}）
     */
    public static ClassNameFilter defaultFilter() {
        return name -> true;
    }

    /**
     * 注册进程级类名过滤器。
     *
     * <p>典型用法：应用启动时调用一次，注册业务白名单过滤。
     * 单 JVM 同一时刻仅生效最后注册的过滤器（覆盖式）。
     *
     * @param filter 过滤器实现（不允许 {@code null}，使用默认值请传 {@link #defaultFilter()}）
     * @throws NullPointerException filter 为 null 时
     */
    public static void setFilter(ClassNameFilter filter) {
        if (filter == null) {
            throw new NullPointerException("filter must not be null");
        }
        EventDeserializer.filter = filter;
    }

    /**
     * 返回当前进程级类名过滤器。
     *
     * <p>未通过 {@link #setFilter(ClassNameFilter)} 显式注册时返回 {@link #defaultFilter()}。
     *
     * @return 当前生效的过滤器
     */
    public static ClassNameFilter filter() {
        return filter;
    }

    /**
     * 校验类名是否为合法的 Java 全限定名格式。
     *
     * <p>在以 {@code Class.forName} 加载外部输入的类名之前调用，
     * 防止格式异常输入触发意外类加载。
     *
     * @param className 待校验的类名（可能为 null）
     * @return 格式合法时 {@code true}
     */
    public static boolean isValidClassName(String className) {
        return className != null && VALID_CLASS_NAME.matcher(className).matches();
    }

    /**
     * 按 eventType 反序列化 payload。
     *
     * <p>校验顺序：
     * <ol>
     *   <li>类名格式（{@link #isValidClassName}）—— 非法回退 Map</li>
     *   <li>类名白名单（{@link ClassNameFilter#allows(String)}）—— 不在白名单回退 Map</li>
     *   <li>类加载（{@code Class.forName}）—— 类不存在（被删除/重命名）回退 Map</li>
     * </ol>
     *
     * <p>所有失败路径均回退为 {@code Map}，保证反序列化永远不抛异常。
     *
     * @param payload   JSON 文本
     * @param eventType 事件类全限定名（来自存储层，不可信）
     * @return 强类型事件对象或 Map（回退）
     */
    @SuppressWarnings("unchecked")
    public static Object deserialize(String payload, String eventType) {
        if (!isValidClassName(eventType)) {
            return JsonKit.toMap(payload);
        }
        if (!filter.allows(eventType)) {
            return JsonKit.toMap(payload);
        }
        try {
            Class<?> eventClass = Class.forName(eventType);
            return JsonKit.toObject(payload, eventClass);
        } catch (ClassNotFoundException e) {
            return JsonKit.toMap(payload);
        }
    }
}

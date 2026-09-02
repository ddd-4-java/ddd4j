package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.utils.JsonKit;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 事件载荷反序列化器（回填自 3.0.x ce08043b）。
 *
 * <p>校验 eventType 合法性后按 {@code Class.forName} 还原强类型事件对象；
 * 任一环节失败均安全回退为 {@code Map}，保证反序列化永不抛异常。
 *
 * <h3>三层防御</h3>
 * <ol>
 *   <li>类名格式（{@link #isValidClassName}）—— 非法回退 Map</li>
 *   <li>白名单（{@link ClassNameFilter}）—— 不允许回退 Map</li>
 *   <li>类加载（{@code Class.forName}）—— 类不存在（被删除/重命名）回退 Map</li>
 * </ol>
 */
public final class EventDeserializer {

    /** 合法的 Java 全限定类名：至少一个包段，每段以字母/下划线/$开头。 */
    private static final Pattern VALID_CLASS_NAME =
            Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+$");

    /** 进程级类名过滤器（默认放行，业务方可注册更严格的实现）。 */
    private static volatile ClassNameFilter filter = defaultFilter();

    private EventDeserializer() {
    }

    /**
     * 默认类名过滤器（空操作：任何合法格式类名都允许加载）。
     *
     * <p>业务方应在启动时通过 {@link #setFilter(ClassNameFilter)} 注册白名单实现
     * （如 {@code className.startsWith("io.ddd4j.")}），限制恶意 eventType 的攻击面。
     */
    public static ClassNameFilter defaultFilter() {
        return new ClassNameFilter() {
            @Override public boolean allows(String className) { return true; }
        };
    }

    /**
     * 注册进程级类名过滤器（覆盖式，单 JVM 仅最后注册的生效）。
     *
     * @param filter 过滤器实现（不允许 {@code null}）
     * @throws NullPointerException filter 为 null 时
     */
    public static void setFilter(ClassNameFilter filter) {
        if (filter == null) {
            throw new NullPointerException("filter must not be null");
        }
        EventDeserializer.filter = filter;
    }

    /** 获取当前进程级类名过滤器（永不为 {@code null}）。 */
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
     * 按 eventType 反序列化 payload。所有失败路径均回退为 {@code Map}。
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

package io.ddd4j.core.ddd.event;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.esc.JpaEventDispatcher;
import org.fuin.ddd4j.core.Event;
import org.fuin.esc.api.CommonEvent;

import java.util.List;

/**
 * ddd4j 事件分发器基类（纯净 DDD 轨道）。
 *
 * <p>基于 fuinorg {@link JpaEventDispatcher}，将事件路由到对应的 {@code JpaEventHandler}：
 * <ul>
 *   <li>{@code getAllTypes()} — 返回所有已注册的事件类型</li>
 *   <li>{@code dispatchCommonEvents(em, events)} — 分发 CommonEvent 列表</li>
 *   <li>{@code dispatchEvents(em, events)} — 分发 Event 列表</li>
 *   <li>{@code dispatchEvent(em, event)} — 分发单个事件</li>
 * </ul>
 *
 * <p>使用方式（通常由 Spring/Quarkus 自动配置注册）：
 * <pre>
 * public class OrderEventDispatcher extends DddEventDispatcher {
 *     private final Map&lt;EventType, List&lt;JpaEventHandler&gt;&gt; handlers = new HashMap&lt;&gt;();
 *
 *     public OrderEventDispatcher(List&lt;JpaEventHandler&lt;?&gt;&gt; handlerList) {
 *         for (JpaEventHandler&lt;?&gt; handler : handlerList) {
 *             handlers.computeIfAbsent(handler.getEventType(), k -&gt; new ArrayList&lt;&gt;()).add(handler);
 *         }
 *     }
 *
 *     &#64;Override
 *     public Set&lt;EventType&gt; getAllTypes() { return handlers.keySet(); }
 *
 *     &#64;Override
 *     public void dispatchEvent(EntityManager em, Event event) {
 *         List&lt;JpaEventHandler&gt; list = handlers.get(event.getEventType());
 *         if (Objects.nonNull(list)) {
 *             for (JpaEventHandler handler : list) {
 *                 handler.handle(em, event);
 *             }
 *         }
 *     }
 * }
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see JpaEventDispatcher
 * @since 2.0.x
 */
public abstract class DddEventDispatcher implements JpaEventDispatcher {

    /**
     * 分发 CommonEvent 列表。默认实现提取 Event 后委托给 dispatchEvents。
     */
    @Override
    public void dispatchCommonEvents(EntityManager entityManager, List<CommonEvent> commonEvents) {
        List<Event> events = commonEvents.stream()
                .map(ce -> (Event) ce.getData())
                .toList();
        dispatchEvents(entityManager, events);
    }

    /**
     * 分发 Event 列表。默认实现逐个委托给 dispatchEvent。
     */
    @Override
    public void dispatchEvents(EntityManager entityManager, List<Event> events) {
        for (Event event : events) {
            dispatchEvent(entityManager, event);
        }
    }

}

package io.ddd4j.core.cqrs.readmodel;

import io.ddd4j.kit.lang.CollKit;
import io.ddd4j.kit.lang.StrKit;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 轻量类型事件分发器。
 *
 * <p>用于替代业务代码里大量 {@code instanceof} 分支，保持读侧投影处理逻辑清晰。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class TypedEventDispatcher {

    /** \u6309\u4e8b\u4ef6\u7c7b\u578b\u6807\u8bc6\u67e5\u627e\u7684\u5f3a\u7c7b\u578b\u5904\u7406\u5668\u3002 */
    private final Map<String, TypedEventHandler<?>> handlers;

    /**
     * \u5efa\u7acb\u4e8b\u4ef6\u7c7b\u578b\u5230\u5904\u7406\u5668\u7684\u7d22\u5f15\u3002
     * @param handlers \u5f85\u6ce8\u518c\u7684\u7c7b\u578b\u5316\u4e8b\u4ef6\u5904\u7406\u5668
     */
    public TypedEventDispatcher(Collection<? extends TypedEventHandler<?>> handlers) {
        this.handlers = indexHandlers(handlers);
    }

    /**
     * \u5c06\u4e8b\u4ef6\u4ea4\u7ed9\u5339\u914d\u7c7b\u578b\u7684\u5904\u7406\u5668\u3002
     * @param event \u5f85\u6d3e\u53d1\u4e8b\u4ef6
     * @return \u627e\u5230\u5e76\u8c03\u7528\u5904\u7406\u5668\u65f6\u4e3a true
     */
    public boolean dispatch(TypedEvent event) {
        TypedEvent typedEvent = Objects.requireNonNull(event, "event must not be null");
        return dispatch(typedEvent.getEventType(), typedEvent);
    }

    /**
     * \u9010\u4e2a\u6d3e\u53d1\u4e8b\u4ef6\u96c6\u5408\u3002
     * @param events \u5f85\u6d3e\u53d1\u4e8b\u4ef6\u96c6\u5408
     * @return \u5b9e\u9645\u6d3e\u53d1\u7684\u4e8b\u4ef6\u6570
     */
    public int dispatchAll(Collection<? extends TypedEvent> events) {
        if (CollKit.isEmpty(events)) {
            return 0;
        }
        int dispatched = 0;
        for (TypedEvent event : events) {
            if (dispatch(event)) {
                dispatched++;
            }
        }
        return dispatched;
    }

    /**
     * \u5c06\u4e8b\u4ef6\u4ea4\u7ed9\u5339\u914d\u7c7b\u578b\u7684\u5904\u7406\u5668\u3002
     * @param eventType \u4e8b\u4ef6\u7c7b\u578b
     * @param event \u4e8b\u4ef6\u8f7d\u8377
     * @return \u627e\u5230\u5e76\u8c03\u7528\u5904\u7406\u5668\u65f6\u4e3a true
     */
    public boolean dispatch(String eventType, Object event) {
        if (StrKit.isBlank(eventType) || Objects.isNull(event)) {
            return false;
        }
        TypedEventHandler<?> handler = handlers.get(eventType);
        if (Objects.isNull(handler)) {
            return false;
        }
        invoke(handler, event);
        return true;
    }

    /**
     * \u7edf\u8ba1\u5df2\u6ce8\u518c\u7684\u5904\u7406\u5668\u6570\u3002
     * @return \u5904\u7406\u5668\u6570\u91cf
     */
    public int size() {
        return handlers.size();
    }

    /**
     * \u6309\u5904\u7406\u5668\u58f0\u660e\u7684\u4e8b\u4ef6\u7c7b\u578b\u5efa\u7acb\u5206\u53d1\u67e5\u627e\u8868\u3002
     * @param eventHandlers \u5019\u9009\u4e8b\u4ef6\u5904\u7406\u5668\u96c6\u5408
     * @return \u6309\u4e8b\u4ef6\u7c7b\u578b\u7d22\u5f15\u7684\u5904\u7406\u5668\u6620\u5c04
     */
    private Map<String, TypedEventHandler<?>> indexHandlers(Collection<? extends TypedEventHandler<?>> eventHandlers) {
        Map<String, TypedEventHandler<?>> indexedHandlers = new LinkedHashMap<>();
        if (CollKit.isEmpty(eventHandlers)) {
            return Map.copyOf(indexedHandlers);
        }
        for (TypedEventHandler<?> handler : eventHandlers) {
            TypedEventHandler<?> safeHandler = Objects.requireNonNull(handler, "handler must not be null");
            if (StrKit.isBlank(safeHandler.getEventType())) {
                throw new IllegalArgumentException("handler eventType must not be blank");
            }
            indexedHandlers.put(safeHandler.getEventType(), safeHandler);
        }
        return Map.copyOf(indexedHandlers);
    }

    /**
     * \u6821\u9a8c\u8fd0\u884c\u65f6\u4e8b\u4ef6\u7c7b\u578b\u5e76\u8c03\u7528\u5339\u914d\u7684\u5904\u7406\u5668\u3002
     * @param <E> \u5904\u7406\u5668\u63a5\u53d7\u7684\u4e8b\u4ef6\u7c7b\u578b
     * @param handler \u8d1f\u8d23\u8be5\u4e8b\u4ef6\u7684\u5f3a\u7c7b\u578b\u5904\u7406\u5668
     * @param event \u4ece\u5206\u53d1\u5165\u53e3\u6536\u5230\u7684\u4e8b\u4ef6\u5bf9\u8c61
     * @throws IllegalArgumentException \u4e8b\u4ef6\u4e0d\u662f\u5904\u7406\u5668\u58f0\u660e\u7684\u7c7b\u578b
     */
    private <E> void invoke(TypedEventHandler<E> handler, Object event) {
        if (!handler.getEventClass().isInstance(event)) {
            throw new IllegalArgumentException("event type mismatch: " + event.getClass().getName());
        }
        handler.handle(handler.getEventClass().cast(event));
    }
}

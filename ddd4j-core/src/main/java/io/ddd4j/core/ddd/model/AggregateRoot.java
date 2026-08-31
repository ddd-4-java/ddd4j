package io.ddd4j.core.ddd.model;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EventHandler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.lang.reflect.Method;

/**
 * JDK8 原生聚合根基类，负责未提交事件队列与事件溯源回放边界。
 *
 * @param <ID> 聚合根标识类型
 */
public abstract class AggregateRoot<ID extends Serializable> implements Entity<ID> {
    private static final long serialVersionUID = 1L;
    private final List<DomainEvent<?>> uncommittedChanges = new ArrayList<DomainEvent<?>>();

    protected <E extends DomainEvent<?>> E apply(E event) {
        E actual = Objects.requireNonNull(event, "event must not be null");
        dispatch(actual, false);
        uncommittedChanges.add(actual);
        return actual;
    }

    public final void loadFromHistory(List<? extends DomainEvent<?>> history) {
        if (history != null) {
            for (DomainEvent<?> event : history) {
                dispatch(Objects.requireNonNull(event, "history event must not be null"), true);
            }
        }
        clearUncommittedChanges();
    }

    private void dispatch(DomainEvent<?> event, boolean replay) {
        Method selected = null;
        Class<?> type = getClass();
        while (type != null && AggregateRoot.class.isAssignableFrom(type)) {
            Method[] methods = type.getDeclaredMethods();
            for (Method method : methods) {
                EventHandler handler = method.getAnnotation(EventHandler.class);
                Class<?>[] parameters = method.getParameterTypes();
                if (handler != null && parameters.length == 1 && parameters[0].isAssignableFrom(event.getClass())) {
                    if (replay && handler.ignoreOnReplay()) return;
                    selected = method;
                    break;
                }
            }
            if (selected != null) break;
            type = type.getSuperclass();
        }
        if (selected == null) throw new IllegalStateException("No @EventHandler method found for event type: " + event.getClass().getName());
        try {
            selected.setAccessible(true);
            selected.invoke(this, event);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to invoke @EventHandler for " + event.getClass().getName(), exception);
        }
    }

    public final List<DomainEvent<?>> getUncommittedChanges() {
        return Collections.unmodifiableList(new ArrayList<DomainEvent<?>>(uncommittedChanges));
    }

    public final void clearUncommittedChanges() {
        uncommittedChanges.clear();
    }
}

package io.ddd4j.core.ddd.model;

import io.ddd4j.core.ddd.event.DomainEvent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * JDK8 原生聚合根基类，负责未提交事件队列与事件溯源回放边界。
 *
 * @param <ID> 聚合根标识类型
 */
public abstract class AggregateRoot<ID extends Serializable> implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<DomainEvent<?>> uncommittedChanges = new ArrayList<DomainEvent<?>>();

    protected <E extends DomainEvent<?>> E apply(E event) {
        E actual = Objects.requireNonNull(event, "event must not be null");
        uncommittedChanges.add(actual);
        return actual;
    }

    public final void loadFromHistory(List<? extends DomainEvent<?>> history) {
        if (history != null) {
            for (DomainEvent<?> event : history) {
                replay(Objects.requireNonNull(event, "history event must not be null"));
            }
        }
        clearUncommittedChanges();
    }

    /** 子类可覆写以把历史事件应用到聚合状态；回放不会进入未提交队列。 */
    protected void replay(DomainEvent<?> event) {
        // 默认无状态重放；领域聚合按需覆写。
    }

    public final List<DomainEvent<?>> getUncommittedChanges() {
        return Collections.unmodifiableList(new ArrayList<DomainEvent<?>>(uncommittedChanges));
    }

    public final void clearUncommittedChanges() {
        uncommittedChanges.clear();
    }
}

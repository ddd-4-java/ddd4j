package io.ddd4j.core.cqrs.command;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.AggregateVersion;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.event.EntityIdPath;

/** 聚合命令基类，包含目标实体路径和乐观锁期望版本。 */
public abstract class AggregateCommand<ROOT_ID extends AggregateRootId, ENTITY_ID extends EntityId> implements Command {
    private EntityIdPath entityIdPath;
    private AggregateVersion aggregateVersion;
    protected AggregateCommand() { }
    protected AggregateCommand(EntityIdPath entityIdPath, AggregateVersion aggregateVersion) {
        this.entityIdPath = entityIdPath;
        this.aggregateVersion = aggregateVersion;
    }
    public EntityIdPath getEntityIdPath() { return entityIdPath; }
    public AggregateVersion getAggregateVersion() { return aggregateVersion; }
    @SuppressWarnings("unchecked") public ROOT_ID getAggregateRootId() { return entityIdPath == null ? null : (ROOT_ID) entityIdPath.first(); }
    @SuppressWarnings("unchecked") public ENTITY_ID getEntityId() { return entityIdPath == null ? null : (ENTITY_ID) entityIdPath.last(); }
}

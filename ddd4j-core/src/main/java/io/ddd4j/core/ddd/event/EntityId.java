package io.ddd4j.core.ddd.event;
import java.io.Serializable;
/**
 * 领域实体标识的最小契约。
 */
public interface EntityId extends Serializable { EntityType getType(); String asString(); String asTypedString(); }

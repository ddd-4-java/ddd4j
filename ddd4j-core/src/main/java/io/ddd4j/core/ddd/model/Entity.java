package io.ddd4j.core.ddd.model;

import java.io.Serializable;
import java.util.Objects;

/** 按身份而非全部属性比较的领域实体。 */
public interface Entity<ID extends Serializable> extends DomainModel<ID> {
    default boolean sameIdentityAs(Entity<ID> other) {
        return other != null && id() != null && Objects.equals(id(), other.id());
    }
}

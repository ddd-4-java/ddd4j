package io.ddd4j.core.ddd.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * DDD entity contract.
 *
 * <p>Entities are compared by identity, not by all field values.</p>
 *
 * @param <ID> entity identity type
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface Entity<ID extends Serializable> extends DomainModel<ID> {

    /**
     * Compares entity identity.
     *
     * @param other another entity
     * @return {@code true} if both entities have the same non-null identity
     */
    default boolean sameIdentityAs(Entity<ID> other) {
        return Objects.nonNull(other) && Objects.nonNull(id()) && Objects.equals(id(), other.id());
    }
}

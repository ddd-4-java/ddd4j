package io.ddd4j.core.domain.model;

import java.io.Serializable;

/**
 * Domain model marker for the tactical DDD path.
 *
 * <p>This contract is intentionally persistence-agnostic. Domain models must not
 * depend on MyBatis, JPA, Spring, or any runtime framework. Persistence objects
 * such as PO/Entity classes belong to infrastructure adapters.</p>
 *
 * @param <ID> domain identity type
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface DomainModel<ID extends Serializable> extends Serializable {

    /**
     * Returns the domain identity.
     *
     * @return domain identity
     */
    ID id();
}

package io.ddd4j.core.ddd.model;

/**
 * Maps between domain models and persistence objects.
 *
 * <p>PO classes are infrastructure concerns. Keep this mapper implementation in
 * the adapter layer so the domain model remains free from ORM annotations and
 * persistence naming conventions.</p>
 *
 * @param <M> domain model type
 * @param <P> persistence object type, usually named {@code *PO}
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface DomainObjectMapper<M, P> {

    /**
     * Converts a persistence object to a domain model.
     *
     * @param persistenceObject persistence object
     * @return domain model
     */
    M toModel(P persistenceObject);

    /**
     * Converts a domain model to a persistence object.
     *
     * @param model domain model
     * @return persistence object
     */
    P toPersistenceObject(M model);
}

package io.ddd4j.sample.javalin.cqrs.shared;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.data.projection.jpa.EntityManagerProjectionPositionRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Guice 样例的事务边界；全部持久化语义复用已有 JPA 位置适配器。 */
final class PersistentPositions implements ProjectionPositionRepository {
    private final EntityManagerFactory factory;
    PersistentPositions(EntityManagerFactory factory) { this.factory = factory; }
    private <T> T execute(Function<EntityManagerProjectionPositionRepository, T> action) {
        try (var manager = factory.createEntityManager()) {
            var transaction = manager.getTransaction();
            transaction.begin();
            try {
                T result = action.apply(new EntityManagerProjectionPositionRepository(manager));
                transaction.commit();
                return result;
            } catch (RuntimeException | Error failure) {
                if (transaction.isActive()) { transaction.rollback(); }
                throw failure;
            }
        }
    }
    @Override public Optional<ProjectionPosition> findByStreamId(String id) { return execute(repo -> repo.findByStreamId(id)); }
    @Override public List<ProjectionPosition> findAll() { return execute(ProjectionPositionRepository::findAll); }
    @Override public ProjectionPosition save(ProjectionPosition position) { return execute(repo -> repo.save(position)); }
    @Override public void deleteByStreamId(String id) { execute(repo -> { repo.deleteByStreamId(id); return null; }); }
    @Override public void resetToZero(String id) { execute(repo -> { repo.resetToZero(id); return null; }); }
}

package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.domain.query.projection.ProjectionPosition;
import io.ddd4j.core.domain.query.projection.ProjectionPositionRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Quarkus Panache 投影位置仓储（{@link ProjectionPositionRepository} SPI 实现）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@ApplicationScoped
public class QuarkusJpaProjectionPositionRepository implements ProjectionPositionRepository {

    @Inject
    PanacheRepositoryBase<QuarkusJpaProjectionPosition, String> panacheRepo;

    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return Optional.ofNullable(panacheRepo.findById(streamId)).map(p -> (ProjectionPosition) p);
    }

    @Override
    public List<ProjectionPosition> findAll() {
        return panacheRepo.listAll().stream().map(p -> (ProjectionPosition) p).toList();
    }

    @Override
    @Transactional
    public ProjectionPosition save(ProjectionPosition position) {
        QuarkusJpaProjectionPosition entity = (QuarkusJpaProjectionPosition) position;
        panacheRepo.persist(entity);
        return entity;
    }

    @Override
    @Transactional
    public void deleteByStreamId(String streamId) {
        panacheRepo.deleteById(streamId);
    }

    @Override
    @Transactional
    public void resetToZero(String streamId) {
        panacheRepo.update("nextEventNumber = 0 where streamId = ?1", streamId);
    }
}

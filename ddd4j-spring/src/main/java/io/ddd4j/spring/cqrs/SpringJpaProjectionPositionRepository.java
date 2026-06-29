package io.ddd4j.spring.cqrs;

import io.ddd4j.core.cqrs.projection.ProjectionPosition;
import io.ddd4j.core.cqrs.projection.ProjectionPositionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA 投影位置仓储（{@link ProjectionPositionRepository} SPI 实现）。
 *
 * <p>由 Spring 框架自动注入，业务方无需关心。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Repository
public class SpringJpaProjectionPositionRepository
        implements ProjectionPositionRepository {

    private final JpaRepository<SpringJpaProjectionPosition, String> jpaRepository;

    public SpringJpaProjectionPositionRepository(JpaRepository<SpringJpaProjectionPosition, String> jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return jpaRepository.findById(streamId).map(p -> (ProjectionPosition) p);
    }

    @Override
    public List<ProjectionPosition> findAll() {
        return jpaRepository.findAll().stream().map(p -> (ProjectionPosition) p).toList();
    }

    @Override
    public ProjectionPosition save(ProjectionPosition position) {
        return jpaRepository.save((SpringJpaProjectionPosition) position);
    }

    @Override
    public void deleteByStreamId(String streamId) {
        jpaRepository.deleteById(streamId);
    }

    @Override
    @Modifying
    @Query("UPDATE SpringJpaProjectionPosition p SET p.nextEventNumber = 0 WHERE p.streamId = :streamId")
    public void resetToZero(@Param("streamId") String streamId) {
    }
}

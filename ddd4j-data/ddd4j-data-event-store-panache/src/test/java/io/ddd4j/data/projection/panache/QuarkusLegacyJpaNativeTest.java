package io.ddd4j.data.projection.panache;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.quarkus.cqrs.QuarkusJpaProjectionPosition;
import io.ddd4j.quarkus.cqrs.QuarkusJpaProjectionPositionRepository;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 旧公开 JPA 实体/仓储在真实 Quarkus ORM 中读取历史类型并接受共享位置值。
 */
@QuarkusTest
@TestProfile(QuarkusLegacyJpaNativeTest.JpaPositionProfile.class)
class QuarkusLegacyJpaNativeTest {
    @Inject
    QuarkusJpaProjectionPositionRepository repository;
    @Inject
    EntityManager entityManager;

    @Test
    void oldEntityMappingAndSharedPositionRemainCompatibleWithoutSpring() {
        assertThat(Arc.container().instance(QuarkusProjectionPositionRepository.class).isAvailable()).isFalse();
        repository.save(new QuarkusJpaProjectionPosition("r08-old-jpa", 41));
        entityManager.clear();
        assertThat(repository.findByStreamId("r08-old-jpa").get().getNextEventNumber()).isEqualTo(41);
        repository.save(new DefaultProjectionPosition("r08-old-jpa", 44));
        entityManager.clear();
        assertThat(repository.findByStreamId("r08-old-jpa").get().getNextEventNumber()).isEqualTo(44);
        repository.deleteByStreamId("r08-old-jpa");
        entityManager.clear();
        assertThat(repository.findByStreamId("r08-old-jpa")).isEmpty();
    }

    public static class JpaPositionProfile implements QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Collections.singletonMap("ddd4j.quarkus.cqrs.projection.position-store", "jpa");
        }
    }
}

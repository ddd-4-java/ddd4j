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
    /**
     * 用于当前读取或写入断言的测试仓储。
     */
    @Inject
    QuarkusJpaProjectionPositionRepository repository;
    /**
     * 执行 JPA 查询和持久化操作的实体管理器。
     */
    @Inject
    EntityManager entityManager;

    /**
     * 验证<code>Arc.container().instance(QuarkusProjectionPositionRepository.class).isAvailable()</code>为假，<code>repository.findByStreamId("r08-old-jpa").get().getNextEventNumber()</code> 等于 <code>41</code>，<code>repository.findByStreamId("r08-old-jpa")</code>为空。
     */
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

    /**
     * 实现 <code>QuarkusTestProfile</code>，供测试调用 <code>getConfigOverrides()</code> 并观察结果。
     */
    public static class JpaPositionProfile implements QuarkusTestProfile {
        /**
         * 通过 <code>java.util.Collections.singletonMap()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>java.util.Collections.singletonMap("ddd4j.quarkus.cqrs.projection.position-store", "jpa")</code>
         */
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Collections.singletonMap("ddd4j.quarkus.cqrs.projection.position-store", "jpa");
        }
    }
}

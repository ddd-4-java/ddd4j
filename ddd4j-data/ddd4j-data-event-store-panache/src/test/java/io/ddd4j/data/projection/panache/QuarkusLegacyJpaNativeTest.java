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
    /** \u7528\u4e8e\u5f53\u524d\u8bfb\u53d6\u6216\u5199\u5165\u65ad\u8a00\u7684\u6d4b\u8bd5\u4ed3\u50a8\u3002 */
    @Inject
    QuarkusJpaProjectionPositionRepository repository;
    /** \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    @Inject
    EntityManager entityManager;

    /** \u9a8c\u8bc1<code>Arc.container().instance(QuarkusProjectionPositionRepository.class).isAvailable()</code>\u4e3a\u5047\uff0c<code>repository.findByStreamId("r08-old-jpa").get().getNextEventNumber()</code> \u7b49\u4e8e <code>41</code>\uff0c<code>repository.findByStreamId("r08-old-jpa")</code>\u4e3a\u7a7a\u3002 */
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

    /** \u5b9e\u73b0 <code>QuarkusTestProfile</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getConfigOverrides()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    public static class JpaPositionProfile implements QuarkusTestProfile {
        /**
         * \u901a\u8fc7 <code>java.util.Collections.singletonMap()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>java.util.Collections.singletonMap("ddd4j.quarkus.cqrs.projection.position-store", "jpa")</code>
         */
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Collections.singletonMap("ddd4j.quarkus.cqrs.projection.position-store", "jpa");
        }
    }
}

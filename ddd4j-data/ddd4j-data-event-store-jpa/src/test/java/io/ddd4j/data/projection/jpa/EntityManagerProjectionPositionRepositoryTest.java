package io.ddd4j.data.projection.jpa;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 无 Spring 容器、文件数据库关闭重开及失败事务的持久化契约。
 */
class EntityManagerProjectionPositionRepositoryTest {
    /** JUnit \u4e3a\u672c\u6b21\u6d4b\u8bd5\u5206\u914d\u7684\u4e34\u65f6\u76ee\u5f55\u3002 */
    @TempDir
    Path directory;

    /**
     * \u6838\u5bf9<code>position.getNextEventNumber()</code> \u7b49\u4e8e <code>expected</code>\u3002
     * @param repository \u5f53\u524d\u6d4b\u8bd5\u8bbf\u95ee\u7684\u4ed3\u50a8\u5b9e\u4f8b
     * @param stream \u4f20\u7ed9 <code>assertThat()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param expected \u5f53\u524d\u7528\u4f8b\u8bbe\u5b9a\u7684\u671f\u671b\u503c
     */
    private static void assertPosition(ProjectionPositionRepository repository, String stream, long expected) {
        assertThat(repository.findByStreamId(stream)).hasValueSatisfying(
                position -> assertThat(position.getNextEventNumber()).isEqualTo(expected));
    }

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>UnsupportedOperationException.class</code>\uff0c<code>snapshot.get(0).getNextEventNumber()</code> \u7b49\u4e8e <code>10L</code>\uff0c<code>repository.findByStreamId("missing")</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u8bfb\u5199\u6d4b\u8bd5\u5b58\u50a8\u6216\u8fd0\u884c\u4e8b\u52a1\u5931\u8d25
     */
    @Test
    void commitsSnapshotsAndResetAcrossFactoryRestart() throws Exception {
        try (EntityManagerFactory factory = factory("create"); EntityManager em = factory.createEntityManager()) {
            ProjectionPositionRepository repository = repository(em);
            em.getTransaction().begin();
            repository.save(new DefaultProjectionPosition("orders", 10L));
            List<ProjectionPosition> snapshot = repository.findAll();
            assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
            repository.save(new DefaultProjectionPosition("orders", 20L));
            assertThat(snapshot.get(0).getNextEventNumber()).isEqualTo(10L);
            repository.resetToZero("missing");
            em.getTransaction().commit();
            assertThat(em.isOpen()).isTrue();
        }
        try (EntityManagerFactory factory = factory("validate"); EntityManager em = factory.createEntityManager()) {
            ProjectionPositionRepository repository = repository(em);
            assertPosition(repository, "orders", 20L);
            assertPosition(repository, "missing", 0L);
            em.getTransaction().begin();
            repository.resetToZero("orders");
            assertPosition(repository, "orders", 0L);
            repository.deleteByStreamId("missing");
            repository.deleteByStreamId("absent");
            em.getTransaction().commit();
        }
        try (EntityManagerFactory factory = factory("validate"); EntityManager em = factory.createEntityManager()) {
            ProjectionPositionRepository repository = repository(em);
            assertPosition(repository, "orders", 0L);
            assertThat(repository.findByStreamId("missing")).isEmpty();
        }
    }

    /**
     * \u9a8c\u8bc1<code>repository.save(new DefaultProjectionPosition("x".repeat(251), 1L))</code> \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>repository.findByStreamId("missing")</code>\u4e3a\u7a7a\uff0c<code>repository.findAll()</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002
     * @throws Exception \u8bfb\u5199\u6d4b\u8bd5\u5b58\u50a8\u6216\u8fd0\u884c\u4e8b\u52a1\u5931\u8d25
     */
    @Test
    void failureAfterFlushedCheckpointRollsBackDurably() throws Exception {
        try (EntityManagerFactory factory = factory("create"); EntityManager em = factory.createEntityManager()) {
            ProjectionPositionRepository repository = repository(em);
            em.getTransaction().begin();
            repository.save(new DefaultProjectionPosition("orders", 10L));
            em.getTransaction().commit();
            em.getTransaction().begin();
            repository.save(new DefaultProjectionPosition("orders", 20L));
            repository.resetToZero("missing");
            // 同一真实事务在已 flush 的 checkpoint 后触发数据库列长度约束失败。
            assertThatThrownBy(() -> repository.save(new DefaultProjectionPosition("x".repeat(251), 1L)))
                    .isInstanceOf(RuntimeException.class);
            em.getTransaction().rollback();
        }
        try (EntityManagerFactory factory = factory("validate"); EntityManager em = factory.createEntityManager()) {
            ProjectionPositionRepository repository = repository(em);
            assertPosition(repository, "orders", 10L);
            assertThat(repository.findByStreamId("missing")).isEmpty();
            assertThat(repository.findAll()).hasSize(1);
        }
    }

    /**
     * \u9a8c\u8bc1<code>repository.save(new DefaultProjectionPosition("orders", 1L))</code> \u629b\u51fa <code>jakarta.persistence.TransactionRequiredException.class</code>\uff0c<code>em.getTransaction().isActive()</code>\u4e3a\u5047\uff0c<code>em.isOpen()</code>\u4e3a\u771f\u3002
     * @throws Exception \u8bfb\u5199\u6d4b\u8bd5\u5b58\u50a8\u6216\u8fd0\u884c\u4e8b\u52a1\u5931\u8d25
     */
    @Test
    void requiresCallerTransactionWithoutStartingOrClosingOne() throws Exception {
        try (EntityManagerFactory factory = factory("create"); EntityManager em = factory.createEntityManager()) {
            ProjectionPositionRepository repository = repository(em);
            assertThatThrownBy(() -> repository.save(new DefaultProjectionPosition("orders", 1L)))
                    .isInstanceOf(jakarta.persistence.TransactionRequiredException.class);
            assertThat(em.getTransaction().isActive()).isFalse();
            assertThat(em.isOpen()).isTrue();
        }
    }

    /** \u9a8c\u8bc1<code>repository.incrementBy("orders", 2L)</code> \u7b49\u4e8e <code>12L</code>\uff0c<code>repository.incrementBy("orders", 0L)</code> \u7b49\u4e8e <code>12L</code>\uff0c<code>repository.incrementBy("absent", 1L)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void incrementRefreshesTargetWithoutDetachingOtherPendingEntities() {
        try (EntityManagerFactory factory = factory("create"); EntityManager em = factory.createEntityManager()) {
            EntityManagerProjectionPositionRepository repository = new EntityManagerProjectionPositionRepository(em);
            em.getTransaction().begin();
            repository.save(new DefaultProjectionPosition("orders", 10L));
            ProjectionPositionEntity unrelated = new ProjectionPositionEntity("unrelated", 3L);
            em.persist(unrelated);
            assertThat(repository.incrementBy("orders", 2L)).isEqualTo(12L);
            assertThat(repository.incrementBy("orders", 0L)).isEqualTo(12L);
            assertThat(repository.incrementBy("orders", -1L)).isEqualTo(11L);
            assertThat(em.contains(unrelated)).isTrue();
            unrelated.setNextEventNumber(4L);
            repository.resetToZero("orders");
            assertThat(em.contains(unrelated)).isTrue();
            assertThatThrownBy(() -> repository.incrementBy("absent", 1L))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("absent");
            em.getTransaction().commit();
        }
        try (EntityManagerFactory factory = factory("validate"); EntityManager em = factory.createEntityManager()) {
            ProjectionPositionRepository repository = new EntityManagerProjectionPositionRepository(em);
            assertPosition(repository, "orders", 0L);
            assertPosition(repository, "unrelated", 4L);
        }
    }

    /**
     * \u8c03\u7528 <code>directory.resolve()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param schemaAction \u4f20\u7ed9 <code>new Configuration</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>EntityManagerFactory</code> \u7ed3\u679c
     */
    private EntityManagerFactory factory(String schemaAction) {
        return new Configuration()
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:file:" + directory.resolve("positions"))
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", schemaAction)
                .addAnnotatedClass(ProjectionPositionEntity.class)
                .buildSessionFactory();
    }

    /**
     * \u521b\u5efa <code>new EntityManagerProjectionPositionRepository</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param em \u7528\u4e8e\u5b9a\u4f4d\u6d4b\u8bd5\u6570\u636e\u5e93\u8bb0\u5f55\u7684\u5b9e\u4f53\u7ba1\u7406\u5668
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new EntityManagerProjectionPositionRepository(em)</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private ProjectionPositionRepository repository(EntityManager em) throws Exception {
        return new EntityManagerProjectionPositionRepository(em);
    }
}

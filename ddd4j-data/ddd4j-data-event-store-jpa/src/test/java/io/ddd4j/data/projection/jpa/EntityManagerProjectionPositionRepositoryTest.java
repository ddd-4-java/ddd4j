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
    /**
     * JUnit 为本次测试分配的临时目录。
     */
    @TempDir
    Path directory;

    /**
     * 核对<code>position.getNextEventNumber()</code> 等于 <code>expected</code>。
     *
     * @param repository 当前测试访问的仓储实例
     * @param stream     传给 <code>assertThat()</code> 的 <code>String</code> 输入
     * @param expected   当前用例设定的期望值
     */
    private static void assertPosition(ProjectionPositionRepository repository, String stream, long expected) {
        assertThat(repository.findByStreamId(stream)).hasValueSatisfying(
                position -> assertThat(position.getNextEventNumber()).isEqualTo(expected));
    }

    /**
     * 验证被测调用 抛出 <code>UnsupportedOperationException.class</code>，<code>snapshot.get(0).getNextEventNumber()</code> 等于 <code>10L</code>，<code>repository.findByStreamId("missing")</code>为空。
     *
     * @throws Exception 读写测试存储或运行事务失败
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
     * 验证<code>repository.save(new DefaultProjectionPosition("x".repeat(251), 1L))</code> 抛出 <code>RuntimeException.class</code>，<code>repository.findByStreamId("missing")</code>为空，<code>repository.findAll()</code> 的大小为 <code>1</code>。
     *
     * @throws Exception 读写测试存储或运行事务失败
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
     * 验证<code>repository.save(new DefaultProjectionPosition("orders", 1L))</code> 抛出 <code>jakarta.persistence.TransactionRequiredException.class</code>，<code>em.getTransaction().isActive()</code>为假，<code>em.isOpen()</code>为真。
     *
     * @throws Exception 读写测试存储或运行事务失败
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

    /**
     * 验证<code>repository.incrementBy("orders", 2L)</code> 等于 <code>12L</code>，<code>repository.incrementBy("orders", 0L)</code> 等于 <code>12L</code>，<code>repository.incrementBy("absent", 1L)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
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
     * 调用 <code>directory.resolve()</code> 执行当前夹具操作。
     *
     * @param schemaAction 传给 <code>new Configuration</code> 的 <code>String</code> 输入
     * @return 提供给后续契约断言的 <code>EntityManagerFactory</code> 结果
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
     * 创建 <code>new EntityManagerProjectionPositionRepository</code> 供当前测试路径调用。
     *
     * @param em 用于定位测试数据库记录的实体管理器
     * @return 当前测试使用的结果，取自 <code>new EntityManagerProjectionPositionRepository(em)</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private ProjectionPositionRepository repository(EntityManager em) throws Exception {
        return new EntityManagerProjectionPositionRepository(em);
    }
}

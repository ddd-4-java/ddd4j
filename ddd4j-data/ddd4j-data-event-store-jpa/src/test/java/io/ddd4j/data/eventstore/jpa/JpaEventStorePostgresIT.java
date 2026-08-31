package io.ddd4j.data.eventstore.jpa;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.data.eventstore.StoredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JpaEventStore} PostgreSQL 容器集成测试（Task 4.4，CI 轨——Docker 缺席自动跳过）。
 *
 * <p><b>本轨在真实 PostgreSQL 上验证两个 H2 无法复刻的 PG 方言行为（计划 watch 点）：</b>
 * <ul>
 *   <li><b>watch①（聚合 JPQL×FOR UPDATE）</b>：
 *       {@link SpringDataStoredEventRepository#findCurrentVersion} 的
 *       {@code @Lock(PESSIMISTIC_WRITE)} 生成的聚合 JPQL＋FOR UPDATE 在 PG 上必须合法且
 *       真正串行化并发追加——由并发乐观锁用例（两线程同 expected 并发 append，恰一成功）
 *       真实验证。</li>
 *   <li><b>watch②（@Lob OID 大对象需事务）</b>：PG 把 {@code @Lob String} 落为 OID 大对象，
 *       读取需在活跃事务内——由 append/readBack 全往返用例（读路径无 @Transactional）验证。</li>
 * </ul>
 *
 * <p><b>预授权回退（仅 CI 首跑后按需执行，本地 Docker 缺席无法验证、不执行）：</b>
 * <ul>
 *   <li>若 PG 拒绝聚合 JPQL 加 FOR UPDATE：回退＝去掉 {@code @Lock}，以
 *       {@code uk_aggregate_version} 唯一约束为唯一串行化点（改
 *       {@code SpringDataStoredEventRepository} 并记入任务报告）；</li>
 *   <li>若读路径报 auto-commit 大对象错：回退＝{@code payload} 改
 *       {@code @JdbcTypeCode(SqlTypes.LONGVARCHAR)}（或 columnDefinition="text"），
 *       或读侧加 {@code @Transactional(readOnly=true)}。</li>
 * </ul>
 *
 * <p>{@code @Testcontainers(disabledWithoutDocker = true)}：本地无 Docker 时整轨跳过
 * （surefire 呈现为 skipped），不伪造 PG 绿；数据源经 {@code @DynamicPropertySource}
 * 覆盖 {@code application-test.yml} 的 JDBC 三项连接属性，其余装配（真实序列化器 Bean、
 * ddl-auto）与 H2 轨共用 {@link TestApp}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("JpaEventStore PostgreSQL 容器 IT（CI 轨，Docker 缺席自动跳过）")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = TestApp.class)
@ActiveProfiles("test")
class JpaEventStorePostgresIT {

    private static final String AGGREGATE_TYPE = "SampleAggregate";

    /** PG 16（Alpine）容器：静态共享，全类一个实例；Docker 缺席时本轨整体跳过、不启动。 */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JpaEventStore eventStore;

    @Autowired
    private SpringDataStoredEventRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 以容器实际连接参数覆盖 test profile 的 H2 数据源（驱动由 Boot 按 URL 推断）。
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void cleanStream() {
        repository.deleteAll();
    }

    /**
     * append/readBack 全量往返（watch② 真实验证点）：真实 PG＋真实 Jackson 序列化，
     * 读回侧重建 {@code StoredEvent}——若 PG OID 大对象拒绝 auto-commit 读取，
     * 本用例将在此失败（回退见类 javadoc）。
     */
    @Test
    void appendThenRead_全往返_应读回等值事件() {
        PgEvent first = new PgEvent("created");
        PgEvent second = new PgEvent("renamed");
        ItAggregateId aggregateId = new ItAggregateId("agg-pg-1");

        eventStore.append(AGGREGATE_TYPE, aggregateId, List.of(first, second), 0L);

        List<StoredEvent> stored = eventStore.read(AGGREGATE_TYPE, aggregateId);
        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(StoredEvent::version).containsExactly(1L, 2L);
        assertThat(stored).extracting(event -> event.eventId().asString())
                .containsExactly(first.getEventId().asString(), second.getEventId().asString());
        assertThat(stored).allSatisfy(event -> {
            assertThat(event.aggregateId().asString()).isEqualTo("agg-pg-1");
            assertThat(event.payload()).isInstanceOf(PgEvent.class);
            assertThat(event.position()).isPositive();
        });
        assertThat(((PgEvent) stored.get(0).payload()).getFact()).isEqualTo("created");
        assertThat(((PgEvent) stored.get(1).payload()).getFact()).isEqualTo("renamed");
    }

    @Test
    void payloadColumnShouldUsePortableTextType() {
        String dataType = jdbcTemplate.queryForObject("""
                select data_type
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'ddd4j_stored_event'
                  and column_name = 'payload'
                """, String.class);

        assertThat(dataType).isEqualTo("text");
    }

    /**
     * 并发乐观锁（watch① 真实验证点）：两线程以同一 {@code expectedVersion=0} 并发 append
     * 同一聚合，经 {@code findCurrentVersion} 的悲观写锁（FOR UPDATE）串行化后
     * <b>恰一成功</b>——败者得 {@code AggregateVersionConflictException}（语义层）或
     * {@code DataIntegrityViolationException}（并发窗口漏检时 uk 约束兜底），两者皆为
     * 正确行为；最终流内恰 1 条事件。
     */
    @Test
    void concurrentAppend_同expected两线程并发_应恰一成功() throws Exception {
        ItAggregateId aggregateId = new ItAggregateId("agg-pg-2");
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(
                    () -> appendConcurrently(aggregateId, startGate, successes));
            Future<?> second = pool.submit(
                    () -> appendConcurrently(aggregateId, startGate, successes));
            startGate.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(eventStore.read(AGGREGATE_TYPE, aggregateId)).hasSize(1);
    }

    private void appendConcurrently(ItAggregateId aggregateId, CountDownLatch startGate,
                                    AtomicInteger successes) {
        try {
            startGate.await();
            eventStore.append(AGGREGATE_TYPE, aggregateId, List.of(new PgEvent("raced")), 0L);
            successes.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
            // 败者：AggregateVersionConflictException（FOR UPDATE 串行化后版本已前移）
            // 或 DataIntegrityViolationException（uk_aggregate_version 兜底）——皆为正确行为
        }
    }

    /**
     * PG 轨样本事件：带一个业务字段的 payload 载体（真实 Jackson 往返）。
     */
    static final class PgEvent extends DomainEvent<StringEntityId> {

        private String fact;

        PgEvent() {
            super("agg-pg");
        }

        PgEvent(String fact) {
            this();
            this.fact = fact;
        }

        public String getFact() {
            return fact;
        }

        public void setFact(String fact) {
            this.fact = fact;
        }
    }

    /**
     * PG 轨自有字符串聚合根标识（契约与 AggregateRootId 三方法一致）。
     */
    private record ItAggregateId(String value) implements AggregateRootId {

        private static final EntityType TYPE = new StringEntityType("SampleAggregate");

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }
    }
}

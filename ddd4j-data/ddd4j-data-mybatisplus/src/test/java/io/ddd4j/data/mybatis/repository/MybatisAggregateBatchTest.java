package io.ddd4j.data.mybatis.repository;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.MapperProxyMetadata;
import com.baomidou.mybatisplus.core.toolkit.MybatisUtils;
import io.ddd4j.annotation.orm.OnCreate;
import io.ddd4j.annotation.orm.OnUpdate;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * 领域批量操作直接复用 Mapper，并保留填充、回写和 JDBC 计数。
 */
class MybatisAggregateBatchTest {
    /**
     * 记录被测组件是否访问其元数据入口的状态。
     */
    private MockedStatic<MybatisUtils> metadataAccess;
    /**
     * 测试中用于执行 Mapper 操作的数据库会话。
     */
    private SqlSession session;

    /**
     * 通过 <code>List.of()</code> 取得当前测试需要的结果。
     *
     * @param counts 传给 <code>result.setUpdateCounts()</code> 的 <code>int[]</code> 输入
     * @return 当前测试使用的结果，取自 <code>List.of(result)</code>
     */
    private static List<BatchResult> results(int... counts) {
        BatchResult result = new BatchResult(null, "batch");
        result.setUpdateCounts(counts);
        return List.of(result);
    }

    /**
     * 测试执行前执行 <code>mock()</code>、<code>metadata.getSqlSession()</code>、<code>mockStatic()</code>、<code>MybatisUtils.getMapperProxy()</code> 完成资源准备。
     */
    @BeforeEach
    void configureMapperMetadata() {
        session = mock(SqlSession.class);
        MapperProxyMetadata metadata = mock(MapperProxyMetadata.class);
        when(metadata.getSqlSession()).thenReturn(session);
        metadataAccess = mockStatic(MybatisUtils.class);
        metadataAccess.when(() -> MybatisUtils.getMapperProxy(any())).thenReturn(metadata);
    }

    /**
     * 测试后依次关闭<code>metadataAccess</code>。
     */
    @AfterEach
    void releaseMapperMetadata() {
        metadataAccess.close();
    }

    /**
     * 验证<code>value.getCreatedAt()</code>非空，<code>first.getId()</code> 等于 <code>100L</code>，<code>first.getCreatedAt()</code>非空。
     */
    @Test
    void saveBatchCopiesGeneratedIdsAndCreateFieldsBackToAggregates() {
        Mapper mapper = mock(Mapper.class);
        when(mapper.insert(anyCollection())).thenAnswer(call -> {
            Collection<Po> values = call.getArgument(0);
            long id = 100L;
            for (Po value : values) {
                assertThat(value.getCreatedAt()).isNotNull();
                value.setId(id++);
            }
            return results(1, 1);
        });
        Model first = new Model(null, "first");
        Model second = new Model(null, "second");

        assertThat(new Repo(mapper).saveBatch(List.of(first, second))).containsExactly(first, second);
        assertThat(first.getId()).isEqualTo(100L);
        assertThat(second.getId()).isEqualTo(101L);
        assertThat(first.getCreatedAt()).isNotNull();
        verify(mapper).insert(anyCollection());
        verify(session).clearCache();
    }

    /**
     * 验证<code>value.getUpdatedAt()</code>非空，<code>first.getUpdatedAt()</code>非空。
     */
    @Test
    void updateBatchCountsActualRowsAndPreservesUpdateFill() {
        Mapper mapper = mock(Mapper.class);
        when(mapper.updateById(anyCollection())).thenAnswer(call -> {
            Collection<Po> values = call.getArgument(0);
            assertThat(values).allSatisfy(value -> assertThat(value.getUpdatedAt()).isNotNull());
            return results(1, 0, Statement.SUCCESS_NO_INFO);
        });
        Model first = new Model(1L, "changed");
        assertThat(new Repo(mapper).updateBatchById(List.of(first,
                new Model(404L, "missing"), new Model(2L, "unknown count")))).isEqualTo(2);
        assertThat(first.getUpdatedAt()).isNotNull();
        verify(session).clearCache();
    }

    /**
     * 验证<code>values.get(0).getUpdatedAt()</code>非空，<code>values.get(1).getCreatedAt()</code>非空。
     */
    @Test
    void upsertDelegatesLongAndInheritedIdsToComponent() {
        Mapper mapper = mock(Mapper.class);
        Po existing = new Po();
        existing.setId(1L);
        when(mapper.selectById(1L)).thenReturn(existing);
        when(mapper.insertOrUpdate(anyCollection())).thenAnswer(call -> {
            List<Po> values = List.copyOf(call.<Collection<Po>>getArgument(0));
            assertThat(values.get(0).getUpdatedAt()).isNotNull();
            assertThat(values.get(1).getCreatedAt()).isNotNull();
            return results(1, 1);
        });
        assertThat(new Repo(mapper).insertOrUpdateBatch(List.of(
                new Model(1L, "update"), new Model(2L, "insert")))).isEqualTo(2);
        verify(mapper).insertOrUpdate(anyCollection());
        verify(session).clearCache();
    }

    /**
     * 验证<code>repo.saveBatch(List.of())</code>为空，<code>repo.updateBatchById(List.of())</code>为零，<code>repo.insertOrUpdateBatch(List.of())</code>为零。
     */
    @Test
    void emptyBatchesDoNotTouchMapper() {
        Mapper mapper = mock(Mapper.class);
        Repo repo = new Repo(mapper);
        assertThat(repo.saveBatch(List.of())).isEmpty();
        assertThat(repo.updateBatchById(List.of())).isZero();
        assertThat(repo.insertOrUpdateBatch(List.of())).isZero();
        verifyNoInteractions(mapper);
    }

    /**
     * 验证<code>repo.saveBatch(values)</code> 抛出 <code>NullPointerException.class</code>，<code>repo.updateBatchById(values)</code> 抛出 <code>NullPointerException.class</code>，<code>repo.insertOrUpdateBatch(values)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void nullElementsAreRejectedBeforeWriting() {
        Mapper mapper = mock(Mapper.class);
        Repo repo = new Repo(mapper);
        List<Model> values = Arrays.asList(new Model(1L, "valid"), null);
        assertThatThrownBy(() -> repo.saveBatch(values)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.updateBatchById(values)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repo.insertOrUpdateBatch(values)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(mapper);
    }

    /**
     * 验证被测调用 抛出 <code>org.apache.ibatis.exceptions.PersistenceException.class</code>。
     */
    @Test
    void failedJdbcStatementCannotBeReportedAsSuccessfulCount() {
        Mapper mapper = mock(Mapper.class);
        when(mapper.updateById(anyCollection())).thenReturn(results(Statement.EXECUTE_FAILED));
        assertThatThrownBy(() -> new Repo(mapper).updateBatchById(List.of(new Model(1L, "value"))))
                .isInstanceOf(org.apache.ibatis.exceptions.PersistenceException.class);
    }

    /**
     * 替代数据库 Mapper，供仓储测试检查 CRUD 调用和映射结果。
     */
    public interface Mapper extends BaseMapper<Po> {
    }

    /**
     * 暴露聚合保存或回填入口的测试专用仓储。
     */
    public static class Repo extends MybatisAggregateRepository<Mapper, Model, Po, ModelQuery, Long> {
        /**
         * 使用 <code>super()</code> 初始化 <code>Repo</code> 测试夹具。
         *
         * @param mapper 当前测试登记或使用的持久化映射组件
         */
        Repo(Mapper mapper) {
            super(mapper);
        }
    }

    /**
     * 用于控制查询条件或路由类型的测试查询。
     */
    public static class ModelQuery extends Query<Model> {
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Model extends AggregateRoot<Long> {
        /**
         * 用于构造和定位测试记录的主键。
         */
        private Long id;
        /**
         * 测试对象的显示名称。
         */
        private String name;
        /**
         * 记录创建的时间。
         */
        private LocalDateTime createdAt;
        /**
         * 最近更新记录的时间。
         */
        private LocalDateTime updatedAt;

        /**
         * 以给定状态构造 <code>Model</code> 测试对象。
         *
         * @param id   用于构造和定位测试记录的主键
         * @param name 测试对象的显示名称
         */
        Model(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public Long id() {
            return id;
        }
    }

    /**
     * 使用 <code>id</code> 验证领域和持久化对象之间转换。
     */
    @Getter
    @Setter
    public static class BasePo {
        /**
         * 用于构造和定位测试记录的主键。
         */
        @TableId
        private Long id;
    }

    /**
     * 将 <code>name</code>、<code>createdAt</code>、<code>updatedAt</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    @Getter
    @Setter
    public static class Po extends BasePo {
        /**
         * 测试对象的显示名称。
         */
        private String name;
        /**
         * 记录创建的时间。
         */
        @OnCreate
        private LocalDateTime createdAt;
        /**
         * 最近更新记录的时间。
         */
        @OnUpdate
        private LocalDateTime updatedAt;
    }
}

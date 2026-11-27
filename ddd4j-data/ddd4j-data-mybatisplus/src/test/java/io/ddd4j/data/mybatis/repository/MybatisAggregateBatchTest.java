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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 领域批量操作直接复用 Mapper，并保留填充、回写和 JDBC 计数。 */
class MybatisAggregateBatchTest {
    /** \u8bb0\u5f55\u88ab\u6d4b\u7ec4\u4ef6\u662f\u5426\u8bbf\u95ee\u5176\u5143\u6570\u636e\u5165\u53e3\u7684\u72b6\u6001\u3002 */
    private MockedStatic<MybatisUtils> metadataAccess;
    /** \u6d4b\u8bd5\u4e2d\u7528\u4e8e\u6267\u884c Mapper \u64cd\u4f5c\u7684\u6570\u636e\u5e93\u4f1a\u8bdd\u3002 */
    private SqlSession session;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>mock()</code>\u3001<code>metadata.getSqlSession()</code>\u3001<code>mockStatic()</code>\u3001<code>MybatisUtils.getMapperProxy()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void configureMapperMetadata() {
        session = mock(SqlSession.class);
        MapperProxyMetadata metadata = mock(MapperProxyMetadata.class);
        when(metadata.getSqlSession()).thenReturn(session);
        metadataAccess = mockStatic(MybatisUtils.class);
        metadataAccess.when(() -> MybatisUtils.getMapperProxy(any())).thenReturn(metadata);
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u5173\u95ed<code>metadataAccess</code>\u3002 */
    @AfterEach
    void releaseMapperMetadata() {
        metadataAccess.close();
    }

    /** \u9a8c\u8bc1<code>value.getCreatedAt()</code>\u975e\u7a7a\uff0c<code>first.getId()</code> \u7b49\u4e8e <code>100L</code>\uff0c<code>first.getCreatedAt()</code>\u975e\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>value.getUpdatedAt()</code>\u975e\u7a7a\uff0c<code>first.getUpdatedAt()</code>\u975e\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>values.get(0).getUpdatedAt()</code>\u975e\u7a7a\uff0c<code>values.get(1).getCreatedAt()</code>\u975e\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>repo.saveBatch(List.of())</code>\u4e3a\u7a7a\uff0c<code>repo.updateBatchById(List.of())</code>\u4e3a\u96f6\uff0c<code>repo.insertOrUpdateBatch(List.of())</code>\u4e3a\u96f6\u3002 */
    @Test
    void emptyBatchesDoNotTouchMapper() {
        Mapper mapper = mock(Mapper.class);
        Repo repo = new Repo(mapper);
        assertThat(repo.saveBatch(List.of())).isEmpty();
        assertThat(repo.updateBatchById(List.of())).isZero();
        assertThat(repo.insertOrUpdateBatch(List.of())).isZero();
        verifyNoInteractions(mapper);
    }

    /** \u9a8c\u8bc1<code>repo.saveBatch(values)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>repo.updateBatchById(values)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>repo.insertOrUpdateBatch(values)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>org.apache.ibatis.exceptions.PersistenceException.class</code>\u3002 */
    @Test
    void failedJdbcStatementCannotBeReportedAsSuccessfulCount() {
        Mapper mapper = mock(Mapper.class);
        when(mapper.updateById(anyCollection())).thenReturn(results(Statement.EXECUTE_FAILED));
        assertThatThrownBy(() -> new Repo(mapper).updateBatchById(List.of(new Model(1L, "value"))))
                .isInstanceOf(org.apache.ibatis.exceptions.PersistenceException.class);
    }

    /**
     * \u901a\u8fc7 <code>List.of()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param counts \u4f20\u7ed9 <code>result.setUpdateCounts()</code> \u7684 <code>int[]</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>List.of(result)</code>
     */
    private static List<BatchResult> results(int... counts) {
        BatchResult result = new BatchResult(null, "batch");
        result.setUpdateCounts(counts);
        return List.of(result);
    }

    /** \u66ff\u4ee3\u6570\u636e\u5e93 Mapper\uff0c\u4f9b\u4ed3\u50a8\u6d4b\u8bd5\u68c0\u67e5 CRUD \u8c03\u7528\u548c\u6620\u5c04\u7ed3\u679c\u3002 */
    public interface Mapper extends BaseMapper<Po> { }

    /** \u66b4\u9732\u805a\u5408\u4fdd\u5b58\u6216\u56de\u586b\u5165\u53e3\u7684\u6d4b\u8bd5\u4e13\u7528\u4ed3\u50a8\u3002 */
    public static class Repo extends MybatisAggregateRepository<Mapper, Model, Po, ModelQuery, Long> {
        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>Repo</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param mapper \u5f53\u524d\u6d4b\u8bd5\u767b\u8bb0\u6216\u4f7f\u7528\u7684\u6301\u4e45\u5316\u6620\u5c04\u7ec4\u4ef6
         */
        Repo(Mapper mapper) { super(mapper); }
    }

    /** \u7528\u4e8e\u63a7\u5236\u67e5\u8be2\u6761\u4ef6\u6216\u8def\u7531\u7c7b\u578b\u7684\u6d4b\u8bd5\u67e5\u8be2\u3002 */
    public static class ModelQuery extends Query<Model> { }

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Model extends AggregateRoot<Long> {
        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        private Long id;
        /** \u6d4b\u8bd5\u5bf9\u8c61\u7684\u663e\u793a\u540d\u79f0\u3002 */
        private String name;
        /** \u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4\u3002 */
        private LocalDateTime createdAt;
        /** \u6700\u8fd1\u66f4\u65b0\u8bb0\u5f55\u7684\u65f6\u95f4\u3002 */
        private LocalDateTime updatedAt;
        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>Model</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param id \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e
         * @param name \u6d4b\u8bd5\u5bf9\u8c61\u7684\u663e\u793a\u540d\u79f0
         */
        Model(Long id, String name) { this.id = id; this.name = name; }
        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override
        public Long id() { return id; }
    }

    /** \u4f7f\u7528 <code>id</code> \u9a8c\u8bc1\u9886\u57df\u548c\u6301\u4e45\u5316\u5bf9\u8c61\u4e4b\u95f4\u8f6c\u6362\u3002 */
    @Getter
    @Setter
    public static class BasePo {
        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        @TableId
        private Long id;
    }

    /** \u5c06 <code>name</code>\u3001<code>createdAt</code>\u3001<code>updatedAt</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    @Getter
    @Setter
    public static class Po extends BasePo {
        /** \u6d4b\u8bd5\u5bf9\u8c61\u7684\u663e\u793a\u540d\u79f0\u3002 */
        private String name;
        /** \u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4\u3002 */
        @OnCreate
        private LocalDateTime createdAt;
        /** \u6700\u8fd1\u66f4\u65b0\u8bb0\u5f55\u7684\u65f6\u95f4\u3002 */
        @OnUpdate
        private LocalDateTime updatedAt;
    }
}

package io.ddd4j.data.mybatis.repository;

import java.util.ArrayList;

import java.util.Collections;

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
    private MockedStatic<MybatisUtils> metadataAccess;
    private SqlSession session;

    @BeforeEach
    void configureMapperMetadata() {
        session = mock(SqlSession.class);
        MapperProxyMetadata metadata = mock(MapperProxyMetadata.class);
        when(metadata.getSqlSession()).thenReturn(session);
        metadataAccess = mockStatic(MybatisUtils.class);
        metadataAccess.when(() -> MybatisUtils.getMapperProxy(any())).thenReturn(metadata);
    }

    @AfterEach
    void releaseMapperMetadata() {
        metadataAccess.close();
    }

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

        assertThat(new Repo(mapper).saveBatch(Arrays.asList(first, second))).containsExactly(first, second);
        assertThat(first.getId()).isEqualTo(100L);
        assertThat(second.getId()).isEqualTo(101L);
        assertThat(first.getCreatedAt()).isNotNull();
        verify(mapper).insert(anyCollection());
        verify(session).clearCache();
    }

    @Test
    void updateBatchCountsActualRowsAndPreservesUpdateFill() {
        Mapper mapper = mock(Mapper.class);
        when(mapper.updateById(anyCollection())).thenAnswer(call -> {
            Collection<Po> values = call.getArgument(0);
            assertThat(values).allSatisfy(value -> assertThat(value.getUpdatedAt()).isNotNull());
            return results(1, 0, Statement.SUCCESS_NO_INFO);
        });
        Model first = new Model(1L, "changed");
        assertThat(new Repo(mapper).updateBatchById(Arrays.asList(first,
                new Model(404L, "missing"), new Model(2L, "unknown count")))).isEqualTo(2);
        assertThat(first.getUpdatedAt()).isNotNull();
        verify(session).clearCache();
    }

    @Test
    void upsertDelegatesLongAndInheritedIdsToComponent() {
        Mapper mapper = mock(Mapper.class);
        Po existing = new Po();
        existing.setId(1L);
        when(mapper.selectById(1L)).thenReturn(existing);
        when(mapper.insertOrUpdate(anyCollection())).thenAnswer(call -> {
            List<Po> values = new ArrayList<>(call.<Collection<Po>>getArgument(0));
            assertThat(values.get(0).getUpdatedAt()).isNotNull();
            assertThat(values.get(1).getCreatedAt()).isNotNull();
            return results(1, 1);
        });
        assertThat(new Repo(mapper).insertOrUpdateBatch(Arrays.asList(
                new Model(1L, "update"), new Model(2L, "insert")))).isEqualTo(2);
        verify(mapper).insertOrUpdate(anyCollection());
        verify(session).clearCache();
    }

    @Test
    void emptyBatchesDoNotTouchMapper() {
        Mapper mapper = mock(Mapper.class);
        Repo repo = new Repo(mapper);
        assertThat(repo.saveBatch(Collections.emptyList())).isEmpty();
        assertThat(repo.updateBatchById(Collections.emptyList())).isZero();
        assertThat(repo.insertOrUpdateBatch(Collections.emptyList())).isZero();
        verifyNoInteractions(mapper);
    }

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

    @Test
    void failedJdbcStatementCannotBeReportedAsSuccessfulCount() {
        Mapper mapper = mock(Mapper.class);
        when(mapper.updateById(anyCollection())).thenReturn(results(Statement.EXECUTE_FAILED));
        assertThatThrownBy(() -> new Repo(mapper).updateBatchById(Arrays.asList(new Model(1L, "value"))))
                .isInstanceOf(org.apache.ibatis.exceptions.PersistenceException.class);
    }

    private static List<BatchResult> results(int... counts) {
        BatchResult result = new BatchResult(null, "batch");
        result.setUpdateCounts(counts);
        return Arrays.asList(result);
    }

    public interface Mapper extends BaseMapper<Po> { }

    public static class Repo extends MybatisAggregateRepository<Mapper, Model, Po, ModelQuery, Long> {
        Repo(Mapper mapper) { super(mapper); }
    }

    public static class ModelQuery extends Query<Model> { }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Model extends AggregateRoot<Long> {
        private Long id;
        private String name;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        Model(Long id, String name) { this.id = id; this.name = name; }
        @Override
        public Long id() { return id; }
    }

    @Getter
    @Setter
    public static class BasePo {
        @TableId
        private Long id;
    }

    @Getter
    @Setter
    public static class Po extends BasePo {
        private String name;
        @OnCreate
        private LocalDateTime createdAt;
        @OnUpdate
        private LocalDateTime updatedAt;
    }
}

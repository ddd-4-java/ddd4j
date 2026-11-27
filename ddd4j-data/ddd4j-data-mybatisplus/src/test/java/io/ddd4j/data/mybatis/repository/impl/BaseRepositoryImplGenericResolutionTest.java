package io.ddd4j.data.mybatis.repository.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseRepositoryImplGenericResolutionTest {

    @Test
    void shouldResolveDomainPersistenceAndQueryTypesBehindMapperGeneric() {
        TestRepository repository = new TestRepository();

        assertEquals(TestAggregate.class, repository.modelType());
        assertEquals(TestPo.class, repository.persistenceType());
        assertEquals(TestQuery.class, repository.queryType());
    }

    interface TestMapper extends BaseMapper<TestPo> {
    }

    static final class TestRepository extends BaseRepositoryImpl<
            TestMapper, TestAggregate, TestPo, TestQuery, Long> {
        Class<TestAggregate> modelType() {
            return resolveModelClass();
        }

        Class<TestPo> persistenceType() {
            return resolvePersistenceObjectClass();
        }

        Class<? extends Query<TestAggregate>> queryType() {
            return resolveQueryClass();
        }
    }

    static final class TestAggregate extends AggregateRoot<Long> {
        @Override
        public Long id() {
            return 1L;
        }
    }

    static final class TestPo {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    static final class TestQuery extends Query<TestAggregate> {
    }
}

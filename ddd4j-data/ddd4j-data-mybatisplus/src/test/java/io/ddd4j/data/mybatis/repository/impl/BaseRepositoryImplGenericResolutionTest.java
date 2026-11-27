package io.ddd4j.data.mybatis.repository.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u901a\u8fc7 <code>repository.modelType()</code>\u3001<code>repository.persistenceType()</code>\u3001<code>repository.queryType()</code> \u9a8c\u8bc1 <code>BaseRepositoryImplGenericResolution</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class BaseRepositoryImplGenericResolutionTest {

    /** \u9a8c\u8bc1<code>repository.modelType()</code> \u7b49\u4e8e <code>TestAggregate.class</code>\uff0c<code>repository.persistenceType()</code> \u7b49\u4e8e <code>TestPo.class</code>\uff0c<code>repository.queryType()</code> \u7b49\u4e8e <code>TestQuery.class</code>\u3002 */
    @Test
    void shouldResolveDomainPersistenceAndQueryTypesBehindMapperGeneric() {
        TestRepository repository = new TestRepository();

        assertEquals(TestAggregate.class, repository.modelType());
        assertEquals(TestPo.class, repository.persistenceType());
        assertEquals(TestQuery.class, repository.queryType());
    }

    /** \u66ff\u4ee3\u6570\u636e\u5e93 Mapper\uff0c\u4f9b\u4ed3\u50a8\u6d4b\u8bd5\u68c0\u67e5 CRUD \u8c03\u7528\u548c\u6620\u5c04\u7ed3\u679c\u3002 */
    interface TestMapper extends BaseMapper<TestPo> {
    }

    /** \u5b9e\u73b0 <code>BaseRepositoryImpl</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>modelType()</code>\u3001<code>persistenceType()</code>\u3001<code>queryType()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    static final class TestRepository extends BaseRepositoryImpl<
            TestMapper, TestAggregate, TestPo, TestQuery, Long> {
        /**
         * \u901a\u8fc7 <code>resolveModelClass()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>resolveModelClass()</code>
         */
        Class<TestAggregate> modelType() {
            return resolveModelClass();
        }

        /**
         * \u901a\u8fc7 <code>resolvePersistenceObjectClass()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>resolvePersistenceObjectClass()</code>
         */
        Class<TestPo> persistenceType() {
            return resolvePersistenceObjectClass();
        }

        /**
         * \u901a\u8fc7 <code>resolveQueryClass()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>resolveQueryClass()</code>
         */
        Class<? extends Query<TestAggregate>> queryType() {
            return resolveQueryClass();
        }
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\uff0c\u4f9b\u9a8c\u8bc1\u805a\u5408\u6807\u8bc6\u548c\u4ed3\u50a8\u6216\u4e8b\u4ef6\u5904\u7406\u884c\u4e3a\u3002 */
    static final class TestAggregate extends AggregateRoot<Long> {
        /**
         * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>id</code> \u8fd4\u56de\u503c\u3002
         * @return <code>1L</code>
         */
        @Override
        public Long id() {
            return 1L;
        }
    }

    /** \u5c06 <code>id</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    static final class TestPo {
        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        private Long id;

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        public Long getId() {
            return id;
        }

        /**
         * \u8bbe\u7f6e<code>id</code> \u5b57\u6bb5\u3002
         * @param id \u8981\u5199\u5165\u7684\u503c
         */
        public void setId(Long id) {
            this.id = id;
        }
    }

    /** \u7528\u4e8e\u63a7\u5236\u67e5\u8be2\u6761\u4ef6\u6216\u8def\u7531\u7c7b\u578b\u7684\u6d4b\u8bd5\u67e5\u8be2\u3002 */
    static final class TestQuery extends Query<TestAggregate> {
    }
}

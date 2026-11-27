package io.ddd4j.data.mybatis.repository.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过 <code>repository.modelType()</code>、<code>repository.persistenceType()</code>、<code>repository.queryType()</code> 验证 <code>BaseRepositoryImplGenericResolution</code> 的运行行为。
 */
class BaseRepositoryImplGenericResolutionTest {

    /**
     * 验证<code>repository.modelType()</code> 等于 <code>TestAggregate.class</code>，<code>repository.persistenceType()</code> 等于 <code>TestPo.class</code>，<code>repository.queryType()</code> 等于 <code>TestQuery.class</code>。
     */
    @Test
    void shouldResolveDomainPersistenceAndQueryTypesBehindMapperGeneric() {
        TestRepository repository = new TestRepository();

        assertEquals(TestAggregate.class, repository.modelType());
        assertEquals(TestPo.class, repository.persistenceType());
        assertEquals(TestQuery.class, repository.queryType());
    }

    /**
     * 替代数据库 Mapper，供仓储测试检查 CRUD 调用和映射结果。
     */
    interface TestMapper extends BaseMapper<TestPo> {
    }

    /**
     * 实现 <code>BaseRepositoryImpl</code>，供测试调用 <code>modelType()</code>、<code>persistenceType()</code>、<code>queryType()</code> 并观察结果。
     */
    static final class TestRepository extends BaseRepositoryImpl<
            TestMapper, TestAggregate, TestPo, TestQuery, Long> {
        /**
         * 通过 <code>resolveModelClass()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>resolveModelClass()</code>
         */
        Class<TestAggregate> modelType() {
            return resolveModelClass();
        }

        /**
         * 通过 <code>resolvePersistenceObjectClass()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>resolvePersistenceObjectClass()</code>
         */
        Class<TestPo> persistenceType() {
            return resolvePersistenceObjectClass();
        }

        /**
         * 通过 <code>resolveQueryClass()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>resolveQueryClass()</code>
         */
        Class<? extends Query<TestAggregate>> queryType() {
            return resolveQueryClass();
        }
    }

    /**
     * 测试专用聚合，供验证聚合标识和仓储或事件处理行为。
     */
    static final class TestAggregate extends AggregateRoot<Long> {
        /**
         * 为当前用例提供固定的 <code>id</code> 返回值。
         *
         * @return <code>1L</code>
         */
        @Override
        public Long id() {
            return 1L;
        }
    }

    /**
     * 将 <code>id</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    static final class TestPo {
        /**
         * 用于构造和定位测试记录的主键。
         */
        private Long id;

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        public Long getId() {
            return id;
        }

        /**
         * 设置<code>id</code> 字段。
         *
         * @param id 要写入的值
         */
        public void setId(Long id) {
            this.id = id;
        }
    }

    /**
     * 用于控制查询条件或路由类型的测试查询。
     */
    static final class TestQuery extends Query<TestAggregate> {
    }
}

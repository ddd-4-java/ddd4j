package io.ddd4j.data.mybatis.repository.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class BaseRepositoryImplGenericResolutionTest {

    @Test
    void shouldResolveDomainPersistenceAndQueryTypesBehindMapperGeneric() {
        TestRepository repository = new TestRepository();

        assertEquals(TestAggregate.class, repository.modelType());
        assertEquals(TestPo.class, repository.persistenceType());
        assertEquals(TestQuery.class, repository.queryType());
    }

    /** 测试用 Mapper 接口，泛型声明持久化对象类型。 */
    interface TestMapper extends BaseMapper<TestPo> {
    }

    /** 测试用仓储实现，验证泛型参数解析结果。 */
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

    /** 测试用聚合根，标识固定为 1。 */
    static final class TestAggregate extends AggregateRoot<Long> {
        /**
         * id() 方法。
         * @return 结果数值。
         */
        @Override
        public Long id() {
            return 1L;
        }
    }

    /** 测试用持久化对象。 */
    static final class TestPo {
        private Long id;

        /**
         * 获取值。
         * @return 结果数值。
         */
        public Long getId() {
            return id;
        }

        /**
         * 设置值。
         * @param id 标识值。
         */
        public void setId(Long id) {
            this.id = id;
        }
    }

    /** 测试用查询对象，绑定测试聚合根类型。 */
    static final class TestQuery extends Query<TestAggregate> {
    }
}

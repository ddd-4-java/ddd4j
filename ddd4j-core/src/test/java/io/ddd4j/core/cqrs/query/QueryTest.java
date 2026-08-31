package io.ddd4j.core.cqrs.query;

import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.util.SFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryTest {
    @Test
    void shouldBuildTypedConditionsAndPagination() {
        TestQuery query = new TestQuery()
                .eq((SFunction<TestAggregate, String>) TestAggregate::getName, "Alice")
                .orderByDesc((SFunction<TestAggregate, String>) TestAggregate::getName)
                .current(2).size(25).ignoreTenantId();
        assertEquals(1, query.getConditions().size());
        assertEquals("name", query.getConditions().get(0).property());
        assertEquals("=", query.getConditions().get(0).getOperator());
        assertEquals("DESC", query.getOrderByConditions().get(0).getOperator());
        assertEquals(2L, query.getCurrent());
        assertEquals(25L, query.getSize());
        assertTrue(query.isIgnoreTenantId());
    }
    private static final class TestQuery extends Query<TestAggregate> {
    }

    private static final class TestAggregate extends AggregateRoot<StringEntityId> {
        @Override
        public StringEntityId id() {
            return new StringEntityId("id");
        }

        public String getName() {
            return "name";
        }
    }
}

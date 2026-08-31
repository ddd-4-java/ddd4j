package io.ddd4j.core.cqrs.query;

import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.util.SFunction;
import java.util.Arrays;
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

    @Test
    void shouldBuildConditionalRangeCollectionNullAndUpdateOperations() {
        TestQuery query = new TestQuery()
                .eq(false, (SFunction<TestAggregate, String>) TestAggregate::getName, "ignored")
                .likeLeft((SFunction<TestAggregate, String>) TestAggregate::getName, "suffix")
                .likeRight((SFunction<TestAggregate, String>) TestAggregate::getName, "prefix")
                .notLike((SFunction<TestAggregate, String>) TestAggregate::getName, "blocked")
                .between((SFunction<TestAggregate, String>) TestAggregate::getName, "A", "Z")
                .in((SFunction<TestAggregate, String>) TestAggregate::getName, Arrays.asList("A", "B"))
                .notIn((SFunction<TestAggregate, String>) TestAggregate::getName, Arrays.asList("C"))
                .isNull((SFunction<TestAggregate, String>) TestAggregate::getName)
                .isNotNull((SFunction<TestAggregate, String>) TestAggregate::getName)
                .set((SFunction<TestAggregate, String>) TestAggregate::getName, "updated")
                .orderByAsc(false, (SFunction<TestAggregate, String>) TestAggregate::getName)
                .ignorePage();

        assertEquals(9, query.getConditions().size());
        assertEquals("LIKE_LEFT", query.getConditions().get(0).getOperator());
        assertEquals("LIKE_RIGHT", query.getConditions().get(1).getOperator());
        assertEquals("NOT_LIKE", query.getConditions().get(2).getOperator());
        assertEquals(">=", query.getConditions().get(3).getOperator());
        assertEquals("<=", query.getConditions().get(4).getOperator());
        assertEquals("IN", query.getConditions().get(5).getOperator());
        assertEquals(Arrays.asList("A", "B"), query.getConditions().get(5).getValue());
        assertEquals("NOT_IN", query.getConditions().get(6).getOperator());
        assertEquals("IS_NULL", query.getConditions().get(7).getOperator());
        assertEquals("IS_NOT_NULL", query.getConditions().get(8).getOperator());
        assertEquals(1, query.getSetOperations().size());
        assertEquals(-1L, query.getSize());
        assertTrue(query.getOrderByConditions().isEmpty());
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

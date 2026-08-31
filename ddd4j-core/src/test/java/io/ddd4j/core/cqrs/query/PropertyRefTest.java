package io.ddd4j.core.cqrs.query;

import io.ddd4j.core.util.SFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PropertyRefTest {
    @Test
    void shouldResolveSerializableGetterReferenceToDomainProperty() {
        PropertyRef property = PropertyRef.domain((SFunction<TestAggregate, String>) TestAggregate::getName);
        assertEquals(PropertySpace.DOMAIN, property.getSpace());
        assertEquals(TestAggregate.class, property.getOwnerType());
        assertEquals("name", property.getProperty());
    }
    private static final class TestAggregate {
        private final String name = "name";
        public String getName() { return name; }
    }
}

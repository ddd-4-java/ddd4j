package io.ddd4j.core.ddd.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EntityIdPathTest {
    @Test
    void shouldRoundTripTypedPathAndExposeHierarchy() {
        EntityIdPath path = new EntityIdPath(new StringEntityId("order-1"), new StringEntityId("line-1"));
        EntityIdPath restored = EntityIdPath.valueOf(path.asString());
        assertEquals("String:order-1/String:line-1", restored.asString());
        assertEquals("order-1", restored.first().asString());
        assertEquals("line-1", restored.last().asString());
        assertEquals("String:order-1", restored.parent().asString());
        assertEquals("String:line-1", restored.rest().asString());
        assertNull(restored.parent().parent());
    }
}

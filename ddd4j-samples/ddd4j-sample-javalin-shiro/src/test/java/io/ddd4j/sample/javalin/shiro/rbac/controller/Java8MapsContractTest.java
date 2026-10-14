package io.ddd4j.sample.javalin.shiro.rbac.controller;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Java 8 响应 Map 与高线 Map.of 的共同输入及不可变性契约。 */
class Java8MapsContractTest {

    @Test
    void createsResponseMap() {
        Map<String, Object> values = Map.of("name", "alice", "count", 2);
        assertEquals("alice", values.get("name"));
        assertEquals(2, values.get("count"));
    }

    @Test
    void rejectsNullKeysAndValues() {
        assertThrows(NullPointerException.class, () -> Map.of(null, "value"));
        assertThrows(NullPointerException.class, () -> Map.of("key", null));
    }

    @Test
    void rejectsDuplicateKeys() {
        assertThrows(IllegalArgumentException.class, () -> Map.of("key", 1, "key", 2));
    }

    @Test
    void returnedMapCannotBeMutated() {
        Map<String, Object> values = Map.of("name", "alice");
        assertThrows(UnsupportedOperationException.class, () -> values.put("name", "bob"));
    }
}

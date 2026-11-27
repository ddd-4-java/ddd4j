package io.ddd4j.sample.javalin.satoken.rbac.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Java 8 响应 Map 与高线 Map.of 的共同输入及不可变性契约。
 */
class Java8MapsContractTest {

    /**
     * 验证<code>values.get("name")</code> 等于 <code>"alice"</code>，<code>values.get("count")</code> 等于 <code>2</code>。
     */
    @Test
    void createsResponseMap() {
        Map<String, Object> values = Map.of("name", "alice", "count", 2);
        assertEquals("alice", values.get("name"));
        assertEquals(2, values.get("count"));
    }

    /**
     * 验证<code>Map.of(null, "value")</code> 抛出 <code>NullPointerException.class</code>，<code>Map.of("key", null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void rejectsNullKeysAndValues() {
        assertThrows(NullPointerException.class, () -> Map.of(null, "value"));
        assertThrows(NullPointerException.class, () -> Map.of("key", null));
    }

    /**
     * 验证<code>Map.of("key", 1, "key", 2)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void rejectsDuplicateKeys() {
        assertThrows(IllegalArgumentException.class, () -> Map.of("key", 1, "key", 2));
    }

    /**
     * 验证<code>values.put("name", "bob")</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void returnedMapCannotBeMutated() {
        Map<String, Object> values = Map.of("name", "alice");
        assertThrows(UnsupportedOperationException.class, () -> values.put("name", "bob"));
    }
}

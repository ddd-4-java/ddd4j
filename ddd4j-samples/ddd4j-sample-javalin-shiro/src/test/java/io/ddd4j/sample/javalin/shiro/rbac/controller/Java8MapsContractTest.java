package io.ddd4j.sample.javalin.shiro.rbac.controller;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Java 8 响应 Map 与高线 Map.of 的共同输入及不可变性契约。
 */
class Java8MapsContractTest {

    /** \u9a8c\u8bc1<code>values.get("name")</code> \u7b49\u4e8e <code>"alice"</code>\uff0c<code>values.get("count")</code> \u7b49\u4e8e <code>2</code>\u3002 */
    @Test
    void createsResponseMap() {
        Map<String, Object> values = ImmutableMap.of("name", "alice", "count", 2);
        assertEquals("alice", values.get("name"));
        assertEquals(2, values.get("count"));
    }

    /** \u9a8c\u8bc1<code>Map.of(null, "value")</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>Map.of("key", null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void rejectsNullKeysAndValues() {
        assertThrows(NullPointerException.class, () -> ImmutableMap.of(null, "value"));
        assertThrows(NullPointerException.class, () -> ImmutableMap.of("key", null));
    }

    /** \u9a8c\u8bc1<code>Map.of("key", 1, "key", 2)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void rejectsDuplicateKeys() {
        assertThrows(IllegalArgumentException.class, () -> ImmutableMap.of("key", 1, "key", 2));
    }

    /** \u9a8c\u8bc1<code>values.put("name", "bob")</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
    @Test
    void returnedMapCannotBeMutated() {
        Map<String, Object> values = ImmutableMap.of("name", "alice");
        assertThrows(UnsupportedOperationException.class, () -> values.put("name", "bob"));
    }
}

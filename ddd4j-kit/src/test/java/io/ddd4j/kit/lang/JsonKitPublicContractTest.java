package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 Jackson 2/3 下相同的公开 JSON 转换契约。 */
class JsonKitPublicContractTest {

    @Test
    void nestedObjectsAndArraysKeepScalarTypes() {
        Map<String, Object> result = JsonKit.toMap("{\"name\":\"order\",\"count\":2,\"active\":true,"
                + "\"child\":{\"code\":7},\"items\":[\"first\",3,{\"code\":9}]}");

        assertEquals("order", result.get("name"));
        assertEquals(2, result.get("count"));
        assertEquals(true, result.get("active"));
        assertEquals(Collections.singletonMap("code", 7), result.get("child"));
        assertEquals(Arrays.asList("first", 3, Collections.singletonMap("code", 9)), result.get("items"));
        assertEquals(Arrays.asList(Collections.singletonMap("id", 1), Collections.singletonMap("id", 2)),
                JsonKit.toMapList("[{\"id\":1},{\"id\":2}]"));
    }

    @Test
    void nullBlankAndExistingValuesKeepTheirDocumentedResults() {
        assertNull(JsonKit.toJson(null));
        assertEquals("raw", JsonKit.toJson("raw"));
        assertNull(JsonKit.toObject("", Integer.class));
        assertEquals(Collections.emptyList(), JsonKit.toList(null, Integer.class));
        assertNull(JsonKit.toList("", Integer.class));
        List<Integer> original = Arrays.asList(1, 2);
        assertTrue(JsonKit.toList(original, Integer.class) == original);
        assertTrue(JsonKit.isJson("{}"));
        assertFalse(JsonKit.isJson(" [] "));
        assertTrue(JsonKit.isJsonArray("[]"));
        assertEquals(42, JsonKit.toType("42", Integer.class));
        assertEquals(Arrays.asList(1, 2), JsonKit.toList("[1,2]", Integer.class));
        assertEquals(7, JsonKit.toPojo("7", JsonKit.INTEGER_TYPE));
        assertNull(JsonKit.toMapList("invalid"));
        assertEquals(Collections.emptyMap(), JsonKit.toMap("invalid"));
    }

    @Test
    void updatesExistingBeanAndUsesCustomTimeFormats() throws Exception {
        MutableValue bean = new MutableValue();
        bean.setName("before");
        JsonKit.update("{\"name\":\"after\"}", bean);
        assertEquals("after", bean.getName());

        String date = JsonKit.buildObjectMapper("dd/MM/yyyy", "dd/MM/yyyy HH:mm:ss", "HH:mm:ss")
                .writeValueAsString(LocalDate.of(2026, 10, 19));
        String dateTime = JsonKit.buildObjectMapper("dd/MM/yyyy", "dd/MM/yyyy HH:mm:ss", "HH:mm:ss")
                .writeValueAsString(LocalDateTime.of(2026, 10, 19, 3, 4, 5));
        String time = JsonKit.buildObjectMapper("dd/MM/yyyy", "dd/MM/yyyy HH:mm:ss", "HH:mm:ss")
                .writeValueAsString(LocalTime.of(3, 4, 5));
        assertEquals("\"19/10/2026\"", date);
        assertEquals("\"19/10/2026 03:04:05\"", dateTime);
        assertEquals("\"03:04:05\"", time);
        assertTrue(JsonKit.toJsonWithDefaultPrettyPrinter(Collections.singletonMap("name", "order"))
                .contains("\"name\""));
    }

    public static class MutableValue {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}

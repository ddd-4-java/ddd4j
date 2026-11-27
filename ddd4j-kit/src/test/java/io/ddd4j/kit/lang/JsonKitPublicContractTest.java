package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Jackson 2/3 下相同的公开 JSON 转换契约。
 */
class JsonKitPublicContractTest {

    /** \u9a8c\u8bc1<code>result.get("name")</code> \u7b49\u4e8e <code>"order"</code>\uff0c<code>result.get("count")</code> \u7b49\u4e8e <code>2</code>\uff0c<code>result.get("items")</code> \u7b49\u4e8e <code>Arrays.asList("first", 3, Collections.singletonMap("code", 9))</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>JsonKit.toJson(null)</code>\u4e3a\u7a7a\uff0c<code>JsonKit.toJson("raw")</code> \u7b49\u4e8e <code>"raw"</code>\uff0c<code>JsonKit.toMap("invalid")</code> \u7b49\u4e8e <code>Collections.emptyMap()</code>\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>bean.getName()</code> \u7b49\u4e8e <code>"after"</code>\uff0c<code>date</code> \u7b49\u4e8e <code>"\"19/10/2026\""</code>\uff0c<code>time</code> \u7b49\u4e8e <code>"\"03:04:05\""</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
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

    /** \u7528\u4e8e\u9a8c\u8bc1 Jackson \u8bfb\u5199\u666e\u901a\u53ef\u53d8 Java Bean \u7684\u6837\u672c\u3002 */
    public static class MutableValue {
        /** \u7528\u4e8e\u9a8c\u8bc1 JSON \u8bfb\u5199\u5f80\u8fd4\u7684\u53ef\u4fee\u6539\u5c5e\u6027\u3002 */
        private String name;

        /**
         * \u8bfb\u53d6<code>name</code> \u5b57\u6bb5\u3002
         * @return <code>name</code> \u5b57\u6bb5
         */
        public String getName() {
            return name;
        }

        /**
         * \u8bbe\u7f6e<code>name</code> \u5b57\u6bb5\u3002
         * @param name \u8981\u5199\u5165\u7684\u503c
         */
        public void setName(String name) {
            this.name = name;
        }
    }
}

package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 Jackson 2/3 类型适配后的 JsonKit 公开类型与读取行为。
 */
class JsonKitTypeContractTest {

    /** \u9a8c\u8bc1<code>JsonKit.buildCollectionType(List.class, Integer.class).getRawClass()</code> \u7b49\u4e8e <code>List.class</code>\uff0c<code>JsonKit.buildCollectionType(List.class, Integer.class) .getContentType().getRawClass()</code> \u7b49\u4e8e <code>Integer.class</code>\uff0c<code>JsonKit.toObject("[1,2]", JsonKit.buildCollectionType(List.class, Integer.class))</code> \u7b49\u4e8e <code>Arrays.asList(1, 2)</code>\u3002 */
    @Test
    void collectionTypeRetainsElementTypeAndReadsValues() {
        assertEquals(List.class, JsonKit.buildCollectionType(List.class, Integer.class).getRawClass());
        assertEquals(Integer.class, JsonKit.buildCollectionType(List.class, Integer.class)
                .getContentType().getRawClass());
        assertEquals(Arrays.asList(1, 2),
                JsonKit.toObject("[1,2]", JsonKit.buildCollectionType(List.class, Integer.class)));
    }

    /** \u9a8c\u8bc1<code>JsonKit.buildMapType(Map.class, String.class, Integer.class).getRawClass()</code> \u7b49\u4e8e <code>Map.class</code>\uff0c<code>JsonKit.buildMapType(Map.class, String.class, Integer.class) .getKeyType().getRawClass()</code> \u7b49\u4e8e <code>String.class</code>\uff0c<code>JsonKit.toObject("{\"a\":2}", JsonKit.buildMapType(Map.class, String.class, Integer.class))</code> \u7b49\u4e8e <code>expected</code>\u3002 */
    @Test
    void mapTypeRetainsKeyAndValueTypesAndReadsValues() {
        assertEquals(Map.class, JsonKit.buildMapType(Map.class, String.class, Integer.class).getRawClass());
        assertEquals(String.class, JsonKit.buildMapType(Map.class, String.class, Integer.class)
                .getKeyType().getRawClass());
        assertEquals(Integer.class, JsonKit.buildMapType(Map.class, String.class, Integer.class)
                .getContentType().getRawClass());
        Map<String, Integer> expected = new HashMap<>();
        expected.put("a", 2);
        assertEquals(expected, JsonKit.toObject("{\"a\":2}",
                JsonKit.buildMapType(Map.class, String.class, Integer.class)));
    }

    /** \u9a8c\u8bc1<code>JsonKit.toPojo("\"value\"", JsonKit.STRING_TYPE)</code> \u7b49\u4e8e <code>"value"</code>\u3002 */
    @Test
    void stringTypeReferenceReadsTheSameValue() {
        assertEquals("value", JsonKit.toPojo("\"value\"", JsonKit.STRING_TYPE));
    }
}

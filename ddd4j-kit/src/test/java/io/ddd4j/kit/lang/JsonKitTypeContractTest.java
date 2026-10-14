package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证 Jackson 2/3 类型适配后的 JsonKit 公开类型与读取行为。 */
class JsonKitTypeContractTest {

    @Test
    void collectionTypeRetainsElementTypeAndReadsValues() {
        assertEquals(List.class, JsonKit.buildCollectionType(List.class, Integer.class).getRawClass());
        assertEquals(Integer.class, JsonKit.buildCollectionType(List.class, Integer.class)
                .getContentType().getRawClass());
        assertEquals(Arrays.asList(1, 2),
                JsonKit.toObject("[1,2]", JsonKit.buildCollectionType(List.class, Integer.class)));
    }

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

    @Test
    void stringTypeReferenceReadsTheSameValue() {
        assertEquals("value", JsonKit.toPojo("\"value\"", JsonKit.STRING_TYPE));
    }
}

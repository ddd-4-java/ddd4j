package io.ddd4j.spring.util;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** 验证签名输入中 null 与空表单的既有等价语义。 */
class SignKitEmptyFormTest {

    @Test
    void multiValueMapTreatsNullAsEmpty() throws Exception {
        MultiValueMap<String, String> empty = new LinkedMultiValueMap<>();
        String expected = SignKit.signOrigin("token", empty, "body", "salt");
        assertEquals(expected, SignKit.signOrigin("token", (MultiValueMap<String, String>) null, "body", "salt"));
        empty.add("key", "value");
        assertNotEquals(expected, SignKit.signOrigin("token", empty, "body", "salt"));
    }

    @Test
    void arrayMapTreatsNullAsEmpty() throws Exception {
        Map<String, String[]> empty = Collections.emptyMap();
        String expected = SignKit.signOrigin("token", empty, "body", "salt");
        assertEquals(expected, SignKit.signOrigin("token", (Map<String, String[]>) null, "body", "salt"));
        assertNotEquals(expected, SignKit.signOrigin("token", Collections.singletonMap("key", new String[] {"value"}), "body", "salt"));
    }
}

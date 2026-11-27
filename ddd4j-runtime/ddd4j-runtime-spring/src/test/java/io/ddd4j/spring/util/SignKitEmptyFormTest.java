package io.ddd4j.spring.util;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 验证签名输入中 null 与空表单的既有等价语义。
 */
class SignKitEmptyFormTest {

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>SignKit.signOrigin()</code>\u3001<code>empty.add()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void multiValueMapTreatsNullAsEmpty() throws Exception {
        MultiValueMap<String, String> empty = new LinkedMultiValueMap<>();
        String expected = SignKit.signOrigin("token", empty, "body", "salt");
        assertEquals(expected, SignKit.signOrigin("token", (MultiValueMap<String, String>) null, "body", "salt"));
        empty.add("key", "value");
        assertNotEquals(expected, SignKit.signOrigin("token", empty, "body", "salt"));
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>Collections.emptyMap()</code>\u3001<code>SignKit.signOrigin()</code>\u3001<code>Collections.singletonMap()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void arrayMapTreatsNullAsEmpty() throws Exception {
        Map<String, String[]> empty = Collections.emptyMap();
        String expected = SignKit.signOrigin("token", empty, "body", "salt");
        assertEquals(expected, SignKit.signOrigin("token", (Map<String, String[]>) null, "body", "salt"));
        assertNotEquals(expected, SignKit.signOrigin("token", Collections.singletonMap("key", new String[]{"value"}), "body", "salt"));
    }
}

package io.ddd4j.web.micronaut;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.web.core.context.WebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三线 Micronaut 请求状态的读取与值方法契约。 */
class Ddd4jMicronautContextValueContractTest {

    /** \u9a8c\u8bc1<code>context.requestContext()</code> \u7b49\u4e8e <code>request</code>\uff0c<code>context.getRequestContext()</code> \u7b49\u4e8e <code>request</code>\uff0c<code>context.getSubject()</code> \u7b49\u4e8e <code>Optional.empty()</code>\u3002 */
    @Test
    void beanAndComponentAccessorsReadSameState() {
        WebRequestContext request = request("request-1");
        Ddd4jMicronautContext context = new Ddd4jMicronautContext(request, Optional.empty());

        assertEquals(request, context.requestContext());
        assertEquals(request, context.getRequestContext());
        assertEquals(Optional.empty(), context.subject());
        assertEquals(Optional.empty(), context.getSubject());
    }

    /** \u9a8c\u8bc1<code>same</code> \u7b49\u4e8e <code>left</code>\uff0c<code>same.hashCode()</code> \u7b49\u4e8e <code>left.hashCode()</code>\uff0c<code>left.toString().contains("Ddd4jMicronautContext")</code>\u4e3a\u771f\u3002 */
    @Test
    void valueMethodsUseRequestAndSubject() {
        Ddd4jMicronautContext left = new Ddd4jMicronautContext(request("request-1"), Optional.empty());
        Ddd4jMicronautContext same = new Ddd4jMicronautContext(request("request-1"), Optional.empty());
        Ddd4jMicronautContext different = new Ddd4jMicronautContext(request("request-2"), Optional.empty());

        assertEquals(left, same);
        assertEquals(left.hashCode(), same.hashCode());
        assertNotEquals(left, different);
        assertTrue(left.toString().contains("Ddd4jMicronautContext"));
    }

    /**
     * \u9a8c\u8bc1<code>restored</code> \u7b49\u4e8e <code>original</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void jsonRoundTripPreservesRequestState() throws Exception {
        Ddd4jMicronautContext original = new Ddd4jMicronautContext(request("request-1"), Optional.empty());
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

        Ddd4jMicronautContext restored = mapper.readValue(
                mapper.writeValueAsString(original), Ddd4jMicronautContext.class);

        assertEquals(original, restored);
    }

    /**
     * \u521b\u5efa <code>new WebRequestContext</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestContext(id, "trace-1", "tenant-1", null, Locale.ENGLISH, "127.0.0.1", "GET", "/test")</code>
     */
    private WebRequestContext request(String id) {
        return new WebRequestContext(id, "trace-1", "tenant-1", null,
                Locale.ENGLISH, "127.0.0.1", "GET", "/test");
    }
}

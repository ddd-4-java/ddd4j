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

    /** 验证<code>context.requestContext()</code> 等于 <code>request</code>，<code>context.getRequestContext()</code> 等于 <code>request</code>，<code>context.getSubject()</code> 等于 <code>Optional.empty()</code>。 */
    @Test
    void accessorsExposeRequestAndOptionalSubject() {
        WebRequestContext request = request("request-1");
        Ddd4jMicronautContext context = new Ddd4jMicronautContext(request, Optional.empty());

        assertEquals(request, context.requestContext());
        assertEquals(Optional.empty(), context.subject());
    }

    /** 验证<code>same</code> 等于 <code>left</code>，<code>same.hashCode()</code> 等于 <code>left.hashCode()</code>，<code>left.toString().contains("Ddd4jMicronautContext")</code>为真。 */
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
     * 验证<code>restored</code> 等于 <code>original</code>。
     * @throws Exception 处理测试数据的序列化时失败
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
     * 创建 <code>new WebRequestContext</code> 供当前测试路径调用。
     * @param id 当前要查找的测试记录标识
     * @return 当前测试使用的结果，取自 <code>new WebRequestContext(id, "trace-1", "tenant-1", null, Locale.ENGLISH, "127.0.0.1", "GET", "/test")</code>
     */
    private WebRequestContext request(String id) {
        return new WebRequestContext(id, "trace-1", "tenant-1", null,
                Locale.ENGLISH, "127.0.0.1", "GET", "/test");
    }
}

package io.ddd4j.sample.dropwizard;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过 <code>response.setToken()</code>、<code>response.token()</code>、<code>response.getToken()</code> 验证 <code>TokenResponseContract</code> 的运行行为。
 */
class TokenResponseContractTest {

    /**
     * 验证<code>response.token()</code> 等于 <code>"token-2"</code>，<code>response.getToken()</code> 等于 <code>response.token()</code>。
     */
    @Test
    void tokenResponseRetainsMutableBeanAndComponentAccess() {
        AuthenticationResource.TokenResponse response = new AuthenticationResource.TokenResponse("token-1");
        response.setToken("token-2");

        assertEquals("token-2", response.token());
        assertEquals(response.token(), response.getToken());
        assertEquals(new AuthenticationResource.TokenResponse("token-2"), response);
    }

    /**
     * 在测试框架中执行 <code>response.setToken()</code>、<code>Jackson.newObjectMapper()</code>，验证该场景。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void tokenResponseWritesOnlyTheTokenField() throws Exception {
        AuthenticationResource.TokenResponse response = new AuthenticationResource.TokenResponse("token-1");
        response.setToken("token-2");

        assertEquals("{\"token\":\"token-2\"}", Jackson.newObjectMapper().writeValueAsString(response));
    }

    /**
     * 验证<code>response.token()</code> 等于 <code>"token-2"</code>，<code>mapper.writeValueAsString(response)</code> 等于 <code>json</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void tokenResponseRoundTripsUsingOnlyTheTokenField() throws Exception {
        ObjectMapper mapper = Jackson.newObjectMapper();
        String json = "{\"token\":\"token-2\"}";

        AuthenticationResource.TokenResponse response = mapper.readValue(json, AuthenticationResource.TokenResponse.class);

        assertEquals(new AuthenticationResource.TokenResponse("token-2"), response);
        assertEquals("token-2", response.token());
        assertEquals(json, mapper.writeValueAsString(response));
        assertEquals(response, new ObjectMapper().readValue(json, AuthenticationResource.TokenResponse.class));
    }

    /**
     * 验证<code>mapper.readValue(json, AuthenticationResource.TokenResponse.class)</code> 等于 <code>empty</code>，<code>mapper.readValue("{}", AuthenticationResource.TokenResponse.class)</code> 等于 <code>empty</code>，<code>clientMapper.readValue("{}", AuthenticationResource.TokenResponse.class)</code> 等于 <code>empty</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void tokenResponseAcceptsExplicitAndMissingNullValues() throws Exception {
        ObjectMapper mapper = Jackson.newObjectMapper();
        String json = "{\"token\":null}";
        AuthenticationResource.TokenResponse empty = new AuthenticationResource.TokenResponse(null);

        assertEquals(empty, mapper.readValue(json, AuthenticationResource.TokenResponse.class));
        assertEquals(empty, mapper.readValue("{}", AuthenticationResource.TokenResponse.class));
        assertEquals(json, mapper.writeValueAsString(empty));
        ObjectMapper clientMapper = new ObjectMapper();
        assertEquals(empty, clientMapper.readValue(json, AuthenticationResource.TokenResponse.class));
        assertEquals(empty, clientMapper.readValue("{}", AuthenticationResource.TokenResponse.class));
    }
}

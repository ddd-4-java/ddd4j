package io.ddd4j.sample.dropwizard;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u901a\u8fc7 <code>response.setToken()</code>\u3001<code>response.token()</code>\u3001<code>response.getToken()</code> \u9a8c\u8bc1 <code>TokenResponseContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class TokenResponseContractTest {

    /** \u9a8c\u8bc1<code>response.token()</code> \u7b49\u4e8e <code>"token-2"</code>\uff0c<code>response.getToken()</code> \u7b49\u4e8e <code>response.token()</code>\u3002 */
    @Test
    void tokenResponseRetainsMutableBeanAndComponentAccess() {
        AuthenticationResource.TokenResponse response = new AuthenticationResource.TokenResponse("token-1");
        response.setToken("token-2");

        assertEquals("token-2", response.token());
        assertEquals(response.token(), response.getToken());
        assertEquals(new AuthenticationResource.TokenResponse("token-2"), response);
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>response.setToken()</code>\u3001<code>Jackson.newObjectMapper()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void tokenResponseWritesOnlyTheTokenField() throws Exception {
        AuthenticationResource.TokenResponse response = new AuthenticationResource.TokenResponse("token-1");
        response.setToken("token-2");

        assertEquals("{\"token\":\"token-2\"}", Jackson.newObjectMapper().writeValueAsString(response));
    }

    /**
     * \u9a8c\u8bc1<code>response.token()</code> \u7b49\u4e8e <code>"token-2"</code>\uff0c<code>mapper.writeValueAsString(response)</code> \u7b49\u4e8e <code>json</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>mapper.readValue(json, AuthenticationResource.TokenResponse.class)</code> \u7b49\u4e8e <code>empty</code>\uff0c<code>mapper.readValue("{}", AuthenticationResource.TokenResponse.class)</code> \u7b49\u4e8e <code>empty</code>\uff0c<code>clientMapper.readValue("{}", AuthenticationResource.TokenResponse.class)</code> \u7b49\u4e8e <code>empty</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
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

package io.ddd4j.sample.dropwizard;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenResponseContractTest {

    @Test
    void tokenResponseRetainsMutableBeanAndComponentAccess() {
        AuthenticationResource.TokenResponse response = new AuthenticationResource.TokenResponse("token-1");
        response.setToken("token-2");

        assertEquals("token-2", response.token());
        assertEquals(response.token(), response.getToken());
        assertEquals(new AuthenticationResource.TokenResponse("token-2"), response);
    }

    @Test
    void tokenResponseWritesOnlyTheTokenField() throws Exception {
        AuthenticationResource.TokenResponse response = new AuthenticationResource.TokenResponse("token-1");
        response.setToken("token-2");

        assertEquals("{\"token\":\"token-2\"}", Jackson.newObjectMapper().writeValueAsString(response));
    }

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

package io.ddd4j.sample.dropwizard;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenResponseContractTest {

    @Test
    void tokenResponseRetainsMutableBeanAndComponentAccess() {
        DropwizardOrderResource.TokenResponse response = new DropwizardOrderResource.TokenResponse("token-1");
        response.setToken("token-2");

        assertEquals("token-2", response.token());
        assertEquals(response.token(), response.getToken());
        assertEquals(new DropwizardOrderResource.TokenResponse("token-2"), response);
    }

    @Test
    void tokenResponseWritesOnlyTheTokenField() throws Exception {
        DropwizardOrderResource.TokenResponse response = new DropwizardOrderResource.TokenResponse("token-1");
        response.setToken("token-2");

        assertEquals("{\"token\":\"token-2\"}", Jackson.newObjectMapper().writeValueAsString(response));
    }

    @Test
    void tokenResponseRoundTripsUsingOnlyTheTokenField() throws Exception {
        ObjectMapper mapper = Jackson.newObjectMapper();
        String json = "{\"token\":\"token-2\"}";

        DropwizardOrderResource.TokenResponse response = mapper.readValue(json, DropwizardOrderResource.TokenResponse.class);

        assertEquals(new DropwizardOrderResource.TokenResponse("token-2"), response);
        assertEquals("token-2", response.token());
        assertEquals(json, mapper.writeValueAsString(response));
        assertEquals(response, new ObjectMapper().readValue(json, DropwizardOrderResource.TokenResponse.class));
    }

    @Test
    void tokenResponseAcceptsExplicitAndMissingNullValues() throws Exception {
        ObjectMapper mapper = Jackson.newObjectMapper();
        String json = "{\"token\":null}";
        DropwizardOrderResource.TokenResponse empty = new DropwizardOrderResource.TokenResponse(null);

        assertEquals(empty, mapper.readValue(json, DropwizardOrderResource.TokenResponse.class));
        assertEquals(empty, mapper.readValue("{}", DropwizardOrderResource.TokenResponse.class));
        assertEquals(json, mapper.writeValueAsString(empty));
        ObjectMapper clientMapper = new ObjectMapper();
        assertEquals(empty, clientMapper.readValue(json, DropwizardOrderResource.TokenResponse.class));
        assertEquals(empty, clientMapper.readValue("{}", DropwizardOrderResource.TokenResponse.class));
    }
}

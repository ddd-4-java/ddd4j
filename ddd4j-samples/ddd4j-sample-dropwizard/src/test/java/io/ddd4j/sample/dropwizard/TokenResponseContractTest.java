package io.ddd4j.sample.dropwizard;

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
}

package io.ddd4j.auth.satoken.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SaTokenSecurityPropertiesTest {

    @Test
    void defaultsAreValid() {
        assertDoesNotThrow(new SaTokenSecurityProperties()::validate);
    }

    @Test
    void validateRejectsInvalidTokenNamespace() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setTokenNamespace("1invalid");
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    @Test
    void validateRejectsInvalidLoginType() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setLoginType("");
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    @Test
    void validateRejectsNonPositiveTokenTtl() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setTokenTtlSeconds(0L);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    @Test
    void validateRejectsInvalidActiveTimeout() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setActiveTimeoutSeconds(0L);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    @Test
    void validateRejectsNullAuthenticationMode() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(null);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    @Test
    void validateJwtModeRequiresAudienceAndIssuer() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("ddd4j-props-test-secret-32bytes");
        assertThrows(IllegalArgumentException.class, properties::validate);

        SaTokenSecurityProperties missingIssuer = new SaTokenSecurityProperties();
        missingIssuer.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        missingIssuer.setJwtSecretKey("ddd4j-props-test-secret-32bytes");
        missingIssuer.setJwtAudience("aud");
        assertThrows(IllegalArgumentException.class, missingIssuer::validate);
    }

    @Test
    void validateJwtModeAcceptsCompleteConfiguration() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("ddd4j-props-test-secret-32bytes-longer");
        properties.setJwtIssuer("issuer");
        properties.setJwtAudience("audience");
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void tokenNameCombinesNamespaceAndLoginType() {
        assertEquals("ddd4j-token", new SaTokenSecurityProperties().tokenName());
    }
}

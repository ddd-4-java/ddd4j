/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.auth.satoken.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>SaTokenSecurityProperties</code> \u5728<code>validate</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class SaTokenSecurityPropertiesTest {

    /** \u9a8c\u8bc1\u7f3a\u7701\u8ba4\u8bc1\u5b89\u5168\u914d\u7f6e\u53ef\u76f4\u63a5\u901a\u8fc7\u6821\u9a8c\u3002 */
    @Test
    void defaultsAreValid() {
        assertDoesNotThrow(new SaTokenSecurityProperties()::validate);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void validateRejectsInvalidTokenNamespace() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setTokenNamespace("1invalid");
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void validateRejectsInvalidLoginType() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setLoginType("");
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void validateRejectsNonPositiveTokenTtl() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setTokenTtlSeconds(0L);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void validateRejectsInvalidActiveTimeout() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setActiveTimeoutSeconds(0L);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void validateRejectsNullAuthenticationMode() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(null);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>properties.setAuthenticationMode()</code>\u3001<code>properties.setJwtSecretKey()</code>\u3001<code>properties.setJwtIssuer()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void validateJwtModeAcceptsCompleteConfiguration() {
        SaTokenSecurityProperties properties = new SaTokenSecurityProperties();
        properties.setAuthenticationMode(SaTokenAuthenticationMode.JWT_SIMPLE);
        properties.setJwtSecretKey("ddd4j-props-test-secret-32bytes-longer");
        properties.setJwtIssuer("issuer");
        properties.setJwtAudience("audience");
        assertDoesNotThrow(properties::validate);
    }

    /** \u9a8c\u8bc1\u9ed8\u8ba4\u547d\u540d\u7a7a\u95f4\u4e0e\u8ba4\u8bc1\u57df\u62fc\u63a5\u6210 ddd4j-token\u3002 */
    @Test
    void tokenNameCombinesNamespaceAndLoginType() {
        assertEquals("ddd4j-token", new SaTokenSecurityProperties().tokenName());
    }
}
